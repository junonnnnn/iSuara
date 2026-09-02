package com.isuara.app.emotion

/**
 * Turns a 224x224 ARGB image into the exact tensor the EmotiEffLib model wants.
 *
 * Split out from [EmotionClassifier] and kept free of Android types so it can be
 * unit-tested on the JVM against a golden fixture. This is deliberate: every
 * failure mode here is silent. A swapped channel order or a missing division by
 * 255 does not crash and does not look wrong — it produces confident, plausible,
 * wrong emotions. The fixture test is the only thing that catches that, and it
 * can only exist if this code has no Android dependency.
 *
 * The contract, taken from EmotiEffLib's `facial_analysis.py`:
 *   resize -> /255 -> (x - mean) / std per channel -> transpose to CHW -> batch 1
 *
 * Channel order is **RGB**, established from EmotiEffLib's own test suite, which
 * does `cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)` before handing frames to
 * either backend. The preprocess function itself contains no colour conversion,
 * which makes it look BGR at a glance; it is not. Android's ARGB_8888 already
 * gives us R, G, B in that order, so no conversion is needed here either.
 */
object EmotionPreprocessor {

    /** Input side for the `enet_b0` family. The b2 models would want 260. */
    const val INPUT_SIZE = 224

    /** Pixels per channel plane in the NCHW layout. */
    const val PLANE = INPUT_SIZE * INPUT_SIZE

    /** Total floats in one batch-1 tensor. */
    const val TENSOR_LENGTH = 3 * PLANE

    /** ImageNet statistics, as used by every non-`mbf_` EmotiEffLib model. */
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * Writes the normalised NCHW tensor for [argb] into [out] and returns it.
     *
     * [argb] must hold exactly [PLANE] pixels in `ARGB_8888` order, already
     * scaled to [INPUT_SIZE] square. [out] is caller-supplied so the hot path can
     * reuse one array; it must be [TENSOR_LENGTH] long.
     */
    fun toTensor(argb: IntArray, out: FloatArray = FloatArray(TENSOR_LENGTH)): FloatArray {
        require(argb.size == PLANE) { "expected $PLANE pixels, got ${argb.size}" }
        require(out.size == TENSOR_LENGTH) { "expected $TENSOR_LENGTH floats, got ${out.size}" }

        // Hoisted out of the loop: these are constant per channel, and this runs
        // 150,528 times per inference on the camera thread.
        val rScale = 1f / (255f * STD[0])
        val gScale = 1f / (255f * STD[1])
        val bScale = 1f / (255f * STD[2])
        val rShift = MEAN[0] / STD[0]
        val gShift = MEAN[1] / STD[1]
        val bShift = MEAN[2] / STD[2]

        for (p in 0 until PLANE) {
            val pixel = argb[p]
            out[p] = ((pixel shr 16) and 0xFF) * rScale - rShift
            out[PLANE + p] = ((pixel shr 8) and 0xFF) * gScale - gShift
            out[2 * PLANE + p] = (pixel and 0xFF) * bScale - bShift
        }
        return out
    }

    /**
     * Softmax over the model's raw logits.
     *
     * The model emits logits, not probabilities — EmotiEffLib applies softmax
     * outside the graph — so anything that wants a confidence must come through
     * here. Max-shifted for numerical stability.
     */
    fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        var sum = 0f
        val out = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = kotlin.math.exp((logits[i] - max).toDouble()).toFloat()
            out[i] = e
            sum += e
        }
        for (i in out.indices) out[i] /= sum
        return out
    }
}
