package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeGenerator {

    /**
     * Encodes a given text string (e.g. student_code "ST-001") as a Code 128 1D Barcode Bitmap.
     * Returns null if encoding fails (e.g. empty string).
     */
    fun generateCode128Bitmap(
        content: String,
        width: Int = 600,
        height: Int = 200
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(
                content,
                BarcodeFormat.CODE_128,
                width,
                height
            )
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a Code 128 barcode directly into a Compose ImageBitmap for fast rendering in Compose.
     */
    fun generateCode128ImageBitmap(
        content: String,
        width: Int = 600,
        height: Int = 200
    ): ImageBitmap? {
        val bitmap = generateCode128Bitmap(content, width, height) ?: return null
        return bitmap.asImageBitmap()
    }
}
