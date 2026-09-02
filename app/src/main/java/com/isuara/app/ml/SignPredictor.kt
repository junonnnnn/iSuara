package com.isuara.app.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.isuara.app.emotion.EmotionTracker
import com.isuara.app.service.Language
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @param emotionTracker optional facial-expression channel, fed from the same
 *   frames as the gloss pipeline. Null when the expression model failed to load,
 *   in which case everything here behaves exactly as it did before it existed.
 */
class SignPredictor(
    context: Context,
    private val emotionTracker: EmotionTracker? = null,
) {

    companion object {
        /**
         * The model's no-sign class. This is a control-flow sentinel, not a
         * word: it gates sentence building here and the auto-translate trigger
         * in CameraScreen, so it must never be compared against a translated
         * string.
         */
        const val IDLE = "Idle"
    }

    data class PredictionState(
        val currentWord: String = "",
        val confidence: Float = 0f,
        val isConfident: Boolean = false,
        val bufferProgress: Float = 0f,
        val sentence: List<String> = emptyList(),
        val keypoints: FloatArray? = null,
        val imageWidth: Int = 480,
        val imageHeight: Int = 640,
        val isWaitingForNewSentence: Boolean = false // ADD THIS LINE
    )

    private val landmarkExtractor = LandmarkExtractor(context, this::onLandmarksExtracted)
    private val signInterpreter = SignInterpreter(context)
    private val labels: List<String>
    private val glossTranslations: Map<String, Map<String, String>>
    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isPredicting = AtomicBoolean(false)
    private val cooldownCounter = AtomicInteger(0)
    private val frameBuffer = ArrayDeque<FloatArray>(31)
    private val sentenceWords = mutableListOf<String>()
    private var lastWord = ""
    private var previousFrame: FloatArray? = null // Holds the EMA state

    private val _state = MutableStateFlow(PredictionState())
    val state: StateFlow<PredictionState> = _state.asStateFlow()

    init {
        val jsonStr = context.assets.open("label_map.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        labels = root.getJSONArray("actions_ordered").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        val translationsObj = root.optJSONObject("translations")
        val parsed = mutableMapOf<String, Map<String, String>>()
        if (translationsObj != null) {
            for (gloss in labels) {
                val entry = translationsObj.optJSONObject(gloss) ?: continue
                val byLang = mutableMapOf<String, String>()
                entry.optString("en").takeIf { it.isNotBlank() }?.let { byLang["en"] = it }
                entry.optString("zh").takeIf { it.isNotBlank() }?.let { byLang["zh"] = it }
                if (byLang.isNotEmpty()) parsed[gloss] = byLang
            }
        }
        glossTranslations = parsed
    }

    /**
     * The gloss rendered in [language], or null when nothing extra should be
     * shown: Malay is canonical, "Idle" is a control-flow sentinel rather than a
     * sign, and an unmapped gloss should render no second row at all.
     *
     * Callers keep the Malay gloss as the value they store and send onward —
     * this is display only.
     */
    fun glossIn(gloss: String, language: Language): String? {
        val key = language.labelMapKey ?: return null
        if (gloss == IDLE) return null
        // updatePrediction appends "?" to a low-confidence word for display.
        val clean = gloss.removeSuffix("?")
        val translated = glossTranslations[clean]?.get(key) ?: return null
        return if (clean == gloss) translated else "$translated?"
    }

    // We added the isFrontCamera parameter here
    fun processFrame(bitmap: Bitmap, timestampMs: Long, isFrontCamera: Boolean = true) {
        // Update dimensions immediately for the UI mapping
        _state.update { it.copy(imageWidth = bitmap.width, imageHeight = bitmap.height) }

        // Pass the flag down to the extractor
        landmarkExtractor.extractAsync(bitmap, timestampMs, isFrontCamera)

        // Dispatched after landmark extraction so the gloss path, which is the
        // product, always gets the frame first. The keypoints handed over are
        // the previous frame's — extraction for this one has not finished — the
        // same one-frame lag the hand crop already relies on.
        emotionTracker?.onFrame(bitmap, _state.value.keypoints)
    }

    private fun onLandmarksExtracted(rawKeypoints: FloatArray?, timestampMs: Long) {
        _state.update { it.copy(keypoints = rawKeypoints) }

        // If no body/hands are detected, clear the previous frame so it doesn't
        // awkwardly "morph" when hands reappear.
        if (rawKeypoints == null) {
            previousFrame = null
            return
        }

        val rawNormalized = FrameNormalizer.normalizeSingleFrame(rawKeypoints)
        val smoothedNormalized = FloatArray(rawNormalized.size)
        val prev = previousFrame

        // --- APPLY EMA SMOOTHING FILTER ---
        if (prev == null) {
            // First frame: Nothing to smooth against, just use the raw values
            System.arraycopy(rawNormalized, 0, smoothedNormalized, 0, rawNormalized.size)
        } else {
            // EMA Math: alpha defines how much we trust the new frame vs the old frame.
            // 0.4f means 40% new frame, 60% old frame (Heavy smoothing)
            val alpha = 0.4f
            for (i in rawNormalized.indices) {
                smoothedNormalized[i] = (rawNormalized[i] * alpha) + (prev[i] * (1f - alpha))
            }
        }

        // Save this smoothed frame to be used as the "previous" frame next time
        previousFrame = smoothedNormalized.clone()
        // ----------------------------------

        var readyToPredict = false
        var snapshot: Array<FloatArray>? = null

        synchronized(frameBuffer) {
            // Add the smoothed data to the buffer instead of the raw data
            frameBuffer.addLast(smoothedNormalized)
            if (frameBuffer.size > 30) frameBuffer.removeFirst()

            // Check if ready, but don't launch coroutine inside the lock
            if (frameBuffer.size == 30 && cooldownCounter.get() <= 0 && !isPredicting.get()) {
                isPredicting.set(true) // Set immediately so we don't trigger twice
                readyToPredict = true
                snapshot = frameBuffer.toTypedArray()
            } else if (cooldownCounter.get() > 0) {
                cooldownCounter.decrementAndGet()
            }
            updateProgress()
        }

        // Launch inference OUTSIDE the lock so MediaPipe isn't blocked waiting for AI
        if (readyToPredict && snapshot != null) {
            inferenceScope.launch {
                try {
                    val features = FrameNormalizer.buildSequenceFeatures(snapshot!!)
                    val (idx, conf) = signInterpreter.predictTopClass(features)
                    updatePrediction(labels[idx], conf)
                } finally {
                    isPredicting.set(false)
                }
            }
        }
    }

    // ADD THIS VARIABLE RIGHT ABOVE updatePrediction
    private var startNewSentenceNextWord = false

    private fun updatePrediction(word: String, confidence: Float) {
        val isConfident = confidence >= 0.6f

        // WE CHANGED THIS IF STATEMENT to allow clearing the sentence gracefully
        if (isConfident && word != IDLE) {

            // If the timer finished, clear the old sentence before adding the new word!
            if (startNewSentenceNextWord) {
                sentenceWords.clear()
                lastWord = ""
                startNewSentenceNextWord = false
            }

            if (word != lastWord) {
                sentenceWords.add(word)
                lastWord = word
                if (sentenceWords.size > 8) sentenceWords.removeAt(0)
            }
        }

        _state.update { it.copy(
            currentWord = if (isConfident) word else "$word?",
            confidence = confidence,
            isConfident = isConfident,
            sentence = sentenceWords.toList(),
            bufferProgress = 1f,
            isWaitingForNewSentence = startNewSentenceNextWord // ADD THIS
        ) }
        cooldownCounter.set(if (isConfident) 10 else 5)
    }

    private fun updateProgress() {
        val progress = synchronized(frameBuffer) { frameBuffer.size.toFloat() / 30 }
        _state.update { it.copy(bufferProgress = progress) }
    }

    fun getSentenceWords() = sentenceWords.toList()
    fun resetAll() {
        synchronized(frameBuffer) { frameBuffer.clear() }
        sentenceWords.clear()
        lastWord = ""
        previousFrame = null
        startNewSentenceNextWord = false // ADD THIS
        emotionTracker?.reset()
        _state.update { PredictionState() }
    }

    // ADD THIS NEW FUNCTION AT THE BOTTOM
    fun prepareForNewSentence() {
        startNewSentenceNextWord = true
        _state.update { it.copy(isWaitingForNewSentence = true) }
    }
    fun close() {
        landmarkExtractor.close()
        signInterpreter.close()
    }
}