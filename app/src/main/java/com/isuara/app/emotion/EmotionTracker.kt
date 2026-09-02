package com.isuara.app.emotion

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EmotionTracker — samples the signer's face, smooths the result, and holds the
 * one reading the pipeline asks for at the end of a sentence.
 *
 * Sits beside the gloss pipeline rather than inside it. The gloss path is the
 * product; expression is an enrichment, so this is built to be droppable: it
 * throttles hard, skips whenever the classifier is busy, and every failure path
 * ends in "no reading" rather than an exception into the camera thread.
 *
 * Threading: [onFrame] is called on the camera analysis thread and does the
 * cheap work there (crop, staleness); inference runs on a private single thread
 * so a slow frame cannot stall landmark extraction. That single thread is also
 * what makes [EmotionClassifier]'s reused buffers safe.
 */
class EmotionTracker(private val classifier: EmotionClassifier) {

    companion object {
        private const val TAG = "EmotionTracker"

        /**
         * ~4 Hz. Expression changes on a timescale of hundreds of milliseconds,
         * so sampling faster buys nothing and costs frames on the gloss path.
         * Wall-clock rather than every-Nth-frame so the rate holds when FPS dips.
         */
        private const val MIN_INTERVAL_MS = 250L

        /**
         * How long a reading survives without a fresh face before the UI marks
         * it stale. Long enough to ride out a hand passing over the face.
         */
        private const val STALE_AFTER_MS = 1_500L

        /** Beyond this the face is gone, not merely occluded; drop the reading. */
        private const val EXPIRE_AFTER_MS = 3_000L

        /**
         * EMA weight on the newest distribution. Same technique and roughly the
         * same weighting as the landmark smoothing in SignPredictor.
         *
         * Applied to the probability vector, never to the winning label:
         * smoothing labels makes the chip flap between two near-tied classes,
         * whereas smoothing the distribution lets them resolve continuously.
         */
        private const val EMA_ALPHA = 0.3f

        /** Ignore a sentence window built from too few looks at the face. */
        private const val MIN_WINDOW_SAMPLES = 2
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "emotion-fer").apply { priority = Thread.MIN_PRIORITY }
    }
    private val inFlight = AtomicBoolean(false)
    private var lastSampleAt = 0L

    /** EMA state for the live chip. Touched only on the inference thread. */
    private var smoothed: FloatArray? = null

    @Volatile
    private var lastFaceAt = 0L

    /** Sentence window: running sum of distributions plus the arousal peak. */
    private val windowSum = FloatArray(EmotionLabel.COUNT)
    private var windowCount = 0
    private var windowPeakArousal = 0f
    private val windowLock = Any()

    private val _state = MutableStateFlow<EmotionReading?>(null)

    /** The live reading for the on-screen chip; null when no face is present. */
    val state: StateFlow<EmotionReading?> = _state.asStateFlow()

    /**
     * Offer a camera frame. Cheap and non-blocking; most calls return immediately.
     *
     * [keypoints] is the most recent landmark vector, which lags [bitmap] by a
     * frame — the same one-frame staleness the hand crop already accepts, and
     * far cheaper than synchronising the two.
     */
    fun onFrame(bitmap: Bitmap, keypoints: FloatArray?) {
        val now = System.currentTimeMillis()
        expireIfStale(now)

        if (keypoints == null) return
        if (now - lastSampleAt < MIN_INTERVAL_MS) return
        if (!inFlight.compareAndSet(false, true)) return
        lastSampleAt = now

        // Crop on THIS thread. The caller's bitmap is reused by the analyzer and
        // would be overwritten before a background thread could read it.
        val crop = try {
            FaceCropper.cropFace(bitmap, keypoints)
        } catch (e: Exception) {
            Log.w(TAG, "crop failed: ${e.message}")
            null
        }
        if (crop == null) {
            inFlight.set(false)
            return
        }
        // createBitmap can hand back the source itself for a full-frame box.
        val owned = if (crop === bitmap) crop.copy(Bitmap.Config.ARGB_8888, false) else crop

        executor.execute {
            try {
                classify(owned, now)
            } catch (e: Exception) {
                Log.w(TAG, "inference failed: ${e.message}")
            } finally {
                owned.recycle()
                inFlight.set(false)
            }
        }
    }

    private fun classify(crop: Bitmap, at: Long) {
        val probs = EmotionPreprocessor.softmax(classifier.predictLogits(crop))

        val prev = smoothed
        val next = if (prev == null) probs.copyOf() else FloatArray(probs.size) { i ->
            probs[i] * EMA_ALPHA + prev[i] * (1f - EMA_ALPHA)
        }
        smoothed = next
        lastFaceAt = at

        val best = EmotionAggregator.argmax(next)
        val label = EmotionLabel.fromIndex(best) ?: return

        synchronized(windowLock) {
            for (i in next.indices) windowSum[i] += next[i]
            windowCount++
            // Weighted by confidence so a barely-held fear does not register as
            // a full-strength spike.
            windowPeakArousal = maxOf(windowPeakArousal, label.arousal * next[best])
        }

        _state.value = EmotionReading(
            label = label,
            confidence = next[best],
            peakArousal = label.arousal,
            isStale = false,
        )
    }

    /**
     * The sentence-level reading, or null when the face was never seen clearly.
     *
     * Reads and clears: the window belongs to the sentence that just ended, and
     * leaving it in place would bleed one sentence's emotion into the next.
     */
    fun readSentenceEmotion(): EmotionReading? {
        val sum: FloatArray
        val count: Int
        val peak: Float
        synchronized(windowLock) {
            sum = windowSum.copyOf()
            count = windowCount
            peak = windowPeakArousal
            windowSum.fill(0f)
            windowCount = 0
            windowPeakArousal = 0f
        }
        if (count < MIN_WINDOW_SAMPLES) return null

        val mean = EmotionAggregator.mean(sum, count)
        val chosen = EmotionAggregator.resolve(mean)
        val label = EmotionLabel.fromIndex(chosen) ?: return null
        val reading = EmotionReading(
            label = label,
            confidence = mean[chosen],
            peakArousal = maxOf(peak, label.arousal),
            isStale = false,
        )
        Log.i(TAG, "sentence emotion over $count samples: $reading")
        return reading
    }

    /** Drop the window and the live reading. Call alongside a sentence reset. */
    fun reset() {
        synchronized(windowLock) {
            windowSum.fill(0f)
            windowCount = 0
            windowPeakArousal = 0f
        }
        smoothed = null
        lastFaceAt = 0L
        _state.value = null
    }

    private fun expireIfStale(now: Long) {
        val last = lastFaceAt
        if (last == 0L) return
        val age = now - last
        when {
            age > EXPIRE_AFTER_MS -> {
                smoothed = null
                lastFaceAt = 0L
                _state.value = null
            }
            age > STALE_AFTER_MS -> {
                _state.value = _state.value?.copy(isStale = true)
            }
        }
    }

    fun close() {
        executor.shutdownNow()
        classifier.close()
    }
}
