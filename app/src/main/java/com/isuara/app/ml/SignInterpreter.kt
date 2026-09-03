package com.isuara.app.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Input:  [1, 30, 780] float32
 * Output: [1, 98] float32 probabilities
 *
 * The FP16 model has FP16 weights but intentionally retains FP32 I/O.
 */
class SignInterpreter(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "SignInterpreter"

        private const val MODEL_FILE =
            "bim_lstm_v312_fp16.tflite"

        private const val NUM_CLASSES = 98
        private const val SEQUENCE_LENGTH = 30
        private const val NUM_FEATURES = 780

        private const val INPUT_FLOAT_COUNT =
            SEQUENCE_LENGTH * NUM_FEATURES

        private const val INPUT_BYTE_COUNT =
            INPUT_FLOAT_COUNT * Float.SIZE_BYTES
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    // Reused for every inference.
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_BYTE_COUNT).apply {
            order(ByteOrder.nativeOrder())
        }

    private val outputBuffer =
        Array(1) { FloatArray(NUM_CLASSES) }

    val usingGpu: Boolean
        get() = gpuDelegate != null

    init {
        val model = loadModelFile(context)
        var selectedInterpreter: Interpreter? = null

        try {
            val compatibilityList = CompatibilityList()

            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                val delegateOptions =
                    compatibilityList.bestOptionsForThisDevice.apply {
                        setPrecisionLossAllowed(true)
                        setInferencePreference(
                            GpuDelegate.Options
                                .INFERENCE_PREFERENCE_SUSTAINED_SPEED
                        )
                    }

                gpuDelegate = GpuDelegate(delegateOptions)

                val interpreterOptions = Interpreter.Options().apply {
                    // numThreads affects CPU kernels, not GPU execution.
                    addDelegate(gpuDelegate!!)
                }

                selectedInterpreter =
                    Interpreter(model, interpreterOptions)

                Log.i(TAG, "FP16 model initialized with GPU delegate")
            } else {
                Log.w(TAG, "TFLite GPU delegate unsupported")
            }
        } catch (error: Throwable) {
            Log.w(
                TAG,
                "GPU initialization failed: ${error.message}",
                error
            )

            gpuDelegate?.close()
            gpuDelegate = null
        }

        if (selectedInterpreter == null) {
            val cpuOptions = Interpreter.Options().apply {
                setNumThreads(
                    Runtime.getRuntime()
                        .availableProcessors()
                        .coerceIn(2, 4)
                )
            }

            selectedInterpreter = Interpreter(model, cpuOptions)
            Log.i(TAG, "Using CPU fallback")
        }

        interpreter = selectedInterpreter

        validateTensorShapes()
        warmUp()

        Log.i(
            TAG,
            "Loaded $MODEL_FILE; GPU=$usingGpu"
        )
    }

    private fun validateTensorShapes() {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()

        require(inputShape.contentEquals(intArrayOf(1, 30, 780))) {
            "Unexpected input shape: ${inputShape.contentToString()}"
        }

        require(outputShape.contentEquals(intArrayOf(1, 98))) {
            "Unexpected output shape: ${outputShape.contentToString()}"
        }

        Log.i(
            TAG,
            "Input=${inputShape.contentToString()} " +
                "${inputTensor.dataType()}, " +
                "output=${outputShape.contentToString()} " +
                outputTensor.dataType()
        )
    }

    /**
     * Warm-up is important because GPU delegate initialization and
     * shader compilation can make the first invocation unusually slow.
     */
    private fun warmUp() {
        val zeros = FloatArray(INPUT_FLOAT_COUNT)

        repeat(3) {
            predictInternal(zeros)
        }
    }

    private fun predictInternal(features: FloatArray): FloatArray {
        require(features.size == INPUT_FLOAT_COUNT) {
            "Expected $INPUT_FLOAT_COUNT values, got ${features.size}"
        }

        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(features)
        inputBuffer.rewind()

        interpreter.run(inputBuffer, outputBuffer)

        // Copy so callers never retain the mutable shared output array.
        return outputBuffer[0].copyOf()
    }

    fun predictTopClass(features: FloatArray): Pair<Int, Float> {
        val probabilities = predictInternal(features)

        var bestIndex = 0
        var bestConfidence = probabilities[0]

        for (index in 1 until NUM_CLASSES) {
            if (probabilities[index] > bestConfidence) {
                bestConfidence = probabilities[index]
                bestIndex = index
            }
        }

        return bestIndex to bestConfidence
    }

    /**
     * Useful for comparing GPU and CPU builds on the real device.
     */
    fun benchmark(
        features: FloatArray,
        iterations: Int = 30
    ): Double {
        repeat(5) {
            predictInternal(features)
        }

        val startNs = SystemClock.elapsedRealtimeNanos()

        repeat(iterations) {
            predictInternal(features)
        }

        return (
            SystemClock.elapsedRealtimeNanos() - startNs
        ) / 1_000_000.0 / iterations
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
        gpuDelegate = null
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_FILE).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                return stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }
    }
}