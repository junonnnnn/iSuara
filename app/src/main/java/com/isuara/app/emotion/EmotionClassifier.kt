package com.isuara.app.emotion

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

/**
 * EmotionClassifier — EfficientNet-B0 facial expression recognition over ONNX
 * Runtime.
 *
 * Weights are EmotiEffLib's `enet_b0_8_best_vgaf.onnx`, AffectNet-pretrained and
 * tuned on VGAF. VGAF is in-the-wild *video*, which is what a front-facing phone
 * camera produces; the AFEW variant is tuned on film clips and generalises worse
 * here. The b2 models score higher but are ~2.5x the compute, which we cannot
 * afford beside two MediaPipe graphs and the gloss LSTM.
 *
 * Lifecycle mirrors [com.isuara.app.ml.SignInterpreter]: construct, [predictLogits]
 * repeatedly, [close] once.
 *
 * NOT thread-safe. The scratch buffers are reused across calls to keep the
 * camera thread off the allocator, so exactly one thread may call
 * [predictLogits]. [EmotionTracker] owns that thread and is the only intended
 * caller.
 */
class EmotionClassifier(context: Context) {

    companion object {
        private const val TAG = "EmotionClassifier"
        private const val MODEL_ASSET = "emotion_enet_b0_8.onnx"

        /** Matches the ONNX graph's declared input and output names. */
        private const val INPUT_NAME = "input"
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Reused across calls — see the thread-safety note above.
    private val pixels = IntArray(EmotionPreprocessor.PLANE)
    private val tensor = FloatArray(EmotionPreprocessor.TENSOR_LENGTH)
    private val shape = longArrayOf(1, 3,
        EmotionPreprocessor.INPUT_SIZE.toLong(), EmotionPreprocessor.INPUT_SIZE.toLong())
    private var scratch: Bitmap? = null

    init {
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Two threads: enough to help, few enough to leave the camera and
            // MediaPipe threads their cores on a mid-range phone.
            setIntraOpNumThreads(2)
            // XNNPACK is a solid ~2x on ARM, but it is an optional EP and its
            // availability varies by AAR build. Failing to add it must not stop
            // us falling back to the plain CPU provider.
            runCatching { addXnnpack(emptyMap()) }
                .onFailure { Log.i(TAG, "XNNPACK unavailable, using default CPU EP: ${it.message}") }
        }
        session = env.createSession(bytes, options)
        Log.i(TAG, "Loaded $MODEL_ASSET (${bytes.size / 1024}KB)")
    }

    /**
     * The 8 raw logits for [faceCrop], in [EmotionLabel] declaration order.
     *
     * [faceCrop] is a square face region of any size; it is scaled to 224 here.
     * Pass it through [EmotionPreprocessor.softmax] for probabilities.
     */
    fun predictLogits(faceCrop: Bitmap): FloatArray {
        val scaled = scaleTo224(faceCrop)
        scaled.getPixels(pixels, 0, EmotionPreprocessor.INPUT_SIZE, 0, 0,
            EmotionPreprocessor.INPUT_SIZE, EmotionPreprocessor.INPUT_SIZE)
        EmotionPreprocessor.toTensor(pixels, tensor)

        OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape).use { input ->
            session.run(mapOf(INPUT_NAME to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = (result[0].value as Array<FloatArray>)[0]
                check(logits.size == EmotionLabel.COUNT) {
                    "model emitted ${logits.size} classes, expected ${EmotionLabel.COUNT}"
                }
                return logits
            }
        }
    }

    /**
     * [source] scaled to the model's input size, reusing one scratch bitmap.
     *
     * Filtered scaling matters: nearest-neighbour aliases the fine texture around
     * the eyes and mouth that the expression classifier keys on.
     */
    private fun scaleTo224(source: Bitmap): Bitmap {
        val size = EmotionPreprocessor.INPUT_SIZE
        if (source.width == size && source.height == size) return source
        val target = scratch ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            .also { scratch = it }
        android.graphics.Canvas(target).drawBitmap(
            source,
            android.graphics.Rect(0, 0, source.width, source.height),
            android.graphics.Rect(0, 0, size, size),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        return target
    }

    fun close() {
        runCatching { session.close() }
        scratch?.recycle()
        scratch = null
    }
}
