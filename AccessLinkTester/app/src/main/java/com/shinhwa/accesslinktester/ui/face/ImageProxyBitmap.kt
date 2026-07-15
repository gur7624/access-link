package com.shinhwa.accesslinktester.ui.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toRotatedBitmap(): Bitmap? {
    val image = image ?: return null
    val nv21 = yuv420ToNv21(this)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val output = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, output)
    val bitmap = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()) ?: return null

    val rotation = imageInfo.rotationDegrees.toFloat()
    if (rotation == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
    val width = imageProxy.width
    val height = imageProxy.height
    val ySize = width * height
    val uvSize = width * height / 2
    val output = ByteArray(ySize + uvSize)
    val planes = imageProxy.planes

    var outputIndex = 0
    val yBuffer = planes[0].buffer
    val yRowStride = planes[0].rowStride
    for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(output, outputIndex, width)
        outputIndex += width
    }

    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val uRowStride = planes[1].rowStride
    val vRowStride = planes[2].rowStride
    val uPixelStride = planes[1].pixelStride
    val vPixelStride = planes[2].pixelStride
    val chromaHeight = height / 2
    val chromaWidth = width / 2

    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vRowStride + col * vPixelStride
            val uIndex = row * uRowStride + col * uPixelStride
            output[outputIndex++] = vBuffer.get(vIndex)
            output[outputIndex++] = uBuffer.get(uIndex)
        }
    }

    return output
}

fun Bitmap.cropFace(bounds: Rect): Bitmap? {
    val left = bounds.left.coerceIn(0, width - 1)
    val top = bounds.top.coerceIn(0, height - 1)
    val right = bounds.right.coerceIn(left + 1, width)
    val bottom = bounds.bottom.coerceIn(top + 1, height)
    if (right <= left || bottom <= top) return null
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}
