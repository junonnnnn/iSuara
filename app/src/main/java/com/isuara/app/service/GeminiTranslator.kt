package com.isuara.app.service

import android.util.Log
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GeminiTranslator — turns detected BIM sign keywords into a natural sentence in
 * Malay, English, Simplified Chinese and Tamil in a single call.
 *
 * Each instance holds its own [client], and therefore its own API key, so the
 * three debate agents draw on three separate free-tier quota buckets. See
 * [GeminiClients].
 *
 * Prompts and parsing are shared with the other providers via
 * [TranslationPrompts] and [TranslationParsing], so switching provider cannot
 * silently change what is asked for or how the reply is read.
 *
 * Failures propagate as exceptions — callers distinguish them from a real
 * translation by catching, not by inspecting the returned text.
 */
class GeminiTranslator(
    /**
     * Supplies this agent's client. A lambda rather than a [Client] because
     * building one loads a heavy dependency graph; resolving it here would
     * happen on whatever thread constructs the translator, and doing that during
     * startup trips Android's process-attach watchdog. Called inside
     * [translate], which is already on Dispatchers.IO.
     */
    private val clientProvider: () -> Client,
    /** Interpretive stance appended to the shared system prompt; see [TranslationPrompts.PERSONAS]. */
    private val persona: String? = null,
    private val modelId: String = DEFAULT_MODEL,
    /** Key slot this agent draws on — logged so a quota failure names a key. */
    private val keySlot: Int = 0,
) : Translator {

    companion object {
        private const val TAG = "GeminiTranslator"
        // gemini-3.7-flash returned 503 "experiencing high demand" on every
        // request when this was wired up; 2.5-flash was available and is what
        // the pipeline was validated against.
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    }

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    private val _progress = MutableStateFlow(DebateProgress())
    override val progress: StateFlow<DebateProgress> = _progress.asStateFlow()

    private val systemPrompt: String = TranslationPrompts.SYSTEM

    override suspend fun translate(
        words: List<String>,
        emotion: EmotionReading?,
    ): Translation = withContext(Dispatchers.IO) {
        require(words.isNotEmpty()) { "no glosses to translate" }

        // One retry: a model that rambles once usually complies on a re-ask, and
        // a second round trip is cheaper than falling back to raw glosses.
        // Note this catches parse failures only — a 429 or 503 propagates
        // immediately, which is what we want here since the debate tolerates a
        // missing agent and retrying an exhausted quota would only stall.
        var lastError: Exception? = null
        _stage.value = TranslationStage.CONSULTING
        try {
            repeat(2) { attempt ->
                try {
                    val raw = complete(
                        systemPrompt,
                        TranslationPrompts.userTurn(words, emotion),
                        clientProvider(),
                        modelId,
                    )
                    val translation = TranslationParsing.extractTranslation(raw)
                    Log.i(TAG, "key[$keySlot] $modelId $words -> ${translation.ms}")
                    return@withContext translation
                } catch (e: IllegalArgumentException) {
                    lastError = e
                    Log.w(TAG, "key[$keySlot] unparseable reply, attempt ${attempt + 1}: ${e.message}")
                }
            }
        } finally {
            _stage.value = TranslationStage.IDLE
        }
        throw lastError ?: IllegalStateException("translation failed")
    }
}

/**
 * One raw Gemini completion on a specific [client], and therefore a specific API
 * key. Shared by [GeminiTranslator] and the judge call in [DebateTranslator],
 * which needs the same transport but not the translation contract.
 *
 * Must be called off the main thread — see [GeminiClients].
 */
internal fun complete(
    system: String,
    user: String,
    client: Client,
    modelId: String = GeminiTranslator.DEFAULT_MODEL,
): String {
    val config = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(system)))
        .build()
    return client.models.generateContent(modelId, user, config).text() ?: ""
}
