package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.example.core.model.Student
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object BarcodeGenerator {

    /**
     * Encodes a given text string (e.g. student_code "ST-001") as a 2D QR Code Bitmap.
     * Uses UTF-8 character encoding and Error Correction Level H for crisp, reliable scanning.
     */
    fun generateQRCodeBitmap(
        content: String,
        size: Int = 512
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a QR code directly into a Compose ImageBitmap for fast rendering in Compose.
     */
    fun generateQRCodeImageBitmap(
        content: String,
        size: Int = 512
    ): ImageBitmap? {
        val bitmap = generateQRCodeBitmap(content, size) ?: return null
        return bitmap.asImageBitmap()
    }

    /**
     * Encodes a given text string (e.g. student_code "ST-001") as a Code 128 1D Barcode Bitmap.
     * Returns null if encoding fails (e.g. empty string or non-ASCII).
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

    /**
     * Generates a complete, beautiful student card Bitmap containing:
     * - School / App header
     * - Student Full Name
     * - Grade / Group Name
     * - High-contrast 2D QR Code
     * - Student Code
     */
    fun generateStudentQrCardBitmap(
        student: Student,
        gradeName: String? = null,
        width: Int = 800,
        height: Int = 1000
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white background
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.parseColor("#4F46E5") // Indigo primary
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
        }

        // Draw card outer frame
        val rect = RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(rect, 36f, 36f, borderPaint)

        // Header Background Banner
        val headerPaint = Paint().apply {
            color = Color.parseColor("#4F46E5")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val headerHeight = height * 0.15f
        val headerRect = RectF(20f, 20f, width - 20f, headerHeight)
        canvas.drawRoundRect(headerRect, 36f, 36f, headerPaint)
        // Square bottom of header
        canvas.drawRect(20f, headerHeight - 40f, width - 20f, headerHeight, headerPaint)

        // Header Text
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.052f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("تطبيق مَدَار — بطاقة الطالبة", width / 2f, headerHeight * 0.65f, headerTextPaint)

        // Student Full Name
        val namePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = width * 0.062f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val displayName = if (student.fullName.length > 25) student.fullName.take(23) + "..." else student.fullName
        canvas.drawText(displayName, width / 2f, height * 0.23f, namePaint)

        // Grade Name
        if (!gradeName.isNullOrBlank()) {
            val gradePaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = width * 0.042f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(gradeName, width / 2f, height * 0.29f, gradePaint)
        }

        // QR Code
        val qrCodeContent = student.studentCode.trim().ifBlank { "ST-000" }
        val qrSize = (width * 0.58f).toInt().coerceAtLeast(200)
        val qrBitmap = generateQRCodeBitmap(qrCodeContent, qrSize)

        if (qrBitmap != null) {
            val qrLeft = (width - qrBitmap.width) / 2f
            val qrTop = height * 0.33f

            // White container with light subtle shadow/border for QR
            val qrBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val qrBorderPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val qrBgRect = RectF(qrLeft - 16f, qrTop - 16f, qrLeft + qrBitmap.width + 16f, qrTop + qrBitmap.height + 16f)
            canvas.drawRoundRect(qrBgRect, 20f, 20f, qrBgPaint)
            canvas.drawRoundRect(qrBgRect, 20f, 20f, qrBorderPaint)

            canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)
        }

        // Student Code Badge
        val codeBadgePaint = Paint().apply {
            color = Color.parseColor("#EEF2FF")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeTop = height * 0.83f
        val badgeBottom = height * 0.91f
        val codeBadgeRect = RectF(width / 2f - (width * 0.28f), badgeTop, width / 2f + (width * 0.28f), badgeBottom)
        canvas.drawRoundRect(codeBadgeRect, 24f, 24f, codeBadgePaint)

        val codeTextPaint = Paint().apply {
            color = Color.parseColor("#4338CA")
            textSize = width * 0.046f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("الكود: $qrCodeContent", width / 2f, (badgeTop + badgeBottom) / 2f + (width * 0.015f), codeTextPaint)

        // Footer Hint
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = width * 0.032f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("امسح الرمز لتسجيل الحضور والتسميع تلقائيًا", width / 2f, height * 0.96f, footerPaint)

        return bitmap
    }

    /**
     * Saves a given Bitmap to the device's public Pictures / MIDAR_QR directory.
     * Uses MediaStore on Android 10+ (API 29+) with Scoped Storage, and falls back gracefully to
     * app-accessible storage with FileProvider if MediaStore insertion fails.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Result<Uri> {
        return try {
            val cleanName = fileName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").ifBlank { "student_qr" }
            val fullFileName = "MIDAR_${cleanName}_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fullFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MIDAR_QR")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return saveToFallbackFile(context, bitmap, fullFileName)

                resolver.openOutputStream(imageUri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        return Result.failure(Exception("فشل ضغط الصورة بصيغة PNG"))
                    }
                    out.flush()
                } ?: return saveToFallbackFile(context, bitmap, fullFileName)

                try {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                } catch (_: Exception) {
                    // Non-fatal if IS_PENDING update fails on some OEM ROMs
                }

                Result.success(imageUri)
            } else {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fullFileName)
                    put(MediaStore.Images.Media.TITLE, fullFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                val imageUri = try {
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                } catch (_: Exception) {
                    null
                }

                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            return Result.failure(Exception("فشل ضغط الصورة بصيغة PNG"))
                        }
                        out.flush()
                    } ?: return saveToFallbackFile(context, bitmap, fullFileName)
                    Result.success(imageUri)
                } else {
                    saveToFallbackFile(context, bitmap, fullFileName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val cleanName = fileName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").ifBlank { "student_qr" }
                val fullFileName = "MIDAR_${cleanName}_${System.currentTimeMillis()}.png"
                saveToFallbackFile(context, bitmap, fullFileName)
            } catch (ex: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveToFallbackFile(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> {
        return try {
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val midarDir = File(picturesDir, "MIDAR_QR")
            if (!midarDir.exists()) {
                midarDir.mkdirs()
            }
            val destFile = File(midarDir, fileName)
            FileOutputStream(destFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return Result.failure(Exception("فشل ضغط صورة QR بصيغة PNG"))
                }
                out.flush()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر حفظ الصورة في مسار التخزين: ${e.localizedMessage}"))
        }
    }

    /**
     * Shares a student's QR code card bitmap as a high-resolution PNG image via Android Intent Chooser.
     */
    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        title: String,
        studentName: String
    ) {
        try {
            val cacheFile = File(context.cacheDir, "shared_student_qr_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "بطاقة رمز QR الخاصة بالطالبة: $studentName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ أثناء مشاركة رمز QR: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

