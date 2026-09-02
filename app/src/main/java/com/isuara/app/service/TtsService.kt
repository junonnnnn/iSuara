package com.isuara.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.isuara.app.emotion.EmotionReading
import java.util.Locale

/**
 * TtsService — Android TextToSpeech wrapper.
 *
 * Speaks in the language the signer selected. Voice data for a given language
 * is frequently absent on retail devices (Mandarin especially in Malaysia), so
 * an unavailable voice falls back to Malay and warns once rather than going
 * silently mute, which would read as a bug.
 */
class TtsService(context: Context) {

    companion object {
        private const val TAG = "TtsService"
        private val LOCALE_MS = Locale("ms", "MY")
        private val LOCALE_ID = Locale("id", "ID")

        /**
         * Slightly under normal speed, which reads as clearer for a synthetic
         * voice. Emotion scales this rather than replacing it, so the deliberate
         * clarity choice survives an expressive utterance.
         */
        private const val BASE_RATE = 0.9f
    }

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyMalay()
                tts?.setSpeechRate(BASE_RATE)
                isReady = true
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    /** Malay, falling back to Indonesian (very close) then English. */
    private fun applyMalay() {
        val engine = tts ?: return
        if (isUsable(engine.setLanguage(LOCALE_MS))) {
            Log.i(TAG, "Using Malay TTS")
            return
        }
        if (isUsable(engine.setLanguage(LOCALE_ID))) {
            Log.i(TAG, "Using Indonesian TTS (close to Malay)")
            return
        }
        engine.setLanguage(Locale.US)
        Log.w(TAG, "Malay & Indonesian TTS not available, using English")
    }

    private fun isUsable(result: Int?): Boolean =
        result != null &&
            result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED

    /**
     * Speak [text] in [language]. Interrupts any ongoing speech.
     *
     * If the requested voice is unavailable this speaks [fallbackText] (the
     * Malay rendering) instead.
     *
     * [emotion] shifts pitch and rate. It is a coarse effect — the on-device
     * engine has no emotion API at all, so this is the only lever available —
     * but a frightened sentence that is faster and higher still reads as
     * frightened, which flat delivery does not. The expressive path is
     * [GeminiTtsService]; this is what runs when that is unavailable.
     */
    fun speak(
        text: String,
        language: Language,
        fallbackText: String = text,
        emotion: EmotionReading? = null,
    ) {
        val engine = tts ?: return
        if (!isReady) return

        applyProsody(emotion)

        if (language == Language.MALAY) {
            applyMalay()
            if (text.isNotBlank()) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "isuara_tts")
            return
        }

        if (isUsable(engine.setLanguage(language.locale))) {
            if (text.isNotBlank()) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "isuara_tts")
            return
        }

        // Requested voice not installed — say the Malay version instead.
        Log.w(TAG, "${language.menuLabel} TTS unavailable, speaking Malay instead")
        applyMalay()
        if (fallbackText.isNotBlank()) {
            engine.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "isuara_tts")
        }
    }

    /**
     * Set pitch and rate for the next utterance.
     *
     * Always called, including with a null [emotion], so that a neutral sentence
     * following an angry one is reset rather than inheriting its prosody — these
     * settings persist on the engine until changed.
     */
    private fun applyProsody(emotion: EmotionReading?) {
        val engine = tts ?: return
        val label = emotion?.label
        engine.setPitch(label?.pitch ?: 1.0f)
        engine.setSpeechRate(BASE_RATE * (label?.rateScale ?: 1.0f))
    }

    fun stop() {
        tts?.stop()
    }

    fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
