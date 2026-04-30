package com.tunix.nazar.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import kotlin.math.max

class BinaryNsfwInterpreter(
    private val context: Context,
    private val modelFileName: String = "nsfw_binary.tflite",
    private val numThreads: Int = DEFAULT_THREAD_COUNT
) {

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null
    private var pixelBuffer: IntArray? = null
    private var isModelLoaded: Boolean = false

    @Volatile
    var lastErrorMessage: String? = null
        private set

    @Synchronized
    fun loadModel(): Boolean {
        close()
        lastErrorMessage = null

        return try {
            val modelBuffer = loadModelFile(modelFileName)

            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
            }

            interpreter = Interpreter(modelBuffer, options)

            inputBuffer = ByteBuffer.allocateDirect(
                FLOAT_SIZE_BYTES * INPUT_WIDTH * INPUT_HEIGHT * CHANNEL_COUNT
            ).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            outputBuffer = Array(1) { FloatArray(OUTPUT_CLASS_COUNT) }
            pixelBuffer = IntArray(INPUT_WIDTH * INPUT_HEIGHT)

            isModelLoaded = true
            true
        } catch (e: Exception) {
            isModelLoaded = false
            lastErrorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Binary NSFW modeli yüklenemedi"}"
            false
        }
    }

    fun isReady(): Boolean {
        return isModelLoaded && interpreter != null
    }

    @Synchronized
    fun classify(bitmap: Bitmap): BinaryNsfwResult {
        val currentInterpreter = interpreter
            ?: return BinaryNsfwResult.empty("interpreter_not_ready")

        val currentInputBuffer = inputBuffer
            ?: return BinaryNsfwResult.empty("input_buffer_not_ready")

        val currentOutputBuffer = outputBuffer
            ?: return BinaryNsfwResult.empty("output_buffer_not_ready")

        if (bitmap.isRecycled) {
            return BinaryNsfwResult.empty("bitmap_recycled")
        }

        return try {
            preprocess(bitmap, currentInputBuffer)

            currentOutputBuffer[0].fill(0f)
            currentInterpreter.run(currentInputBuffer, currentOutputBuffer)

            /*
             * Repo referansı:
             * output[0][0] = sfwScore
             * output[0][1] = nsfwScore
             */
            val sfwScore = currentOutputBuffer[0]
                .getOrElse(SFW_INDEX) { 0f }
                .coerceIn(0f, 1f)

            val nsfwScore = currentOutputBuffer[0]
                .getOrElse(NSFW_INDEX) { 0f }
                .coerceIn(0f, 1f)

            BinaryNsfwResult(
                nsfwScore = nsfwScore,
                sfwScore = sfwScore,
                isReady = true,
                source = "binary"
            )
        } catch (e: Exception) {
            lastErrorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Binary NSFW sınıflandırma hatası"}"
            BinaryNsfwResult.empty("classification_error")
        }
    }

    @Synchronized
    fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {
        }

        interpreter = null
        inputBuffer = null
        outputBuffer = null
        pixelBuffer = null
        isModelLoaded = false
        lastErrorMessage = null
    }

    private fun preprocess(bitmap: Bitmap, targetBuffer: ByteBuffer) {
        /*
         * Flutter NSFW reposundaki Android native koduyla aynı preprocess:
         *
         * 1. Bitmap 256x256 yapılır.
         * 2. Merkezden 224x224 alan alınır.
         * 3. Kanal sırası BGR'dir.
         * 4. Normalize edilmez; mean subtraction yapılır:
         *    B - 104, G - 117, R - 123
         */
        val resizedBitmap =
            if (bitmap.width == RESIZE_SIZE && bitmap.height == RESIZE_SIZE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    RESIZE_SIZE,
                    RESIZE_SIZE,
                    true
                )
            }

        try {
            val pixels = pixelBuffer ?: IntArray(INPUT_WIDTH * INPUT_HEIGHT).also {
                pixelBuffer = it
            }

            val startX = max((resizedBitmap.width - INPUT_WIDTH) / 2, 0)
            val startY = max((resizedBitmap.height - INPUT_HEIGHT) / 2, 0)

            resizedBitmap.getPixels(
                pixels,
                0,
                INPUT_WIDTH,
                startX,
                startY,
                INPUT_WIDTH,
                INPUT_HEIGHT
            )

            targetBuffer.rewind()

            for (pixel in pixels) {
                targetBuffer.putFloat((Color.blue(pixel) - BLUE_MEAN).toFloat())
                targetBuffer.putFloat((Color.green(pixel) - GREEN_MEAN).toFloat())
                targetBuffer.putFloat((Color.red(pixel) - RED_MEAN).toFloat())
            }

            targetBuffer.rewind()
        } finally {
            if (resizedBitmap !== bitmap && !resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        return FileUtil.loadMappedFile(context, fileName)
    }

    companion object {
        private const val DEFAULT_THREAD_COUNT = 4

        private const val RESIZE_SIZE = 256
        private const val INPUT_WIDTH = 224
        private const val INPUT_HEIGHT = 224
        private const val CHANNEL_COUNT = 3
        private const val FLOAT_SIZE_BYTES = 4

        private const val OUTPUT_CLASS_COUNT = 2
        private const val SFW_INDEX = 0
        private const val NSFW_INDEX = 1

        private const val BLUE_MEAN = 104
        private const val GREEN_MEAN = 117
        private const val RED_MEAN = 123
    }
}

data class BinaryNsfwResult(
    val nsfwScore: Float = 0f,
    val sfwScore: Float = 1f,
    val isReady: Boolean = false,
    val source: String = "unknown"
) {
    val isNsfwDominant: Boolean
        get() = nsfwScore > sfwScore

    companion object {
        fun empty(source: String): BinaryNsfwResult {
            return BinaryNsfwResult(
                nsfwScore = 0f,
                sfwScore = 1f,
                isReady = false,
                source = source
            )
        }
    }
}