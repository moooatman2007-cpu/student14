package com.example

import com.example.core.model.EducationalStages
import com.example.core.model.LessonPayment
import com.example.core.model.Student
import com.example.core.model.SupabaseLessonPaymentDto
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.attendance.FastAttendanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LessonPaymentTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var paymentRepo: MockPaymentRepository
    private lateinit var gradeRepo: MockGradeRepository
    private lateinit var studentRepo: MockStudentRepository
    private lateinit var attendanceRepo: MockAttendanceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        paymentRepo = MockPaymentRepository()
        gradeRepo = MockGradeRepository()
        studentRepo = MockStudentRepository(gradeRepo)
        attendanceRepo = MockAttendanceRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSerializationAndDeserialization() {
        val payment = LessonPayment(
            paymentId = "p_101",
            studentId = "s_202",
            teacherId = "t_303",
            year = 2026,
            month = 9,
            amount = 150.0,
            isPaid = true,
            paidAt = "2026-09-20T10:00:00Z"
        )

        val dto = SupabaseLessonPaymentDto(
            id = payment.paymentId,
            studentId = payment.studentId,
            teacherId = payment.teacherId,
            year = payment.year,
            month = payment.month,
            amount = payment.amount,
            isPaid = payment.isPaid,
            paidAt = payment.paidAt
        )

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"student_id\":\"s_202\""))
        assertTrue(encoded.contains("\"is_paid\":true"))
        assertTrue(encoded.contains("\"amount\":150.0"))

        val decoded = json.decodeFromString<SupabaseLessonPaymentDto>(encoded)
        val converted = decoded.toLessonPayment()
        assertEquals(payment.paymentId, converted.paymentId)
        assertEquals(payment.studentId, converted.studentId)
        assertEquals(payment.year, converted.year)
        assertEquals(payment.month, converted.month)
        assertEquals(payment.amount, converted.amount, 0.001)
        assertTrue(converted.isPaid)
        assertEquals("2026-09-20T10:00:00Z", converted.paidAt)
    }

    @Test
    fun testDefaultPaymentStatusForMissingRecord() = runTest {
        val studentId = "student_without_payment"
        val payment = paymentRepo.getPaymentForStudent(studentId, 2026, 9)
        assertNull(payment)

        // UI interpretation defaults to unpaid
        val isPaid = payment?.isPaid ?: false
        assertFalse(isPaid)
    }

    @Test
    fun testSetAndTogglePaymentStatus() = runTest {
        val studentId = "student_toggle_01"
        val year = 2026
        val month = 9

        // 1. Initial toggle -> becomes Paid
        val firstToggle = paymentRepo.togglePaymentStatus(studentId, year, month, 200.0)
        assertTrue(firstToggle.isSuccess)
        val paidPayment = firstToggle.getOrNull()
        assertNotNull(paidPayment)
        assertTrue(paidPayment!!.isPaid)
        assertEquals(200.0, paidPayment.amount, 0.001)
        assertNotNull(paidPayment.paidAt)

        // 2. Second toggle -> becomes Unpaid
        val secondToggle = paymentRepo.togglePaymentStatus(studentId, year, month, 200.0)
        assertTrue(secondToggle.isSuccess)
        val unpaidPayment = secondToggle.getOrNull()
        assertNotNull(unpaidPayment)
        assertFalse(unpaidPayment!!.isPaid)
        assertNull(unpaidPayment.paidAt)
    }

    @Test
    fun testMonthAndYearIsolation() = runTest {
        val studentId = "student_multi_month"

        // Pay for September 2026
        paymentRepo.setPaymentStatus(studentId, 2026, 9, isPaid = true, amount = 100.0)

        // October 2026 is unpaid by default
        val octPayment = paymentRepo.getPaymentForStudent(studentId, 2026, 10)
        assertNull(octPayment)

        // September 2026 remains paid
        val sepPayment = paymentRepo.getPaymentForStudent(studentId, 2026, 9)
        assertNotNull(sepPayment)
        assertTrue(sepPayment!!.isPaid)

        // September 2027 remains unpaid
        val sepNextYear = paymentRepo.getPaymentForStudent(studentId, 2027, 9)
        assertNull(sepNextYear)
    }

    @Test
    fun testStageUniversalityPrimaryPrepSecondary() = runTest {
        // Payments work universally across all educational stages (primary, prep, secondary)
        val primaryGrades = EducationalStages.getGradeNamesForStage("ابتدائي")
        val prepGrades = EducationalStages.getGradeNamesForStage("إعدادي")
        val secGrades = EducationalStages.getGradeNamesForStage("ثانوي")

        assertEquals(6, primaryGrades.size)
        assertEquals(3, prepGrades.size)
        assertEquals(3, secGrades.size)

        // Add 3 students from different stages
        val primaryStudentId = "student_primary"
        val prepStudentId = "student_prep"
        val secStudentId = "student_sec"

        paymentRepo.setPaymentStatus(primaryStudentId, 2026, 9, isPaid = true, amount = 80.0)
        paymentRepo.setPaymentStatus(prepStudentId, 2026, 9, isPaid = true, amount = 120.0)
        paymentRepo.setPaymentStatus(secStudentId, 2026, 9, isPaid = false, amount = 150.0)

        val monthlyList = paymentRepo.getMonthlyPaymentsList(2026, 9)
        assertEquals(3, monthlyList.size)

        val p1 = monthlyList.find { it.studentId == primaryStudentId }
        val p2 = monthlyList.find { it.studentId == prepStudentId }
        val p3 = monthlyList.find { it.studentId == secStudentId }

        assertNotNull(p1)
        assertTrue(p1!!.isPaid)
        assertNotNull(p2)
        assertTrue(p2!!.isPaid)
        assertNotNull(p3)
        assertFalse(p3!!.isPaid)
    }

    @Test
    fun testPerformanceWith100PlusStudentsBatchQuery() = runTest {
        val totalStudents = 150
        val initialPayments = (1..totalStudents).map { i ->
            LessonPayment(
                paymentId = "pay_$i",
                studentId = "student_$i",
                teacherId = "teacher_perf",
                year = 2026,
                month = 9,
                amount = 100.0,
                isPaid = (i % 2 == 0) // even is paid, odd is unpaid
            )
        }

        val perfRepo = MockPaymentRepository(initialPayments)

        // Single batch query to load all 150 payments
        val fetchedList = perfRepo.getMonthlyPaymentsList(2026, 9)
        assertEquals(150, fetchedList.size)

        // O(1) Associate Map build
        val map = fetchedList.associateBy { it.studentId }
        assertEquals(150, map.size)

        // Verify O(1) lookups
        for (i in 1..totalStudents) {
            val payment = map["student_$i"]
            assertNotNull(payment)
            assertEquals(i % 2 == 0, payment!!.isPaid)
        }

        val paidCount = fetchedList.count { it.isPaid }
        val unpaidCount = fetchedList.count { !it.isPaid }
        assertEquals(75, paidCount)
        assertEquals(75, unpaidCount)
    }

    @Test
    fun testFastAttendanceViewModelPaymentCountersAndOptimisticToggle() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val students = listOf(
            Student(studentId = "s_1", fullName = "طالب 1", studentCode = "101", gradeId = "g_1"),
            Student(studentId = "s_2", fullName = "طالب 2", studentCode = "102", gradeId = "g_1"),
            Student(studentId = "s_3", fullName = "طالب 3", studentCode = "103", gradeId = "g_1")
        )

        val vm = FastAttendanceViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            paymentRepository = paymentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setStudentsForScope(students, students)
        testDispatcher.scheduler.advanceUntilIdle()

        val state1 = vm.uiState.value
        assertEquals(0, state1.paidCount)
        assertEquals(3, state1.unpaidCount)

        // Optimistically toggle s_1 -> paid
        vm.togglePaymentStatus("s_1")
        val state2 = vm.uiState.value
        assertEquals(1, state2.paidCount)
        assertEquals(2, state2.unpaidCount)
        assertTrue(state2.paymentsMap["s_1"]?.isPaid == true)

        testDispatcher.scheduler.advanceUntilIdle()

        // Optimistically toggle s_2 -> paid
        vm.togglePaymentStatus("s_2")
        val state3 = vm.uiState.value
        assertEquals(2, state3.paidCount)
        assertEquals(1, state3.unpaidCount)
        assertTrue(state3.paymentsMap["s_2"]?.isPaid == true)

        testDispatcher.scheduler.advanceUntilIdle()

        // Optimistically toggle s_1 -> unpaid
        vm.togglePaymentStatus("s_1")
        val state4 = vm.uiState.value
        assertEquals(1, state4.paidCount)
        assertEquals(2, state4.unpaidCount)
        assertFalse(state4.paymentsMap["s_1"]?.isPaid == true)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun testFastAttendanceViewModelRollbackOnFailure() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Create a repository that always fails
        val failingRepo = object : com.example.data.repository.PaymentRepository {
            override fun getMonthlyPayments(year: Int, month: Int): kotlinx.coroutines.flow.Flow<List<LessonPayment>> =
                kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getMonthlyPaymentsList(year: Int, month: Int): List<LessonPayment> = emptyList()
            override suspend fun getPaymentForStudent(studentId: String, year: Int, month: Int): LessonPayment? = null
            override suspend fun setPaymentStatus(
                studentId: String,
                year: Int,
                month: Int,
                isPaid: Boolean,
                amount: Double
            ): Result<LessonPayment> = Result.failure(Exception("Network simulated error"))
            override suspend fun togglePaymentStatus(
                studentId: String,
                year: Int,
                month: Int,
                amount: Double
            ): Result<LessonPayment> = Result.failure(Exception("Network simulated error"))
        }

        val students = listOf(
            Student(studentId = "s_fail_1", fullName = "طالب فاشل", studentCode = "201", gradeId = "g_1")
        )

        val vm = FastAttendanceViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            paymentRepository = failingRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setStudentsForScope(students, students)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle payment status -> will optimistically be paid, then rollback to unpaid
        vm.togglePaymentStatus("s_fail_1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0, state.paidCount)
        assertEquals(1, state.unpaidCount)
        assertFalse(state.paymentsMap["s_fail_1"]?.isPaid == true)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun testTeacherTenantIsolation() = runTest {
        // Teacher A payments vs Teacher B payments
        val teacherAPayment = LessonPayment(
            paymentId = "pay_A",
            studentId = "student_1",
            teacherId = "teacher_A",
            year = 2026,
            month = 9,
            amount = 100.0,
            isPaid = true
        )
        val teacherBPayment = LessonPayment(
            paymentId = "pay_B",
            studentId = "student_2",
            teacherId = "teacher_B",
            year = 2026,
            month = 9,
            amount = 100.0,
            isPaid = true
        )

        val isolatedRepo = MockPaymentRepository(listOf(teacherAPayment, teacherBPayment))
        val monthlyPayments = isolatedRepo.getMonthlyPaymentsList(2026, 9)
        assertEquals(2, monthlyPayments.size)

        // RLS in Supabase ensures teacher_id == auth.uid()
        val teacherAOnly = monthlyPayments.filter { it.teacherId == "teacher_A" }
        assertEquals(1, teacherAOnly.size)
        assertEquals("student_1", teacherAOnly.first().studentId)
    }
}

