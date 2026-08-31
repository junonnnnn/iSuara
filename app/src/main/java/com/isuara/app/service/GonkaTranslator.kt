package com.isuara.app.service

import android.util.Log
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * GonkaTranslator — turns detected BIM sign keywords into a natural sentence in
 * Malay, English, Simplified Chinese and Tamil in a single call, via GonkaRouter
 * (which speaks the Anthropic Messages API).
 *
 * The JSON contract is enforced by prompt, not by schema: GonkaRouter accepts
 * `output_config.format` with HTTP 200 but silently ignores it, returning
 * free-form prose. Relying on it would fail at parse time in production, so the
 * shape is demanded in the system prompt and validated here instead.
 *
 * [modelId] is a constructor parameter so the planned voting feature can point
 * several instances at different models and run them concurrently; they all
 * share the one client in [GonkaClient].
 *
 * Failures propagate as exceptions — callers distinguish them from a real
 * translation by catching, not by inspecting the returned text.
 */
class GonkaTranslator(
    private val modelId: String = DEFAULT_MODEL
) : Translator {

    companion object {
        private const val TAG = "GonkaTranslator"
        const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-V4-Flash-0731"

        // One sentence was ~30 output tokens; three plus JSON scaffolding
        // measured ~141, and Tamil script adds roughly a third again. The
        // headroom also covers models that prepend a reasoning monologue.
        private const val MAX_TOKENS = 2048L

        private val SYSTEM_PROMPT = """
            You are a professional Bahasa Isyarat Malaysia (BIM) sign language interpreter.

            Rules:
            1. Rearrange and expand the BIM keywords (glosses) into a natural Bahasa Melayu sentence (Subject + Verb + Object).
            2. Infer context and add implied verbs (e.g., "rasa," "mahu"), emotions (e.g., "Gembira", "Sedih", "Kecewa", "Maaf") and grammatical particles.
            3. If a phrase is ambiguous, default to the most direct, standard interpretation.
            4. Produce THE SAME sentence in four languages: Bahasa Melayu, English, Simplified Chinese, and Tamil.
            5. Write Tamil in Tamil script, not romanised.
            6. Each language must be ONE single sentence. Do NOT explain your process.

            Return ONLY a single JSON object. No markdown, no code fences, no commentary, no reasoning:
            {"ms": "<Bahasa Melayu>", "en": "<English>", "zh": "<Simplified Chinese>", "ta": "<Tamil>"}

            Examples:
            Input: [Polis, Siapa, Salah]
            Output: {"ms": "Siapa yang polis salahkan tadi?", "en": "Who did the police blame just now?", "zh": "警察刚才怪谁?", "ta": "காவல்துறை யாரை குற்றம் சாட்டியது?"}
        """.trimIndent()

        /**
         * Pulls the translation object out of a raw model reply.
         *
         * Tolerant of the two things models actually do: wrapping the object in
         * markdown fences, and prepending a visible reasoning monologue (the
         * latency benchmark saw MiniMax leak `<think>` blocks on 10/10 runs).
         * Both are handled by taking the outermost brace-delimited span.
         *
         * @throws IllegalArgumentException if no complete object is present or
         *   any of the four languages is missing or blank.
         */
        internal fun extractTranslation(raw: String): Translation {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "no JSON object in reply: ${raw.take(200)}" }

            val obj = try {
                JSONObject(raw.substring(start, end + 1))
            } catch (e: Exception) {
                throw IllegalArgumentException("malformed JSON: ${raw.take(200)}", e)
            }

            val ms = obj.optString("ms").trim()
            val en = obj.optString("en").trim()
            val zh = obj.optString("zh").trim()
            val ta = obj.optString("ta").trim()
            require(ms.isNotEmpty() && en.isNotEmpty() && zh.isNotEmpty() && ta.isNotEmpty()) {
                "missing language in reply: ms=${ms.isNotEmpty()} en=${en.isNotEmpty()} " +
                    "zh=${zh.isNotEmpty()} ta=${ta.isNotEmpty()}"
            }
            return Translation(ms = ms, en = en, zh = zh, ta = ta)
        }
    }

    override suspend fun translate(words: List<String>): Translation = withContext(Dispatchers.IO) {
        require(words.isNotEmpty()) { "no glosses to translate" }

        val params = MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(MAX_TOKENS)
            .system(SYSTEM_PROMPT)
            .addUserMessage("Input: $words\nOutput:")
            .build()

        // One retry: a model that rambles once usually complies on a re-ask, and
        // a second round trip is cheaper than falling back to raw glosses.
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val raw = GonkaClient.client.messages().create(params)
                    .content()
                    .mapNotNull { it.text().orElse(null) }
                    .joinToString("") { it.text() }

                val translation = extractTranslation(raw)
                Log.i(TAG, "Translation [$modelId] $words -> ${translation.ms}")
                return@withContext translation
            } catch (e: IllegalArgumentException) {
                lastError = e
                Log.w(TAG, "Unparseable reply on attempt ${attempt + 1}: ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("translation failed")
    }
}
