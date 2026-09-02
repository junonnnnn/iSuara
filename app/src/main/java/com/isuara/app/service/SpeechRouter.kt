package com.isuara.app.service

import android.util.Log
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SpeechRouter — speaks with the best voice currently available.
 *
 * Prefers [GeminiTtsService], which can genuinely act a sentence, and falls back
 * to [TtsService], which can only shift pitch and rate. The UI talks to this and
 * never to either engine, so which one served an utterance is invisible.
 *
 * The fallback is not just an offline story. On the current tier the expressive
 * path fails routinely — quota 429s, 200s carrying no audio — so this is the
 * normal path being exercised, not an edge case. Anything that goes wrong, for
 * any reason, ends in a spoken sentence rather than silence: a mute app reads as
 * broken, and for an accessibility tool being heard is the entire product.
 */
class SpeechRouter(
    private val local: TtsService,
    private val cloud: GeminiTtsService = GeminiTtsService(),
) {

    companion object {
        private const val TAG = "SpeechRouter"

        /**
         * Ceiling on the expressive attempt. Measured latency is 3.0-4.4s, so
         * this leaves headroom for a slow call without letting a hung one hold
         * the user in silence.
         */
        private const val CLOUD_BUDGET_MS = 8_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Speak [text] in [language], delivered per [emotion].
     *
     * Returns immediately; synthesis happens off the main thread. [fallbackText]
     * is the Malay rendering, used when the requested on-device voice is not
     * installed — the same contract [TtsService.speak] already has.
     */
    fun speak(
        text: String,
        language: Language,
        fallbackText: String = text,
        emotion: EmotionReading? = null,
    ) {
        if (text.isBlank()) return
        stop()

        if (!cloud.isConfigured) {
            local.speak(text, language, fallbackText, emotion)
            return
        }

        scope.launch {
            val pcm = withTimeoutOrNull(CLOUD_BUDGET_MS) {
                runCatching { cloud.synthesize(text, language, emotion) }
                    .onFailure { Log.w(TAG, "expressive synthesis threw: ${it.message}") }
                    .getOrNull()
            }
            if (pcm != null) {
                runCatching { cloud.play(pcm) }
                    .onFailure {
                        Log.w(TAG, "playback failed, speaking locally: ${it.message}")
                        local.speak(text, language, fallbackText, emotion)
                    }
            } else {
                Log.i(TAG, "expressive voice unavailable, speaking on-device")
                local.speak(text, language, fallbackText, emotion)
            }
        }
    }

    /** Silence both engines — either may be mid-utterance. */
    fun stop() {
        cloud.stop()
        local.stop()
    }
}
