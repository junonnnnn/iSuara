package com.isuara.app.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [FaceCropper.faceBox] — the geometry that decides what the expression
 * model actually looks at.
 *
 * Asserts properties (square, contains the nose, plausible size, inside the
 * frame) rather than exact pixel values. The constants in [FaceCropper] are
 * empirical and will be retuned against real signers; a test pinned to today's
 * arithmetic would fail on every such tweak while telling us nothing about
 * whether the crop is still correct.
 */
class FaceCropperTest {

    private companion object {
        const val W = 480
        const val H = 640

        // MediaPipe BlazePose indices used by the cropper.
        const val NOSE = 0
        const val LEFT_EYE = 2
        const val RIGHT_EYE = 5
        const val LEFT_EAR = 7
        const val RIGHT_EAR = 8
        const val MOUTH_L = 9
        const val MOUTH_R = 10
    }

    /** The 258-float feature vector the landmark extractor publishes. */
    private fun keypoints(vararg points: Triple<Int, Pair<Float, Float>, Float>): FloatArray {
        val kp = FloatArray(258)
        for ((index, xy, visibility) in points) {
            kp[index * 4] = xy.first
            kp[index * 4 + 1] = xy.second
            kp[index * 4 + 2] = 0f
            kp[index * 4 + 3] = visibility
        }
        return kp
    }

    /**
     * A head facing the camera, with realistic proportions: the ear-to-ear span
     * is roughly the face width and the interocular distance roughly 0.45 of it.
     * [centerX] and [scale] move and resize it.
     */
    private fun frontalFace(centerX: Float = 0.5f, centerY: Float = 0.4f, scale: Float = 1f) =
        keypoints(
            Triple(NOSE, centerX to centerY, 1f),
            Triple(LEFT_EYE, (centerX - 0.033f * scale) to (centerY - 0.03f), 1f),
            Triple(RIGHT_EYE, (centerX + 0.033f * scale) to (centerY - 0.03f), 1f),
            Triple(LEFT_EAR, (centerX - 0.07f * scale) to (centerY - 0.02f), 1f),
            Triple(RIGHT_EAR, (centerX + 0.07f * scale) to (centerY - 0.02f), 1f),
            Triple(MOUTH_L, (centerX - 0.02f * scale) to (centerY + 0.04f), 1f),
            Triple(MOUTH_R, (centerX + 0.02f * scale) to (centerY + 0.04f), 1f),
        )

    @Test
    fun `a frontal face yields a square box around the nose`() {
        val box = FaceCropper.faceBox(frontalFace(), W, H)
        assertNotNull("expected a box for a clearly visible face", box)
        box!!

        assertEquals("box must be square", box.width, box.height)

        val noseX = 0.5f * W
        val noseY = 0.4f * H
        assertTrue("box must contain the nose horizontally",
            noseX > box.left && noseX < box.left + box.width)
        assertTrue("box must contain the nose vertically",
            noseY > box.top && noseY < box.top + box.height)

        assertTrue("box must lie inside the frame",
            box.left >= 0 && box.top >= 0 &&
                box.left + box.width <= W && box.top + box.height <= H)
    }

    /**
     * The box must be bigger than the landmark spread it was derived from. Those
     * 11 points stop at the eyebrows and mouth, so a box merely bounding them
     * would cut off the forehead and chin the model was trained on.
     */
    @Test
    fun `box is larger than the bare landmark spread`() {
        val box = FaceCropper.faceBox(frontalFace(), W, H)!!
        val earSpanPx = 0.14f * W
        assertTrue(
            "box (${box.width}px) should exceed the ear span (${earSpanPx}px)",
            box.width > earSpanPx,
        )
        assertTrue("box should not balloon past ~3x the ear span", box.width < earSpanPx * 3)
    }

    @Test
    fun `a face scales with the signer`() {
        val near = FaceCropper.faceBox(frontalFace(scale = 1.6f), W, H)!!
        val far = FaceCropper.faceBox(frontalFace(scale = 1f), W, H)!!
        assertTrue("a closer face must produce a bigger box", near.width > far.width)
    }

    /** Head turned away: one ear occluded, so sizing falls back to the eyes. */
    @Test
    fun `eyes alone are enough to size a box`() {
        val kp = keypoints(
            Triple(NOSE, 0.5f to 0.4f, 1f),
            Triple(LEFT_EYE, 0.467f to 0.37f, 1f),
            Triple(RIGHT_EYE, 0.533f to 0.37f, 1f),
        )
        assertNotNull("ears are not required to locate the face", FaceCropper.faceBox(kp, W, H))
    }

    @Test
    fun `too few visible landmarks yields no box`() {
        val kp = keypoints(
            Triple(NOSE, 0.5f to 0.4f, 1f),
            Triple(LEFT_EYE, 0.467f to 0.37f, 1f),
        )
        assertNull("two points is not enough to trust", FaceCropper.faceBox(kp, W, H))
    }

    /** Low visibility must be treated as absent, not as a point at the origin. */
    @Test
    fun `landmarks below the visibility gate are ignored`() {
        val kp = keypoints(
            Triple(NOSE, 0.5f to 0.4f, 0.1f),
            Triple(LEFT_EYE, 0.467f to 0.37f, 0.2f),
            Triple(RIGHT_EYE, 0.533f to 0.37f, 0.3f),
            Triple(LEFT_EAR, 0.43f to 0.38f, 0.4f),
        )
        assertNull(FaceCropper.faceBox(kp, W, H))
    }

    /**
     * A half-visible face still produces a confident prediction, and a confident
     * wrong reading is worse than none — so a badly clipped box is refused.
     */
    @Test
    fun `a face clipped by the frame edge yields no box`() {
        assertNull(
            "face hanging off the left edge must be refused",
            FaceCropper.faceBox(frontalFace(centerX = 0.02f), W, H),
        )
    }

    @Test
    fun `a face too small to upscale yields no box`() {
        assertNull(
            "a distant face would be invented detail once scaled to 224",
            FaceCropper.faceBox(frontalFace(scale = 0.12f), W, H),
        )
    }

    @Test
    fun `degenerate frame dimensions yield no box`() {
        assertNull(FaceCropper.faceBox(frontalFace(), 0, H))
        assertNull(FaceCropper.faceBox(frontalFace(), W, 0))
    }

    @Test
    fun `a truncated keypoint vector yields no box`() {
        assertNull(FaceCropper.faceBox(FloatArray(8), W, H))
    }

    @Test
    fun `an all-zero keypoint vector yields no box`() {
        assertNull(FaceCropper.faceBox(FloatArray(258), W, H))
    }
}
