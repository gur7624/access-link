package com.shinhwa.accesslinktester.ui.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private const val MODEL_FILE = "facenet.tflite"
private const val INPUT_SIZE = 160
private const val EMBEDDING_SIZE = 128
private const val FLOAT_BYTES = 4

class FaceRecognitionEngine(context: Context) : AutoCloseable {
    private val interpreter: Interpreter = Interpreter(
        loadModel(context),
        Interpreter.Options().apply {
            numThreads = 4
            setUseXNNPACK(true)
            useNNAPI = true
        }
    )

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val input = bitmapToInput(faceBitmap)
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        synchronized(interpreter) {
            interpreter.run(input, output)
        }
        return output[0]
    }

    override fun close() {
        interpreter.close()
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val values = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
        var valueIndex = 0
        pixels.forEach { pixel ->
            values[valueIndex++] = ((pixel shr 16) and 0xFF).toFloat()
            values[valueIndex++] = ((pixel shr 8) and 0xFF).toFloat()
            values[valueIndex++] = (pixel and 0xFF).toFloat()
        }

        val mean = values.average().toFloat()
        var std = sqrt(values.sumOf { (it - mean).toDouble().pow(2.0) }.toFloat() / values.size)
        std = max(std, 1f / sqrt(values.size.toFloat()))

        val buffer = ByteBuffer
            .allocateDirect(values.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        values.forEach { value ->
            buffer.putFloat((value - mean) / std)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModel(context: Context): ByteBuffer {
        val bytes = context.assets.open(MODEL_FILE).use { input ->
            input.readBytes()
        }
        return ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }
}
