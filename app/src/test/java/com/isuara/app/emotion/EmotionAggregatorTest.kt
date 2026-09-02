package com.isuara.app.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the neutral-suppression rule, which is the tuning knob most likely to
 * be revisited once real signers are in front of the camera.
 *
 * Distributions here are written as named-class maps rather than raw arrays so a
 * failure says which emotion won, not which index did.
 */
class EmotionAggregatorTest {

    private fun dist(vararg pairs: Pair<EmotionLabel, Float>): FloatArray {
        val out = FloatArray(EmotionLabel.COUNT)
        for ((label, p) in pairs) out[label.ordinal] = p
        return out
    }

    private fun resolved(v: FloatArray) = EmotionLabel.fromIndex(EmotionAggregator.resolve(v))

    @Test
    fun `a clear expressive winner is reported directly`() {
        val d = dist(
            EmotionLabel.FEAR to 0.62f,
            EmotionLabel.NEUTRAL to 0.20f,
            EmotionLabel.SURPRISE to 0.18f,
        )
        assertEquals(EmotionLabel.FEAR, resolved(d))
    }

    /**
     * The case the whole rule exists for: Neutral leads on a plurality, as it
     * does on most real video, but a genuine fear signal is present underneath.
     */
    @Test
    fun `a plurality of neutral yields to the expressive runner-up`() {
        val d = dist(
            EmotionLabel.NEUTRAL to 0.45f,
            EmotionLabel.FEAR to 0.35f,
            EmotionLabel.SADNESS to 0.20f,
        )
        assertEquals(EmotionLabel.FEAR, resolved(d))
    }

    @Test
    fun `a genuinely calm face still reports neutral`() {
        val d = dist(
            EmotionLabel.NEUTRAL to 0.80f,
            EmotionLabel.SADNESS to 0.12f,
            EmotionLabel.FEAR to 0.08f,
        )
        assertEquals(EmotionLabel.NEUTRAL, resolved(d))
    }

    /** Exactly at the threshold counts as dominant — the boundary is inclusive. */
    @Test
    fun `neutral at exactly the dominance threshold wins`() {
        val d = dist(
            EmotionLabel.NEUTRAL to EmotionAggregator.NEUTRAL_DOMINANCE,
            EmotionLabel.ANGER to 0.45f,
        )
        assertEquals(EmotionLabel.NEUTRAL, resolved(d))
    }

    @Test
    fun `mean averages the running sum over the sample count`() {
        val sum = dist(EmotionLabel.ANGER to 3f, EmotionLabel.NEUTRAL to 1f)
        val mean = EmotionAggregator.mean(sum, 4)
        assertEquals(0.75f, mean[EmotionLabel.ANGER.ordinal], 1e-6f)
        assertEquals(0.25f, mean[EmotionLabel.NEUTRAL.ordinal], 1e-6f)
    }

    @Test
    fun `averaging zero samples is rejected rather than dividing by zero`() {
        val e = runCatching { EmotionAggregator.mean(FloatArray(8), 0) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $e", e is IllegalArgumentException)
    }

    @Test
    fun `argmaxExcluding reports no alternative when only the skipped class exists`() {
        val single = floatArrayOf(1f)
        assertEquals(-1, EmotionAggregator.argmaxExcluding(single, 0))
    }

    /**
     * A degenerate all-neutral distribution must not fall through to -1 and
     * crash the lookup; resolve keeps the argmax when there is nothing to defer
     * to.
     */
    @Test
    fun `resolve is total even when nothing but neutral has mass`() {
        val d = dist(EmotionLabel.NEUTRAL to 0.1f)
        val label = resolved(d)
        assertTrue("resolve returned an out-of-range index", label != null)
    }
}
