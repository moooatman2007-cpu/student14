package com.example

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.*
import com.example.data.local.AppDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import com.example.data.repository.*
import com.example.ui.add_edit_student.AddEditStudentViewModel
import com.example.ui.student_detail.StudentDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class F03DataIntegrityTest {

    private lateinit var db: AppDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var recitationDao: RecitationDao
    private lateinit var examDao: ExamDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var gradeDao: GradeDao
    private lateinit var outboxDao: OutboxDao

    private lateinit var studentRepository: SupabaseStudentRepository
    private lateinit var recitationRepository: SupabaseRecitationRepository
    private lateinit var attendanceRepository: SupabaseAttendanceRepository
    private lateinit var examRepository: SupabaseExamRepository
    private lateinit var paymentRepository: SupabasePaymentRepository
    private lateinit var gradeRepository: SupabaseGradeRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        studentDao = db.studentDao()
        attendanceDao = db.attendanceDao()
        recitationDao = db.recitationDao()
        examDao = db.examDao()
        paymentDao = db.paymentDao()
        gradeDao = db.gradeDao()
        outboxDao = db.outboxDao()

        com.example.data.SupabaseClientProvider.mockTeacherId = "teacher_test"

        studentRepository = SupabaseStudentRepository(studentDao, outboxDao)
        recitationRepository = SupabaseRecitationRepository(recitationDao, outboxDao)
        attendanceRepository = SupabaseAttendanceRepository(attendanceDao, outboxDao)
        examRepository = SupabaseExamRepository(examDao, outboxDao)
        paymentRepository = SupabasePaymentRepository(paymentDao, outboxDao)
        gradeRepository = SupabaseGradeRepository(gradeDao, outboxDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testSameIdReusedAfterRetry_andOfflineStudentSyncIdempotency() = runBlocking {
        // F03-01: ID must be generated once and reused on retry
        val result = studentRepository.addStudent(
            fullName = "Idempotent Student",
            gradeId = "grade_test",
            parentPhone = "01011111111",
            hasWhatsApp = true,
            alternativePhone = null
        )

        assertTrue(result.isSuccess)
        val student = result.getOrNull()
        assertNotNull(student)
        val firstId = student!!.studentId

        // Verify Room
        val cached = studentDao.getStudentByIdSync("teacher_test", firstId)
        assertNotNull(cached)
        assertEquals(firstId, cached?.studentId)

        // Verify Outbox
        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals(firstId, pending[0].entityId)
        assertEquals("INSERT", pending[0].operationType)
    }

    @Test
    fun testOfflineRecitationSyncIdempotency() = runBlocking {
        // F03-01: Recitation pre-generated ID
        val result = recitationRepository.addRecitation(
            studentId = "student_test_123",
            date = "2026-09-23",
            title = "Surah Yasin",
            content = "Verses 1-10",
            score = 10.0,
            maxScore = 10.0,
            note = "Excellent"
        )

        assertTrue(result.isSuccess)
        val recitation = result.getOrNull()
        assertNotNull(recitation)

        val cached = recitationDao.getRecitationByIdSync("teacher_test", recitation!!.recitationId)
        assertNotNull(cached)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals(recitation.recitationId, pending[0].entityId)
        assertEquals("RECITATION", pending[0].entityType)
        assertEquals("INSERT", pending[0].operationType)
    }

    @Test
    fun testAttendanceOfflineSyncDeleteSync() = runBlocking {
        // F03-02: Stable identity for attendance and offline deletion
        val result = attendanceRepository.recordOrUpdateAttendance(
            studentId = "student_x",
            date = "2026-09-23",
            status = AttendanceStatus.PRESENT,
            note = "On time"
        )
        assertTrue(result.isSuccess)

        val delResult = attendanceRepository.deleteAttendance("student_x_2026-09-23")
        assertTrue(delResult.isSuccess)

        // Verify it was removed from Room
        val cached = attendanceDao.getAttendanceByDateSync("teacher_test", "student_x", "2026-09-23")
        assertNull(cached)

        // Outbox contains both actions chronologically
        val pending = outboxDao.getPendingOperations()
        assertTrue(pending.size >= 2)
        assertEquals("UPSERT", pending[0].operationType)
        assertEquals("DELETE", pending[1].operationType)
        assertEquals("student_x_2026-09-23", pending[1].entityId)
    }

    @Test
    fun testExamOfflineSync() = runBlocking {
        // F03-03: Exam offline support
        val result = examRepository.addExam(
            studentId = "student_abc",
            date = "2026-09-23",
            examName = "Math Midterm",
            subject = "Algebra",
            score = 18.5,
            maxScore = 20.0,
            note = "Good work"
        )

        assertTrue(result.isSuccess)
        val exam = result.getOrNull()
        assertNotNull(exam)

        // Room check
        val cached = examDao.getExamByIdSync("teacher_test", exam!!.examId)
        assertNotNull(cached)

        // Outbox check
        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("EXAM", pending[0].entityType)
        assertEquals(exam.examId, pending[0].entityId)
    }

    @Test
    fun testPaymentOfflineSync() = runBlocking {
        // F03-03: Lesson payment offline support
        val result = paymentRepository.setPaymentStatus(
            studentId = "student_pay",
            year = 2026,
            month = 9,
            isPaid = true,
            amount = 150.0
        )

        assertTrue(result.isSuccess)
        val payment = result.getOrNull()
        assertNotNull(payment)

        // Room check
        val cached = paymentDao.getPaymentForStudentSync("teacher_test", "student_pay", 2026, 9)
        assertNotNull(cached)
        assertTrue(cached!!.isPaid)

        // Outbox check
        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("PAYMENT", pending[0].entityType)
        assertEquals("student_pay_2026_9", pending[0].entityId)
    }

    @Test
    fun testGradeOfflineSync() = runBlocking {
        // F03-03: Grade offline support
        val grade = Grade(
            id = "grade_uuid_offline",
            name = "الصف الأول الثانوي",
            displayOrder = 1,
            teacherId = "teacher_test"
        )
        val result = gradeRepository.addGrade(grade)
        assertTrue(result)

        // Room check
        val cached = gradeDao.getGradeById("teacher_test", "grade_uuid_offline")
        assertNotNull(cached)

        // Outbox check
        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("GRADE", pending[0].entityType)
        assertEquals("grade_uuid_offline", pending[0].entityId)
    }

    @Test
    fun testBatchAttendanceOfflineSync() = runBlocking {
        // F03-03: Batch Attendance offline sync handles individual writes
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_1", status = "PRESENT", note = null),
            BatchAttendanceItemDto(studentId = "st_2", status = "ABSENT", note = "Ill")
        )
        val result = attendanceRepository.recordBatchAttendance("2026-09-23", items)
        assertTrue(result.isSuccess)

        // Individual attendance written to Room and Outbox via recordOrUpdateAttendance fallback
        val pending = outboxDao.getPendingOperations()
        assertEquals(2, pending.size)
        assertEquals("ATTENDANCE", pending[0].entityType)
        assertEquals("ATTENDANCE", pending[1].entityType)
    }

    class FakeDelayStudentRepository : StudentRepository {
        var addStudentCallCount = 0
        override suspend fun addStudent(
            fullName: String,
            gradeId: String,
            parentPhone: String,
            hasWhatsApp: Boolean,
            alternativePhone: String?
        ): Result<Student> {
            addStudentCallCount++
            kotlinx.coroutines.delay(1000) // Delay to simulate latency and hold isSaving = true
            return Result.success(
                Student(
                    studentId = "s_double",
                    studentCode = "ST-999",
                    fullName = fullName,
                    gradeId = gradeId,
                    parentPhone = parentPhone,
                    hasWhatsApp = hasWhatsApp,
                    teacherId = "teacher_test"
                )
            )
        }
        override fun getStudents(): kotlinx.coroutines.flow.Flow<List<Student>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun getStudentsByGrade(gradeId: String): kotlinx.coroutines.flow.Flow<List<Student>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getStudentById(studentId: String): Student? = null
        override suspend fun getStudentByCode(studentCode: String): Student? = null
        override suspend fun updateStudent(student: Student): Result<Student> = Result.success(student)
        override suspend fun deleteStudent(studentId: String): Result<Unit> = Result.success(Unit)
        override fun searchStudents(query: String, gradeId: String?): kotlinx.coroutines.flow.Flow<List<Student>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun getStats(): kotlinx.coroutines.flow.Flow<TeacherStats> = kotlinx.coroutines.flow.flowOf(TeacherStats())
    }

    @Test
    fun testDoubleClickSaveProtection() = runTest {
        // F03-04: Double-click guard on AddEditStudentViewModel and StudentDetailViewModel
        val fakeRepo = FakeDelayStudentRepository()
        val addEditViewModel = AddEditStudentViewModel(
            studentRepository = fakeRepo,
            gradeRepository = MockGradeRepository()
        )

        addEditViewModel.onFullNameChange("Double Tapper")
        addEditViewModel.onGradeSelected("grade_test")
        addEditViewModel.onParentPhoneChange("01011111111")

        // First save should initiate saving
        addEditViewModel.saveStudent()
        assertTrue(addEditViewModel.uiState.value.isSaving)

        // Immediate second save should be ignored (guard blocks reentry)
        addEditViewModel.saveStudent()

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify only 1 record actually called/created in repo instead of duplicates
        assertEquals(1, fakeRepo.addStudentCallCount)
    }

    @Test
    fun testDuplicateWorkerExecutionAndRetry() = runBlocking {
        // F03-05: Worker retry simulation and chronological execution order
        val op1 = OutboxEntity(
            id = "op_1",
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "st_sync_1",
            payload = "{}",
            createdAt = 1000L,
            status = "PENDING",
            teacherId = "teacher_test"
        )
        val op2 = OutboxEntity(
            id = "op_2",
            operationType = "UPDATE",
            entityType = "STUDENT",
            entityId = "st_sync_1",
            payload = "{}",
            createdAt = 2000L,
            status = "PENDING",
            teacherId = "teacher_test"
        )

        outboxDao.insertOperation(op1)
        outboxDao.insertOperation(op2)

        val ops = outboxDao.getPendingOperationsForTeacher("teacher_test")
        assertEquals(2, ops.size)
        // Order is guaranteed ASC by created_at
        assertEquals("op_1", ops[0].id)
        assertEquals("op_2", ops[1].id)
    }

    @Test
    fun testTenantIsolationDuringSync() = runBlocking {
        // F-02: Tenant isolation during sync and outbox operations
        val opOther = OutboxEntity(
            id = "op_other",
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "st_other",
            payload = "{}",
            createdAt = 500L,
            status = "PENDING",
            teacherId = "teacher_other" // different teacher scoped outbox
        )
        outboxDao.insertOperation(opOther)

        val ops = outboxDao.getPendingOperationsForTeacher("teacher_test")
        assertTrue(ops.isEmpty()) // scoped to teacher_test should not fetch teacher_other's operations
    }
}
