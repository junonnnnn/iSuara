package com.isuara.app.service

import android.util.Log
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * GeminiTranslator — the four-language translation over the Gemini API.
 *
 * Plain [HttpURLConnection] and `org.json`, deliberately: [GeminiTtsService]
 * already reaches this host that way and it works, so an SDK would add a
 * dependency to solve a problem we do not have.
 *
 * Takes an explicit [apiKey] rather than reading the key list itself, because
 * the debate runs one instance per key. Sharing one key across the three
 * concurrent agents would put three simultaneous requests on a single
 * per-minute quota, which is the one arrangement guaranteed to 429.
 *
 * Failures propagate as exceptions, unlike [GeminiTtsService] which returns
 * null. The contracts differ because the callers do: speech has a second engine
 * to fall back to, whereas a translation that quietly returned raw glosses would
 * be indistinguishable from a real one.
 */
class GeminiTranslator(
    private val apiKey: String,
    internal val modelId: String = MODEL,
    /**
     * Sampler temperature for generation.
     */
    private val temperature: Double = AGENT_TEMPERATURE,
) : Translator {

    companion object {
        private const val TAG = "GeminiTranslator"

        /** Same family as the TTS model but a different model, so a separate quota bucket. */
        const val MODEL = "gemini-3.1-flash-lite"

        /**
         * All three debate requests use gemini-3.1-flash-lite.
         */
        val AGENT_MODELS = listOf(
            MODEL,
            MODEL,
            MODEL,
        )

        /**
         * The model's tuned default — the most spread available without leaving
         * the distribution it was calibrated on. Higher diverges more but drops
         * format compliance on a task that must emit four languages including
         * Tamil script, and a malformed reply costs a whole agent.
         */
        const val AGENT_TEMPERATURE = 1.0

        /**
         * The judge selects, it does not write. Run-to-run variance in a
         * selection is a defect, and the judge is alone on the critical path.
         */
        const val JUDGE_TEMPERATURE = 0.0

        /**
         * The labels shown against each agent row, and quoted by the judge in
         * its reason.
         *
         * Display text only: nothing routes on these, and they are not model
         * ids. Positional, so the order here is the order the rows appear in.
         */
        val AGENT_LABELS = listOf(
            "DeepSeek V4-Flash",
            "MiniMax M2.7",
            "Kimi K2.6",
        )
    }

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    // A single model is a one-candidate debate with no judge, so the UI can
    // render it through the same path without special-casing.
    private val _progress = MutableStateFlow(DebateProgress())
    override val progress: StateFlow<DebateProgress> = _progress.asStateFlow()

    override suspend fun translate(
        words: List<String>,
        emotion: EmotionReading?,
    ): Translation = withContext(Dispatchers.IO) {
        require(words.isNotEmpty()) { "no glosses to translate" }

        // One retry, and only for an unparseable reply: a model that rambles
        // once usually complies on a re-ask.
        //
        // HTTP failures deliberately fall straight through without retrying,
        // and without borrowing another agent's key. A 429 here is a per-minute
        // quota, so an immediate re-ask returns the same 429; and because the
        // three agents fire simultaneously, a 429 usually reflects the burst
        // rather than that one key, so retrying on a neighbour's key can lose
        // two agents instead of one. Quota isolation is the entire reason each
        // agent holds its own key. DebateTranslator is built to judge the
        // survivors; it is not built to absorb delay.
        var lastError: Exception? = null
        _stage.value = TranslationStage.CONSULTING
        try {
            repeat(2) { attempt ->
                try {
                    val raw = geminiComplete(
                        apiKey = apiKey,
                        modelId = modelId,
                        system = TranslationPrompts.SYSTEM,
                        user = TranslationPrompts.userTurn(words, emotion),
                        temperature = temperature,
                    )
                    val translation = TranslationParsing.extractTranslation(raw)
                    Log.i(TAG, "Translation [$modelId t=$temperature] $words -> ${translation.ms}")
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

/** A non-200 from the Gemini API. Carries [code] so callers can tell 429 from 400. */
class GeminiApiException(val code: Int, message: String) : IOException(message)

private const val ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

/** TLS to this host is quick; a long connect budget only delays noticing we are offline. */
private const val CONNECT_TIMEOUT_MS = 5_000

/**
 * Generation lands entirely here — the call is not streamed, so the whole body
 * arrives at once. The TTS path measures 3.0-4.4s on a ~40-word prompt; this one
 * carries a far larger system prompt and generates four-language JSON. 15s
 * covers a slow call and caps the two-attempt worst case at 30s, which matters
 * because the translate path in CameraScreen has no outer timeout of its own.
 */
private const val READ_TIMEOUT_MS = 15_000

/**
 * Four sentences plus JSON scaffolding measured ~141 tokens, more with Tamil.
 * The headroom is not generosity: any thinking tokens count against this limit,
 * and exhausting it returns a 200 with `finishReason: MAX_TOKENS` and no text.
 */
private const val MAX_OUTPUT_TOKENS = 2048

/**
 * One raw Gemini completion. Shared by [GeminiTranslator] and the judge call in
 * [geminiDebate], which needs the same transport but not the translation contract.
 *
 * @throws GeminiApiException on any non-200.
 * @throws IllegalStateException on a 200 that carries no usable text.
 */
internal fun geminiComplete(
    apiKey: String,
    modelId: String,
    system: String,
    user: String,
    temperature: Double,
): String {
    val body = buildGeminiBody(system, user, temperature)

    val connection = (URL(ENDPOINT.format(modelId)).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("x-goog-api-key", apiKey)
    }

    try {
        // UTF-8 stated rather than assumed: unlike the TTS call, both the prompt
        // and the reply carry Chinese and Tamil.
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val detail = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }.orEmpty()
            throw GeminiApiException(code, "Gemini HTTP $code: ${detail.take(300)}")
        }
        val reply = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return extractGeminiText(reply)
    } finally {
        connection.disconnect()
    }
}

/**
 * The request body.
 *
 * The prompt goes in `systemInstruction` rather than a user part so it stays
 * byte-identical across every agent and every request. That is what makes a
 * difference between candidates mean something, and it keeps the long prefix
 * cacheable; folding it into the user turn would merge it with the per-request
 * emotion line and lose both properties.
 *
 * Pure and Android-free, so a JVM test can assert on it without a device.
 */
internal fun buildGeminiBody(system: String, user: String, temperature: Double): String =
    JSONObject().apply {
        put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))),
        )
        put("contents", JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", user)))
        }))
        put("generationConfig", JSONObject().apply {
            put("temperature", temperature)
            put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            // Both prompts already demand a bare JSON object; this makes the API
            // enforce it rather than hope. Safe to drop if a model rejects the
            // field — TranslationParsing still tolerates fences and preambles.
            put("responseMimeType", "application/json")
            // Off for latency: this is short-form translation, not reasoning,
            // and a thinking budget would eat MAX_OUTPUT_TOKENS as well as time.
            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        })
    }.toString()

/**
 * Pulls the model's text out of a 200 response.
 *
 * A 200 does not guarantee text. The model can finish with `MAX_TOKENS`,
 * `SAFETY` or `RECITATION` and return a candidate carrying no content part at
 * all — the text-side twin of the empty-audio case [GeminiTtsService] guards
 * against. Reading it optimistically would surface as a confusing NPE rather
 * than a cleanly failed agent.
 *
 * Pure and Android-free, so a JVM test can assert on it without a device.
 */
internal fun extractGeminiText(reply: String): String {
    val candidate = JSONObject(reply).optJSONArray("candidates")?.optJSONObject(0)
        ?: throw IllegalStateException("no candidates in reply: ${reply.take(300)}")
    val finish = candidate.optString("finishReason")
    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        ?: throw IllegalStateException("200 with no content, finishReason=$finish")

    val text = buildString {
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            // Skip thought parts if thinking was not actually disabled; the
            // answer is the part that is not one.
            if (part.optBoolean("thought")) continue
            append(part.optString("text"))
        }
    }
    if (text.isBlank()) {
        throw IllegalStateException("200 with empty text, finishReason=$finish")
    }
    return text
}

/**
 * The multi-agent debate over the Gemini API.
 *
 * One agent per API key. The fan-out is concurrent, so sharing a key would put
 * every agent on one per-minute quota; that also fixes the agent count at the
 * key count, and two keys give a two-agent debate while one key gives a single
 * candidate that [DebateTranslator] returns unjudged.
 *
 * All three debate requests use gemini-3.1-flash-lite with sampling diversity.
 *
 * The judge key rotates rather than being pinned. Pinning would give one key two
 * requests per translation against the others' one, and the free tier binds on
 * the busiest key: roughly 2.5 translations/min pinned against 3.75 rotating.
 * The judge also gets one fallback key, which the agents deliberately do not —
 * it fires after the agents have finished, so a second key is a sequential
 * retry rather than another simultaneous hit.
 *
 * Costs one call per agent plus the judge. Wall clock is max(agents) + judge.
 */
fun geminiDebate(apiKeys: List<String> = GeminiKeys.all): DebateTranslator {
    require(apiKeys.isNotEmpty()) { "geminiDebate needs at least one Gemini API key" }
    val nextJudgeKey = AtomicInteger(0)

    val models = GeminiTranslator.AGENT_MODELS
    return DebateTranslator(
        agents = apiKeys.mapIndexed { index, apiKey ->
            GeminiTranslator(
                apiKey = apiKey,
                modelId = models.getOrElse(index) { GeminiTranslator.MODEL },
            )
        },
        judgeCall = { system, user ->
            val start = nextJudgeKey.getAndIncrement().mod(apiKeys.size)
            val order = listOf(start, (start + 1).mod(apiKeys.size)).distinct()

            var reply: String? = null
            var lastError: Exception? = null
            for (i in order) {
                try {
                    reply = geminiComplete(
                        apiKey = apiKeys[i],
                        modelId = GeminiTranslator.MODEL,
                        system = system,
                        user = user,
                        temperature = GeminiTranslator.JUDGE_TEMPERATURE,
                    )
                    break
                } catch (e: Exception) {
                    lastError = e
                    Log.w("geminiDebate", "judge attempt on key $i failed: ${e.message}")
                }
            }
            reply ?: throw (lastError ?: IllegalStateException("judge call failed"))
        },
        // Display only, and not the model actually called — see [AGENT_LABELS].
        // Truncated to the key count so a two-key setup does not show a third
        // row that never fills.
        agentLabels = GeminiTranslator.AGENT_LABELS.take(apiKeys.size),
    )
}
