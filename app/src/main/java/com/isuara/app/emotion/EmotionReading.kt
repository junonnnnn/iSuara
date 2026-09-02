package com.isuara.app.emotion

/**
 * A settled read of the signer's facial expression.
 *
 * Produced two ways by [EmotionTracker]: continuously for the on-screen chip,
 * and once per sentence for the translator and the voice. Both are the same
 * shape so the UI and the pipeline cannot disagree about what was observed.
 *
 * Absence is represented by a null [EmotionReading], never by a NEUTRAL one —
 * "no face in frame" and "a calm face" are different facts, and collapsing them
 * would have the translator assert calmness it never actually observed.
 */
data class EmotionReading(
    val label: EmotionLabel,
    /** Smoothed probability of [label], 0..1. */
    val confidence: Float,
    /**
     * The highest arousal seen across the window, not the mean.
     *
     * A single spike of fear inside an otherwise composed sentence is exactly
     * the signal this feature exists to surface; averaging would erase it.
     */
    val peakArousal: Float,
    /** True when the underlying frames are old enough that the face may have left. */
    val isStale: Boolean = false,
) {
    /** See [EmotionLabel.isHighArousal] — gates colloquial register and prosody. */
    val isHighArousal: Boolean get() = peakArousal >= EmotionLabel.HIGH_AROUSAL_THRESHOLD

    /** Compact form for logcat. */
    override fun toString(): String =
        "${label.descriptorEn}@${"%.2f".format(confidence)}" +
            " arousal=${"%.2f".format(peakArousal)}${if (isStale) " stale" else ""}"
}
