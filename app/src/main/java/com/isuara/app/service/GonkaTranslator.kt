package com.isuara.app.service

import android.util.Log
import com.anthropic.models.messages.MessageCreateParams
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GonkaTranslator — the same four-language translation via GonkaRouter, which
 * speaks the Anthropic Messages API.
 *
 * NOT the active provider. Unwired on 2026-08-31 after GonkaRouter's serving
 * capacity degraded to roughly 19 tok/s — measured 3-55s per call across all
 * three of its models, against a 1,290ms median benchmarked a day earlier.
 * Diagnostics pointed at queueing on the router rather than any model, so it
 * was not something callers could optimise around.
 *
 * Kept as live, compiling code rather than commented out, so it cannot rot and
 * its tests keep running. To switch back, see the commented line in
 * MainActivity.
 *
 * The JSON contract is enforced by prompt, not by schema: GonkaRouter accepts
 * `output_config.format` with HTTP 200 but silently ignores it, returning
 * free-form prose.
 *
 * Failures propagate as exceptions — callers distinguish them from a real
 * translation by catching, not by inspecting the returned text.
 */
class GonkaTranslator(
    private val modelId: String = DEFAULT_MODEL,
) : Translator {

    companion object {
        private const val TAG = "GonkaTranslator"
        const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-V4-Flash-0731"

        /**
         * The three models that debate. Diversity comes from the models
         * themselves rather than from prompt stances, so a difference between
         * candidates is attributable to the model and nothing else.
         *
         * Benchmarked over 10 runs each: DeepSeek 1,290ms median / 4,219ms p90;
         * MiniMax 6,310ms / 32,390ms; Kimi 11,578ms / 63,590ms. Since the debate
         * waits for all three, wall clock tracks Kimi.
         */
        val AGENT_MODELS = listOf(
            DEFAULT_MODEL,
            "MiniMaxAI/MiniMax-M2.7",
            "moonshotai/Kimi-K2.6",
        )

        /**
         * DeepSeek judges: fastest and cleanest of the three, and the judge sits
         * on the critical path after the agents finish, so its latency adds
         * directly to the total.
         */
        const val JUDGE_MODEL = DEFAULT_MODEL
    }

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    override suspend fun translate(
        words: List<String>,
        emotion: EmotionReading?,
    ): Translation = withContext(Dispatchers.IO) {
        require(words.isNotEmpty()) { "no glosses to translate" }

        // One retry: a model that rambles once usually complies on a re-ask, and
        // a second round trip is cheaper than falling back to raw glosses.
        var lastError: Exception? = null
        _stage.value = TranslationStage.CONSULTING
        try {
            repeat(2) { attempt ->
                try {
                    val raw = gonkaComplete(
                        modelId,
                        TranslationPrompts.SYSTEM,
                        TranslationPrompts.userTurn(words, emotion),
                    )
                    val translation = TranslationParsing.extractTranslation(raw)
                    Log.i(TAG, "Translation [$modelId] $words -> ${translation.ms}")
                    return@withContext translation
                } catch (e: IllegalArgumentException) {
                    lastError = e
                    Log.w(TAG, "Unparseable reply on attempt ${attempt + 1}: ${e.message}")
                }
            }
        } finally {
            _stage.value = TranslationStage.IDLE
        }
        throw lastError ?: IllegalStateException("translation failed")
    }
}

/**
 * One raw GonkaRouter completion. Shared by [GonkaTranslator] and the judge call
 * in [DebateTranslator], which needs the same transport but not the translation
 * contract.
 *
 * The token budget covers four sentences plus JSON scaffolding (~141 measured,
 * more with Tamil script) and leaves headroom for models that prepend a
 * reasoning monologue before the object.
 */
internal fun gonkaComplete(modelId: String, system: String, user: String): String {
    val params = MessageCreateParams.builder()
        .model(modelId)
        .maxTokens(2048L)
        .system(system)
        .addUserMessage(user)
        .build()

    return GonkaClient.client.messages().create(params)
        .content()
        .mapNotNull { it.text().orElse(null) }
        .joinToString("") { it.text() }
}
