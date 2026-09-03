package com.isuara.app.service

import android.util.Log
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * DebateTranslator — asks three interpreters with different stances, then has a
 * judge pick the best answer.
 *
 * BIM glosses arrive loosely ordered and the mapping to a sentence is genuinely
 * ambiguous, so a single model just commits to one reading. Three stances
 * disagree in useful ways and a judge resolves them. On unambiguous glosses the
 * three converge, which is correct but means the cost buys nothing there.
 *
 * Takes ready-made [agents] and a raw [judgeCall] rather than constructing them,
 * which keeps it independent of any one provider and testable without network.
 * Use [gonkaDebate] rather than constructing it directly.
 *
 * The judge returns an INDEX, not a sentence. That way the result is always
 * something an agent actually proposed — the judge cannot invent a fourth
 * answer — the four languages stay mutually consistent because they come from
 * one agent's single response, and the judge prompt carries three short
 * sentences instead of twelve.
 *
 * Costs four calls per translation. Wall clock is max(agents) + judge.
 */
class DebateTranslator(
    private val agents: List<Translator>,
    private val judgeCall: suspend (system: String, user: String) -> String,
    /** Display names, parallel to [agents]. Shown while each is still pending. */
    private val agentLabels: List<String> = agents.indices.map { "agent $it" },
) : Translator {

    companion object {
        private const val TAG = "DebateTranslator"

        /** Long enough for each stage label to actually be read. */
        private const val STAGE_HOLD_MS = 400L
    }

    private val _progress = MutableStateFlow(DebateProgress())
    override val progress: StateFlow<DebateProgress> = _progress.asStateFlow()

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    private fun setStage(next: TranslationStage) {
        _stage.value = next
        _progress.value = _progress.value.copy(stage = next)
    }

    /** Presentation only — must never delay the returned translation. */
    private suspend fun hold(next: TranslationStage) {
        setStage(next)
        delay(STAGE_HOLD_MS)
    }

    /** Publishes one agent's outcome the moment it lands. */
    private fun publish(index: Int, sentence: String?, failed: Boolean) {
        _progress.value = _progress.value.copy(
            candidates = _progress.value.candidates.mapIndexed { i, c ->
                if (i == index) c.copy(sentence = sentence, failed = failed) else c
            }
        )
    }

    override suspend fun translate(
        words: List<String>,
        emotion: EmotionReading?,
    ): Translation {
        require(words.isNotEmpty()) { "no glosses to translate" }

        try {
            // Seed every slot as pending up front so the UI can show all three
            // models immediately, then fill each in as its model answers.
            _progress.value = DebateProgress(
                stage = TranslationStage.CONSULTING,
                candidates = agentLabels.map { CandidateView(model = it) },
            )
            _stage.value = TranslationStage.CONSULTING

            // Each agent publishes on completion rather than the whole set
            // waiting on the slowest — the measured spread across the three
            // models was ~9s to ~126s, so batching means minutes of dead air.
            val results = coroutineScope {
                agents.mapIndexed { i, agent ->
                    async {
                        val result = runCatching { agent.translate(words, emotion) }.getOrNull()
                        publish(i, result?.ms, failed = result == null)
                        Log.i(TAG, "candidate[$i] ${agentLabels.getOrNull(i)}: " +
                            (result?.ms ?: "FAILED"))
                        result
                    }
                }.awaitAll()
            }
            val candidates = results.filterNotNull()
            // Maps a surviving candidate's index back to its agent slot, so the
            // judge's choice reaches the row that actually produced the winner.
            val slotOf = results.indices.filter { results[it] != null }

            when (candidates.size) {
                0 -> throw IllegalStateException("all ${agents.size} interpreters failed")
                // Nothing to choose between — skip the judge and its latency.
                1 -> {
                    Log.i(TAG, "only one interpreter succeeded, returning it unjudged")
                    return candidates.single()
                }
            }

            hold(TranslationStage.COLLECTED)
            hold(TranslationStage.JUDGING)

            // A failed judge must not discard candidates we already hold; an
            // arbitrary but valid answer beats falling back to raw glosses.
            val verdict = runCatching { judge(words, candidates, emotion) }
                .onFailure { Log.w(TAG, "judge failed, using first candidate: ${it.message}") }
                .getOrNull()

            // Re-index onto agent slots before publishing: the judge numbers
            // the candidates it was given, so with a failed agent its choice
            // refers to a different row than the one that produced the winner.
            if (verdict != null) {
                _progress.value = _progress.value.copy(
                    verdict = verdict.copy(
                        choice = slotOf.getOrElse(verdict.choice) { verdict.choice },
                    )
                )
            }

            hold(TranslationStage.DECIDING)
            return candidates[verdict?.choice ?: 0]
        } finally {
            _stage.value = TranslationStage.IDLE
            _progress.value = _progress.value.copy(stage = TranslationStage.IDLE)
        }
    }

    /**
     * Returns the index of the winning candidate.
     *
     * Forced onto Dispatchers.IO: agents do this themselves inside their own
     * translate(), but the judge call would otherwise run on whatever dispatcher
     * the caller used — which is Main, from the UI.
     */
    private suspend fun judge(
        words: List<String>,
        candidates: List<Translation>,
        emotion: EmotionReading?,
    ): JudgeVerdict =
        withContext(Dispatchers.IO) {
            val raw = judgeCall(
                TranslationPrompts.JUDGE,
                TranslationPrompts.judgeTurn(words, candidates, emotion),
            )
            val verdict = TranslationParsing.extractChoice(raw, candidates.size)
            Log.i(TAG, "judge chose [${verdict.choice}] " +
                "'${candidates[verdict.choice].ms}' — ${verdict.reason}")
            verdict
        }
}

/**
 * The multi-agent debate over GonkaRouter.
 *
 * Three different models answer the same glosses with the same prompt, so any
 * disagreement is attributable to the model rather than to a differing stance.
 * DeepSeek judges: it is the fastest and cleanest of the three and sits on the
 * critical path once the agents finish.
 *
 * Note this waits for all three — wall clock tracks the slowest model, which
 * benchmarked as Kimi.
 */
fun gonkaDebate() = DebateTranslator(
    agents = GonkaTranslator.AGENT_MODELS.map { GonkaTranslator(it) },
    judgeCall = { system, user ->
        gonkaComplete(GonkaTranslator.JUDGE_MODEL, system, user)
    },
    agentLabels = GonkaTranslator.AGENT_MODELS,
)
