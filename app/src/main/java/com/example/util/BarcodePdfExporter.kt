package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.core.model.Student
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object BarcodePdfExporter {

    // Standard A4 dimensions in points (72 points = 1 inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 20
    private const val CARDS_PER_ROW = 2
    private const val CARDS_PER_COL = 4
    private const val CARDS_PER_PAGE = CARDS_PER_ROW * CARDS_PER_COL

    suspend fun generatePdfFile(context: Context, students: List<Student>): File? {
        if (students.isEmpty()) return null
        val appContext = context.applicationContext

        return withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val totalPages = (students.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE

                for (pageIndex in 0 until totalPages) {
                    ensureActive()

                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Background
                    canvas.drawColor(Color.WHITE)

                    val startIndex = pageIndex * CARDS_PER_PAGE
                    val endIndex = minOf(startIndex + CARDS_PER_PAGE, students.size)
                    val pageStudents = students.subList(startIndex, endIndex)

                    drawPageContent(canvas, pageStudents)

                    pdfDocument.finishPage(page)
                }

                ensureActive()

                withContext(Dispatchers.IO) {
                    val outputFile = File(appContext.cacheDir, "students_barcodes.pdf")
                    FileOutputStream(outputFile).use { fos ->
                        pdfDocument.writeTo(fos)
                        fos.flush()
                    }
                    outputFile
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    pdfDocument.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun drawPageContent(canvas: Canvas, pageStudents: List<Student>) {
        val usableWidth = PAGE_WIDTH - (2 * MARGIN)
        val usableHeight = PAGE_HEIGHT - (2 * MARGIN)
        val cardWidth = usableWidth / CARDS_PER_ROW
        val cardHeight = usableHeight / CARDS_PER_COL

        val cardBorderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val namePaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val codePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (i in pageStudents.indices) {
            val student = pageStudents[i]
            val row = i / CARDS_PER_ROW
            val col = i % CARDS_PER_ROW

            val left = MARGIN + col * cardWidth + 8f
            val top = MARGIN + row * cardHeight + 8f
            val right = left + cardWidth - 16f
            val bottom = top + cardHeight - 16f

            // Card outline
            val cardRect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(cardRect, 10f, 10f, cardBorderPaint)

            // Draw Barcode 1D Code 128
            val barcodeCode = student.studentCode.ifBlank { "ST-000" }
            val barcodeBitmap = BarcodeGenerator.generateCode128Bitmap(
                content = barcodeCode,
                width = (right - left - 24f).toInt().coerceAtLeast(100),
                height = (cardHeight * 0.45f).toInt().coerceAtLeast(50)
            )

            if (barcodeBitmap != null) {
                val bcLeft = left + (cardWidth - 16f - barcodeBitmap.width) / 2f
                val bcTop = top + 16f
                canvas.drawBitmap(barcodeBitmap, bcLeft, bcTop, null)
            }

            // Student Name
            val nameY = top + (cardHeight * 0.65f)
            val centerX = left + (cardWidth - 16f) / 2f
            val nameText = if (student.fullName.length > 28) student.fullName.take(26) + ".." else student.fullName
            canvas.drawText(nameText, centerX, nameY, namePaint)

            // Student Code
            val codeY = nameY + 16f
            canvas.drawText(barcodeCode, centerX, codeY, codePaint)
        }
    }

    fun printBarcodes(context: Context, students: List<Student>) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "الطباعة غير مدعومة على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }

        val appContext = context.applicationContext
        val jobName = "Student_Barcodes_Print"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            private val adapterScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
            private var pdfFile: File? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val layoutJob = adapterScope.launch {
                    try {
                        val file = generatePdfFile(appContext, students)
                        pdfFile = file

                        if (file == null) {
                            callback?.onLayoutFailed("فشل إعداد ملف الطباعة")
                            return@launch
                        }

                        val totalPages = (students.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE
                        val info = PrintDocumentInfo.Builder("students_barcodes.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(totalPages)
                            .build()

                        callback?.onLayoutFinished(info, true)
                    } catch (e: CancellationException) {
                        callback?.onLayoutCancelled()
                    } catch (e: Exception) {
                        callback?.onLayoutFailed(e.localizedMessage ?: "حدث خطأ أثناء إعداد مستند الطباعة")
                    }
                }

                cancellationSignal?.setOnCancelListener {
                    layoutJob.cancel()
                }
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }

                if (pdfFile == null || destination == null) {
                    callback?.onWriteFailed("فشل إعداد ملف الطباعة")
                    return
                }

                val writeJob = adapterScope.launch(Dispatchers.IO) {
                    try {
                        pdfFile!!.inputStream().use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } >= 0) {
                                    if (cancellationSignal?.isCanceled == true) {
                                        withContext(Dispatchers.Main) {
                                            callback?.onWriteCancelled()
                                        }
                                        return@launch
                                    }
                                    output.write(buffer, 0, bytesRead)
                                }
                                output.flush()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        }
                    } catch (e: CancellationException) {
                        withContext(Dispatchers.Main) {
                            callback?.onWriteCancelled()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            callback?.onWriteFailed(e.localizedMessage ?: "حدث خطأ أثناء كتابة ملف الطباعة")
                        }
                    }
                }

                cancellationSignal?.setOnCancelListener {
                    writeJob.cancel()
                }
            }

            override fun onFinish() {
                super.onFinish()
                adapterScope.cancel()
            }
        }, null)
    }

    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "تصدير Barcodes الطلاب PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ أثناء فتح ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
