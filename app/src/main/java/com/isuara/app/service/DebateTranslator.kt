package com.isuara.app.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * DebateTranslator — asks three interpreters with different stances, then has a
 * judge pick the best answer.
 *
 * BIM glosses arrive loosely ordered and the mapping to a sentence is genuinely
 * ambiguous, so a single model just commits to one reading. Three stances
 * disagree in useful ways and a judge resolves them. On unambiguous glosses the
 * three converge, which is correct but means the cost buys nothing there.
 *
 * Provider-agnostic: it takes ready-made [agents] and a raw [judgeCall], so the
 * same debate runs over Gemini or GonkaRouter. Use [geminiDebate] or
 * [gonkaDebate] rather than constructing it directly.
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
) : Translator {

    companion object {
        private const val TAG = "DebateTranslator"

        /** Long enough for each stage label to actually be read. */
        private const val STAGE_HOLD_MS = 400L
    }

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    /** Presentation only — must never delay the returned translation. */
    private suspend fun hold(next: TranslationStage) {
        _stage.value = next
        delay(STAGE_HOLD_MS)
    }

    override suspend fun translate(words: List<String>): Translation {
        require(words.isNotEmpty()) { "no glosses to translate" }

        try {
            _stage.value = TranslationStage.CONSULTING

            val candidates = coroutineScope {
                agents.map { agent ->
                    async { runCatching { agent.translate(words) }.getOrNull() }
                }.awaitAll()
            }.filterNotNull()

            candidates.forEachIndexed { i, c -> Log.i(TAG, "candidate[$i]: ${c.ms}") }

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
            val chosen = runCatching { judge(words, candidates) }
                .onFailure { Log.w(TAG, "judge failed, using first candidate: ${it.message}") }
                .getOrDefault(0)

            hold(TranslationStage.DECIDING)
            return candidates[chosen]
        } finally {
            _stage.value = TranslationStage.IDLE
        }
    }

    /**
     * Returns the index of the winning candidate.
     *
     * Forced onto Dispatchers.IO: agents do this themselves inside their own
     * translate(), but the judge call would otherwise run on whatever dispatcher
     * the caller used — which is Main, from the UI.
     */
    private suspend fun judge(words: List<String>, candidates: List<Translation>): Int =
        withContext(Dispatchers.IO) {
            val raw = judgeCall(TranslationPrompts.JUDGE, TranslationPrompts.judgeTurn(words, candidates))
            val (choice, reason) = TranslationParsing.extractChoice(raw, candidates.size)
            Log.i(TAG, "judge chose [$choice] \"${candidates[choice].ms}\" — $reason")
            choice
        }
}

/**
 * The multi-agent debate over Gemini — the active provider.
 *
 * Agent i draws on key slot i, so the three agents consume three separate
 * free-tier quota buckets rather than competing for one. The judge rotates
 * round-robin across the same keys: there are four calls per translation but
 * only three keys, so pinning the judge to one would make that key carry two
 * calls per translation and hit its 5/min ceiling first.
 *
 * With fewer than three keys configured the slots wrap, so it still works — with
 * proportionally less quota. [GeminiClients] logs a warning in that case.
 */
fun geminiDebate(modelId: String = GeminiTranslator.DEFAULT_MODEL): DebateTranslator {
    val judgeSlot = AtomicInteger(0)
    return DebateTranslator(
        agents = TranslationPrompts.PERSONAS.mapIndexed { i, persona ->
            GeminiTranslator(
                // Lambda, not a Client: this runs on the main thread at startup.
                clientProvider = { GeminiClients.forSlot(i) },
                persona = persona,
                modelId = modelId,
                keySlot = GeminiClients.slotOf(i),
            )
        },
        judgeCall = { system, user ->
            val slot = GeminiClients.slotOf(judgeSlot.getAndIncrement())
            Log.i("DebateTranslator", "judging on key[$slot]")
            complete(system, user, GeminiClients.forSlot(slot), modelId)
        },
    )
}

/**
 * The multi-agent debate over GonkaRouter. Kept working but unwired — see the
 * commented line in MainActivity for how to switch back.
 */
fun gonkaDebate(modelId: String = GonkaTranslator.DEFAULT_MODEL) = DebateTranslator(
    agents = TranslationPrompts.PERSONAS.map { GonkaTranslator(modelId, it) },
    judgeCall = { system, user -> gonkaComplete(modelId, system, user) },
)
