package com.isuara.app.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.isuara.app.BuildConfig
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * GeminiTtsService — expressive speech from the Gemini API.
 *
 * This exists because no other Google path can do it. Chirp 3 HD has no `ms-MY`
 * voice and no emotion control at all; the on-device engine has an `ms-MY` voice
 * but only pitch and rate. Gemini-TTS is the one option that speaks Malay *and*
 * takes a natural-language delivery instruction, which is the whole point of the
 * feature.
 *
 * The instruction is genuinely obeyed rather than read aloud — verified by
 * holding the payload fixed and varying instruction length; see
 * `tools/GEMINI_TTS_FINDINGS.md` for the measurements behind every constant here.
 *
 * Returns null on any failure rather than throwing. Failure is routine on this
 * tier, not exceptional: quota 429s, 200-responses carrying no audio, and a 400
 * when the model decides to answer the prompt instead of speaking it. Callers
 * fall back to [TtsService]; see [SpeechRouter].
 */
class GeminiTtsService(
    private val apiKeys: List<String> = BuildConfig.GEMINI_API_KEYS
        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
) {

    companion object {
        private const val TAG = "GeminiTtsService"

        /**
         * Sampled better than `gemini-2.5-flash-preview-tts` across the same
         * five emotions; `gemini-2.5-flash-tts` is not a valid id on this API.
         */
        private const val MODEL = "gemini-3.1-flash-tts-preview"

        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

        /** Warm, mid-range, and holds up across the emotional range. */
        private const val VOICE = "Kore"

        /** The API returns headerless PCM at a fixed rate: 16-bit mono 24 kHz. */
        private const val SAMPLE_RATE = 24_000

        /** Measured 3.0-4.4s end to end, so a 3s budget would reject good calls. */
        private const val TIMEOUT_MS = 8_000
    }

    /** Rotates keys across calls so one key's quota does not sink every request. */
    private val nextKey = AtomicInteger(0)

    @Volatile
    private var track: AudioTrack? = null

    val isConfigured: Boolean get() = apiKeys.isNotEmpty()

    /**
     * Synthesise [text] delivered per [emotion], returning raw PCM, or null if
     * every configured key failed.
     *
     * Tries each key once. The dominant failure is a per-key quota 429, which a
     * different key usually satisfies immediately; retrying the same key would
     * just burn the timeout budget.
     */
    suspend fun synthesize(
        text: String,
        language: Language,
        emotion: EmotionReading?,
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (text.isBlank() || apiKeys.isEmpty()) return@withContext null

        val prompt = buildPrompt(text, language, emotion)
        repeat(apiKeys.size) {
            val key = apiKeys[nextKey.getAndIncrement().mod(apiKeys.size)]
            val audio = runCatching { requestAudio(prompt, key) }
                .onFailure { Log.w(TAG, "synthesis attempt failed: ${it.message}") }
                .getOrNull()
            if (audio != null) return@withContext audio
        }
        Log.w(TAG, "all ${apiKeys.size} key(s) failed, caller should fall back")
        null
    }

    /**
     * The single text part: a delivery instruction, then the sentence.
     *
     * Never send bare text. A sentence with no instruction can make the model
     * answer it instead of speaking it, which comes back as a 400 rather than
     * audio. The instruction is always English even for Malay speech — that is
     * Google's guidance, and it measurably holds.
     */
    private fun buildPrompt(text: String, language: Language, emotion: EmotionReading?): String {
        val style = emotion?.label?.styleDirective
            ?: "Say this in a calm, clear, matter-of-fact voice."
        return "Speak the following in ${language.menuLabel}. $style: $text"
    }

    private fun requestAudio(prompt: String, apiKey: String): ByteArray? {
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().apply {
                put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", org.json.JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().put(
                    "voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE)),
                ))
            })
        }.toString()

        val connection = (URL(ENDPOINT.format(MODEL)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "HTTP $code: ${detail.take(180)}")
                return null
            }
            val reply = connection.inputStream.bufferedReader().use { it.readText() }
            return decodeAudio(reply)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Pulls the PCM out of a 200 response, or null if it carries none.
     *
     * A 200 does not guarantee audio: the model can finish with `OTHER` and
     * return a candidate with no content at all. Navigating that optimistically
     * would throw inside the caller instead of falling back cleanly.
     */
    private fun decodeAudio(reply: String): ByteArray? {
        val candidate = JSONObject(reply).optJSONArray("candidates")?.optJSONObject(0)
            ?: return null.also { Log.w(TAG, "no candidates in reply") }
        val content = candidate.optJSONObject("content")
            ?: return null.also {
                Log.w(TAG, "200 with no audio, finishReason=${candidate.optString("finishReason")}")
            }
        val data = content.optJSONArray("parts")?.optJSONObject(0)
            ?.optJSONObject("inlineData")?.optString("data")
        if (data.isNullOrEmpty()) return null
        return Base64.decode(data, Base64.DEFAULT)
    }

    /**
     * Play raw 16-bit mono PCM, interrupting anything already playing.
     *
     * A fresh [AudioTrack] per utterance: they are cheap, and reusing one across
     * utterances of different lengths means managing buffer state for no gain.
     */
    fun play(pcm: ByteArray) {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, pcm.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        player.write(pcm, 0, pcm.size)
        player.play()
        track = player
    }

    fun stop() {
        track?.let { player ->
            runCatching {
                if (player.state == AudioTrack.STATE_INITIALIZED) player.stop()
                player.release()
            }
        }
        track = null
    }
}
