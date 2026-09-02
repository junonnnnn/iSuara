package com.isuara.app.emotion

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A square face region in pixels, in full-frame coordinates.
 *
 * Plain Kotlin rather than `android.graphics.Rect` so the box maths is testable
 * as an ordinary JVM unit test — `Rect` is stubbed to zero under the standard
 * unit-test runtime and would make every assertion pass vacuously.
 */
data class FaceBox(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Locates the signer's face from the pose landmarks the pipeline already
 * produces.
 *
 * No face detector is needed and none is added: `LandmarkExtractor` publishes 33
 * pose landmarks, of which indices 0-10 are nose, eyes, ears and mouth. Adding a
 * third MediaPipe graph to find something we already know the location of would
 * cost a model download and a per-frame inference for nothing.
 *
 * Sizing is driven by the ear-to-ear span rather than by the bounding box of the
 * landmarks, because those 11 points cover only the middle band of the face —
 * they stop at the eyebrows and the mouth, so their bounding box omits the
 * forehead and the chin entirely. AffectNet crops include both, and feeding the
 * model a mid-face band measurably degrades it.
 */
object FaceCropper {

    /** Stride of the pose block in the feature vector: x, y, z, visibility. */
    private const val POSE_STRIDE = 4

    /** Matches the visibility gate the landmark overlay already uses. */
    private const val MIN_VISIBILITY = 0.5f

    /** Below this, too little of the face is showing to size a box from. */
    private const val MIN_VISIBLE_POINTS = 3

    /**
     * Face width as a multiple of the ear-to-ear span. Ears sit fractionally
     * inside the silhouette, so the span slightly under-measures the face.
     */
    private const val EAR_SPAN_TO_FACE_WIDTH = 1.05f

    /** Face width as a multiple of interocular distance, used when ears are hidden. */
    private const val EYE_SPAN_TO_FACE_WIDTH = 2.2f

    /** Square side as a multiple of face width; covers hairline to chin with margin. */
    private const val BOX_SCALE = 1.5f

    /**
     * Upward bias, as a fraction of the box side. The nose tip sits below the
     * vertical middle of the face, so a box centred on it clips forehead.
     * Mirrors the upward bias already applied to the hand crop.
     */
    private const val VERTICAL_BIAS = 0.06f

    /**
     * Reject a crop that lost more than this fraction of its intended side to
     * the frame edge. A half-visible face still yields a confident prediction,
     * and a confident wrong reading is worse for us than no reading at all.
     */
    private const val MAX_CLIP_FRACTION = 0.20f

    /** Upscaling a tiny face to 224 invents detail the model then reads as expression. */
    private const val MIN_BOX_PX = 48

    // Pose landmark indices (MediaPipe BlazePose full-body topology).
    private const val NOSE = 0
    private const val LEFT_EYE = 2
    private const val RIGHT_EYE = 5
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LAST_FACE_POINT = 10

    private fun x(kp: FloatArray, i: Int) = kp[i * POSE_STRIDE]
    private fun y(kp: FloatArray, i: Int) = kp[i * POSE_STRIDE + 1]
    private fun visible(kp: FloatArray, i: Int) = kp[i * POSE_STRIDE + 3] > MIN_VISIBILITY

    /**
     * The square face box for [keypoints], or null when the face cannot be
     * located reliably.
     *
     * [keypoints] is the raw 258-float feature vector from `LandmarkExtractor`,
     * whose coordinates are normalised to the full frame.
     */
    fun faceBox(keypoints: FloatArray, imageWidth: Int, imageHeight: Int): FaceBox? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (keypoints.size < (LAST_FACE_POINT + 1) * POSE_STRIDE) return null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var sumX = 0f
        var sumY = 0f
        var seen = 0
        for (i in NOSE..LAST_FACE_POINT) {
            if (!visible(keypoints, i)) continue
            val px = x(keypoints, i)
            minX = minOf(minX, px)
            maxX = maxOf(maxX, px)
            sumX += px
            sumY += y(keypoints, i)
            seen++
        }
        if (seen < MIN_VISIBLE_POINTS) return null

        // Prefer the ear span; fall back to the eyes when the head is turned and
        // one ear is occluded; fall back again to the raw landmark spread.
        val earSpanPx = if (visible(keypoints, LEFT_EAR) && visible(keypoints, RIGHT_EAR)) {
            abs(x(keypoints, LEFT_EAR) - x(keypoints, RIGHT_EAR)) * imageWidth
        } else 0f
        val eyeSpanPx = if (visible(keypoints, LEFT_EYE) && visible(keypoints, RIGHT_EYE)) {
            abs(x(keypoints, LEFT_EYE) - x(keypoints, RIGHT_EYE)) * imageWidth
        } else 0f

        val faceWidth = max(
            max(earSpanPx * EAR_SPAN_TO_FACE_WIDTH, eyeSpanPx * EYE_SPAN_TO_FACE_WIDTH),
            (maxX - minX) * imageWidth,
        )
        if (faceWidth <= 0f) return null

        val side = faceWidth * BOX_SCALE
        val centerX = if (visible(keypoints, NOSE)) x(keypoints, NOSE) * imageWidth
                      else (sumX / seen) * imageWidth
        val centerY = (if (visible(keypoints, NOSE)) y(keypoints, NOSE) * imageHeight
                       else (sumY / seen) * imageHeight) - side * VERTICAL_BIAS

        val idealLeft = centerX - side / 2f
        val idealTop = centerY - side / 2f

        val left = idealLeft.coerceIn(0f, (imageWidth - 1).toFloat())
        val top = idealTop.coerceIn(0f, (imageHeight - 1).toFloat())
        val right = (idealLeft + side).coerceIn(0f, imageWidth.toFloat())
        val bottom = (idealTop + side).coerceIn(0f, imageHeight.toFloat())

        val width = (right - left).roundToInt()
        val height = (bottom - top).roundToInt()
        if (width < MIN_BOX_PX || height < MIN_BOX_PX) return null

        // Reject a badly clipped face rather than handing the model a partial one.
        val lost = 1f - (minOf(width, height) / side)
        if (lost > MAX_CLIP_FRACTION) return null

        // Keep it square: the model expects a square crop, and a stretched face
        // shifts every geometric cue the expression classifier relies on.
        val squareSide = minOf(width, height)
        return FaceBox(left.roundToInt(), top.roundToInt(), squareSide, squareSide)
    }

    /**
     * The cropped face for [keypoints], or null when [faceBox] declines.
     *
     * Allocates a new bitmap per call. That is acceptable only because
     * [EmotionTracker] throttles this to a few times a second; do not move this
     * onto the per-frame path.
     */
    fun cropFace(bitmap: Bitmap, keypoints: FloatArray): Bitmap? {
        val box = faceBox(keypoints, bitmap.width, bitmap.height) ?: return null
        if (box.left + box.width > bitmap.width || box.top + box.height > bitmap.height) return null
        return Bitmap.createBitmap(bitmap, box.left, box.top, box.width, box.height)
    }
}
