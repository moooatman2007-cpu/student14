package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.core.model.TeacherStats
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.StudentRepository
import com.example.ui.students.StudentBarcodesViewModel
import com.example.util.BarcodeGenerator
import com.example.util.BarcodePdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {
    @Implementation
    fun __constructor__() {}

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(canvas, pageInfo)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {}

    @Implementation
    fun writeTo(out: OutputStream) {
        out.write("%PDF-1.4 dummy valid PDF header for test".toByteArray())
    }

    @Implementation
    fun close() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowPdfDocument::class])
class BarcodePdfExportTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    private class EmptyStudentRepository : StudentRepository {
        private val _students = MutableStateFlow<List<Student>>(emptyList())
        override fun getStudents(): Flow<List<Student>> = _students.asStateFlow()
        override fun getStudentsByGrade(gradeId: String): Flow<List<Student>> = _students.asStateFlow()
        override suspend fun getStudentById(studentId: String): Student? = null
        override suspend fun getStudentByCode(studentCode: String): Student? = null
        override suspend fun addStudent(fullName: String, gradeId: String, parentPhone: String, hasWhatsApp: Boolean, alternativePhone: String?): Result<Student> =
            Result.failure(Exception("Not implemented"))
        override suspend fun updateStudent(student: Student): Result<Student> = Result.failure(Exception("Not implemented"))
        override suspend fun deleteStudent(studentId: String): Result<Unit> = Result.success(Unit)
        override fun searchStudents(query: String, gradeId: String?): Flow<List<Student>> = _students.asStateFlow()
        override fun getStats(): Flow<TeacherStats> = MutableStateFlow(TeacherStats()).asStateFlow()
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBarcodeGenerator_validCode_returnsBitmap() {
        val bitmap = BarcodeGenerator.generateCode128Bitmap(
            content = "ST-1001",
            width = 300,
            height = 100
        )
        assertNotNull(bitmap)
        assertEquals(300, bitmap?.width)
        assertEquals(100, bitmap?.height)
    }

    @Test
    fun testBarcodeGenerator_blankCode_returnsNullSafely() {
        val bitmap = BarcodeGenerator.generateCode128Bitmap("")
        assertNull(bitmap)

        val whitespaceBitmap = BarcodeGenerator.generateCode128Bitmap("   ")
        assertNull(whitespaceBitmap)
    }

    @Test
    fun testBarcodePdfExporter_emptyList_returnsNull() = runTest {
        val result = BarcodePdfExporter.generatePdfFile(context, emptyList())
        assertNull(result)
    }

    @Test
    fun testBarcodePdfExporter_students_generatesValidPdfFile() = runTest {
        val testStudents = (1..10).map { i ->
            Student(
                studentId = "std_$i",
                teacherId = "t1",
                gradeId = "g1",
                fullName = "طالب اختبار رقم $i",
                studentCode = "ST-$i"
            )
        }

        val pdfFile = BarcodePdfExporter.generatePdfFile(context, testStudents)
        assertNotNull(pdfFile)
        assertTrue(pdfFile is File)
        assertTrue(pdfFile!!.exists())
        assertTrue(pdfFile.length() > 0)
    }

    @Test
    fun testBarcodePdfExporter_largeBatch_100Students_succeeds() = runTest {
        val testStudents = (1..100).map { i ->
            Student(
                studentId = "batch_std_$i",
                teacherId = "t1",
                gradeId = "g1",
                fullName = "طالب الدفعة رقم $i",
                studentCode = String.format("BTC%04d", i)
            )
        }

        val pdfFile = BarcodePdfExporter.generatePdfFile(context, testStudents)
        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.exists())
        assertTrue(pdfFile.length() > 0)
    }

    @Test
    fun testBarcodePdfExporter_cancellation_abortsCleanly() = runTest {
        val testStudents = (1..200).map { i ->
            Student(
                studentId = "std_cancel_$i",
                teacherId = "t1",
                gradeId = "g1",
                fullName = "طالب إلغاء $i",
                studentCode = "CNC-$i"
            )
        }

        val deferred = async(Dispatchers.Default) {
            BarcodePdfExporter.generatePdfFile(context, testStudents)
        }
        deferred.cancelAndJoin()
        assertTrue(deferred.isCancelled)
    }

    @Test
    fun testStudentBarcodesViewModel_downloadPdf_updatesExportingState() = runTest {
        val gradeRepo = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        val studentRepo = MockStudentRepository(gradeRepository = gradeRepo)

        val viewModel = StudentBarcodesViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExporting)
        assertTrue(viewModel.uiState.value.filteredStudents.isNotEmpty())

        // Trigger download
        viewModel.downloadPdf(context)

        // Wait for background worker and test dispatcher to complete
        for (i in 1..30) {
            advanceUntilIdle()
            if (!viewModel.uiState.value.isExporting) break
            Thread.sleep(50)
        }

        // Should return back to false after completion
        assertFalse(viewModel.uiState.value.isExporting)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testStudentBarcodesViewModel_downloadPdf_emptyList_doesNotExport() = runTest {
        val gradeRepo = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        val emptyStudentRepo = EmptyStudentRepository()

        val viewModel = StudentBarcodesViewModel(
            studentRepository = emptyStudentRepo,
            gradeRepository = gradeRepo
        )
        advanceUntilIdle()

        viewModel.downloadPdf(context)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExporting)
    }
}
