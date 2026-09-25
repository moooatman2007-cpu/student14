package com.example

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.LessonPayment
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockPaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AttendanceAndPaymentConcurrencyTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================================
    // 1. ATTENDANCE TESTS
    // ============================================================================

    @Test
    fun testAttendance_newRecord_createsSuccessfully() = runTest {
        val repo = MockAttendanceRepository()
        val studentId = "student_test_1"
        val date = "2026-09-22"

        val result = repo.recordOrUpdateAttendance(
            studentId = studentId,
            date = date,
            status = AttendanceStatus.PRESENT,
            note = "حاضر في الموعد"
        )

        assertTrue(result.isSuccess)
        val attendance = result.getOrNull()
        assertNotNull(attendance)
        assertEquals(studentId, attendance?.studentId)
        assertEquals(date, attendance?.date)
        assertEquals(AttendanceStatus.PRESENT, attendance?.status)
        assertEquals("حاضر في الموعد", attendance?.note)

        val list = repo.getAttendanceForStudent(studentId).first()
        assertEquals(1, list.size)
        assertEquals(AttendanceStatus.PRESENT, list[0].status)
    }

    @Test
    fun testAttendance_existingRecord_updatesAtomicallyWithoutDuplicates() = runTest {
        val repo = MockAttendanceRepository()
        val studentId = "student_test_2"
        val date = "2026-09-22"

        // First write: PRESENT
        val write1 = repo.recordOrUpdateAttendance(
            studentId = studentId,
            date = date,
            status = AttendanceStatus.PRESENT,
            note = "أول تسجيل"
        )
        assertTrue(write1.isSuccess)

        // Second write (Update): ABSENT with new note
        val write2 = repo.recordOrUpdateAttendance(
            studentId = studentId,
            date = date,
            status = AttendanceStatus.ABSENT,
            note = "تعديل: غائب بعذر طبي"
        )
        assertTrue(write2.isSuccess)

        // Verify that only 1 record exists on this date and its state is ABSENT
        val list = repo.getAttendanceForStudent(studentId).first()
        assertEquals(1, list.size)
        assertEquals(AttendanceStatus.ABSENT, list[0].status)
        assertEquals("تعديل: غائب بعذر طبي", list[0].note)
    }

    @Test
    fun testAttendance_concurrentWrites_sameStudentAndDate_noDuplicateRecords() = runTest {
        val repo = MockAttendanceRepository()
        val studentId = "student_test_concurrent"
        val date = "2026-09-22"

        // Fire 20 concurrent coroutines attempting to write attendance on the exact same (student_id, date)
        val jobs = (1..20).map { index ->
            async(Dispatchers.Default) {
                val status = if (index % 2 == 0) AttendanceStatus.PRESENT else AttendanceStatus.LATE
                repo.recordOrUpdateAttendance(
                    studentId = studentId,
                    date = date,
                    status = status,
                    note = "Write attempt $index"
                )
            }
        }

        val results = jobs.awaitAll()
        assertTrue(results.all { it.isSuccess })

        // Check storage: exactly ONE record must exist for this date
        val list = repo.getAttendanceForStudent(studentId).first()
        assertEquals(1, list.size)
        assertEquals(date, list[0].date)
    }

    // ============================================================================
    // 2. PAYMENT TESTS
    // ============================================================================

    @Test
    fun testPayment_newRecord_createsSuccessfully() = runTest {
        val repo = MockPaymentRepository()
        val studentId = "student_pay_1"
        val year = 2026
        val month = 9

        val result = repo.setPaymentStatus(
            studentId = studentId,
            year = year,
            month = month,
            isPaid = true,
            amount = 150.0
        )

        assertTrue(result.isSuccess)
        val payment = result.getOrNull()
        assertNotNull(payment)
        assertEquals(studentId, payment?.studentId)
        assertEquals(year, payment?.year)
        assertEquals(month, payment?.month)
        assertTrue(payment?.isPaid == true)
        assertEquals(150.0, payment?.amount ?: 0.0, 0.01)

        val paymentsList = repo.getMonthlyPayments(year, month).first()
        assertEquals(1, paymentsList.size)
        assertEquals(studentId, paymentsList[0].studentId)
        assertTrue(paymentsList[0].isPaid)
    }

    @Test
    fun testPayment_existingRecord_updatesAtomicallyWithoutDuplicates() = runTest {
        val repo = MockPaymentRepository()
        val studentId = "student_pay_2"
        val year = 2026
        val month = 9

        // 1. Initial write: UNPAID
        val write1 = repo.setPaymentStatus(
            studentId = studentId,
            year = year,
            month = month,
            isPaid = false,
            amount = 150.0
        )
        assertTrue(write1.isSuccess)

        // 2. Update to PAID
        val write2 = repo.setPaymentStatus(
            studentId = studentId,
            year = year,
            month = month,
            isPaid = true,
            amount = 200.0
        )
        assertTrue(write2.isSuccess)

        // Verify only 1 payment row exists for this student & period
        val payments = repo.getMonthlyPayments(year, month).first()
        assertEquals(1, payments.size)
        assertTrue(payments[0].isPaid)
        assertEquals(200.0, payments[0].amount, 0.01)
    }

    @Test
    fun testPayment_concurrentWrites_sameStudentPeriod_noDuplicates() = runTest {
        val repo = MockPaymentRepository()
        val studentId = "student_pay_concurrent"
        val year = 2026
        val month = 9

        // 20 concurrent coroutines attempting to write payment status
        val jobs = (1..20).map { i ->
            async(Dispatchers.Default) {
                repo.setPaymentStatus(
                    studentId = studentId,
                    year = year,
                    month = month,
                    isPaid = (i % 2 == 0),
                    amount = 100.0 + i
                )
            }
        }

        val results = jobs.awaitAll()
        assertTrue(results.all { it.isSuccess })

        // Check storage: exactly 1 payment record exists for (student_id, year, month)
        val payments = repo.getMonthlyPayments(year, month).first()
        assertEquals(1, payments.size)
        assertEquals(studentId, payments[0].studentId)
    }

    @Test
    fun testPayment_toggleStatus_isIdempotentAndSafe() = runTest {
        val repo = MockPaymentRepository()
        val studentId = "student_toggle_1"
        val year = 2026
        val month = 9

        // Initial toggle: from null (treated as false) to true
        val res1 = repo.togglePaymentStatus(studentId, year, month, 150.0)
        assertTrue(res1.isSuccess)
        assertTrue(res1.getOrNull()?.isPaid == true)

        // Second toggle: from true to false
        val res2 = repo.togglePaymentStatus(studentId, year, month, 150.0)
        assertTrue(res2.isSuccess)
        assertFalse(res2.getOrNull()?.isPaid == true)

        // Verify single record
        val list = repo.getMonthlyPayments(year, month).first()
        assertEquals(1, list.size)
        assertFalse(list[0].isPaid)
    }

    // ============================================================================
    // 3. FAILURE HANDLING & RETRY
    // ============================================================================

    @Test
    fun testFailureHandling_and_retrySafety() = runTest {
        val attempts = AtomicInteger(0)
        val baseRepo = MockAttendanceRepository()
        val failingRepo = object : AttendanceRepository by baseRepo {
            override suspend fun recordOrUpdateAttendance(
                studentId: String,
                date: String,
                status: AttendanceStatus,
                note: String?
            ): Result<Attendance> {
                if (attempts.incrementAndGet() == 1) {
                    return Result.failure(Exception("Network timeout simulation"))
                }
                return baseRepo.recordOrUpdateAttendance(studentId, date, status, note)
            }
        }

        val studentId = "std_retry_1"
        val date = "2026-09-22"

        // First attempt fails
        val firstTry = failingRepo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.PRESENT, null)
        assertTrue(firstTry.isFailure)

        // Retry succeeds
        val secondTry = failingRepo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.PRESENT, null)
        assertTrue(secondTry.isSuccess)
        assertEquals(AttendanceStatus.PRESENT, secondTry.getOrNull()?.status)

        // Verify single record
        val list = failingRepo.getAttendanceForStudent(studentId).first()
        assertEquals(1, list.size)
    }
}
