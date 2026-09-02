package com.isuara.app.emotion

/**
 * The decision rules that turn a set of per-frame distributions into one label.
 *
 * Pure and Android-free so the rules can be unit-tested directly. They are the
 * part of the emotion path most likely to need tuning against real signers, and
 * tuning without tests would be guesswork.
 */
object EmotionAggregator {

    /**
     * Share of probability mass Neutral must hold to be reported.
     *
     * AffectNet-trained models return Neutral for the majority of real video
     * frames, so a plain argmax reports Neutral almost always and the feature
     * does nothing. Requiring a majority lets a weaker but genuine expressive
     * signal through, while still reporting calm when the face really is calm.
     */
    const val NEUTRAL_DOMINANCE = 0.55f

    /** Index of the highest value in [v]; 0 for an empty array. */
    fun argmax(v: FloatArray): Int {
        var best = 0
        for (i in v.indices) if (v[i] > v[best]) best = i
        return best
    }

    /** Index of the highest value in [v] other than [skip], or -1 if none exists. */
    fun argmaxExcluding(v: FloatArray, skip: Int): Int {
        var best = -1
        for (i in v.indices) if (i != skip && (best < 0 || v[i] > v[best])) best = i
        return best
    }

    /** Element-wise mean of [sum] over [count] samples. */
    fun mean(sum: FloatArray, count: Int): FloatArray {
        require(count > 0) { "cannot average zero samples" }
        return FloatArray(sum.size) { sum[it] / count }
    }

    /**
     * The reported class index for an averaged distribution [mean].
     *
     * Straight argmax, except that Neutral must clear [dominance] to win; below
     * that it yields to the strongest expressive class. Returns the argmax
     * unchanged when there is no alternative to fall back to.
     */
    fun resolve(mean: FloatArray, dominance: Float = NEUTRAL_DOMINANCE): Int {
        val top = argmax(mean)
        val neutral = EmotionLabel.NEUTRAL.ordinal
        if (top != neutral || mean.getOrElse(neutral) { 0f } >= dominance) return top
        val alternative = argmaxExcluding(mean, neutral)
        return if (alternative >= 0) alternative else top
    }
}
