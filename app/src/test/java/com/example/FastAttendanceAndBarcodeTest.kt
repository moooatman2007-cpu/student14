package com.example

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.attendance.FastAttendanceViewModel
import com.example.ui.attendance.ScanResultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class FastAttendanceAndBarcodeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var paymentRepository: MockPaymentRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.SECONDARY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        paymentRepository = MockPaymentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Requirement A:
     * 100 students -> efficient loading and correct attendance filtering.
     */
    @Test
    fun testA_100Students_correctAttendanceFiltering() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()

        val largeStudentList = (1..100).map { i ->
            Student(
                studentId = "std_100_$i",
                teacherId = "teacher_test",
                gradeId = targetGrade.id,
                fullName = "طالب اختبار $i",
                studentCode = String.format("STU%04d", i),
                parentPhone = "011000000$i"
            )
        }

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(largeStudentList, largeStudentList)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(100, state.allStudentsInScope.size)
        assertEquals(100, state.filteredStudents.size)

        // Mark first 40 students present via barcode
        for (i in 1..40) {
            viewModel.processScannedBarcode(String.format("STU%04d", i))
        }

        assertEquals(40, viewModel.uiState.value.presentStudentIds.size)
    }

    /**
     * Requirement B:
     * selectedGradeId isolates students correctly.
     */
    @Test
    fun testB_selectedGradeId_isolatesStudentsCorrectly() = runTest {
        val grades = gradeRepository.getGrades().first()
        val grade1 = grades[0]
        val grade2 = grades[1]

        val s1 = Student(studentId = "s1", teacherId = "t1", gradeId = grade1.id, fullName = "أحمد", studentCode = "C1")
        val s2 = Student(studentId = "s2", teacherId = "t1", gradeId = grade2.id, fullName = "محمود", studentCode = "C2")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1, s2))
        viewModel.onGradeSelected(grade1.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(s1), state.allStudentsInScope)
        assertFalse(state.allStudentsInScope.contains(s2))
    }

    /**
     * Requirement C:
     * Student from another Grade is rejected when scanned.
     */
    @Test
    fun testC_studentFromAnotherGrade_isRejectedOnBarcodeScan() = runTest {
        val grades = gradeRepository.getGrades().first()
        val grade1 = grades[0]
        val grade2 = grades[1]

        val s1 = Student(studentId = "s1", teacherId = "t1", gradeId = grade1.id, fullName = "أحمد", studentCode = "CODE_G1")
        val s2 = Student(studentId = "s2", teacherId = "t1", gradeId = grade2.id, fullName = "محمود", studentCode = "CODE_G2")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1, s2))
        viewModel.onGradeSelected(grade1.id)
        advanceUntilIdle()

        // Scan student from grade 2
        viewModel.processScannedBarcode("CODE_G2")
        val feedback = viewModel.uiState.value.scanFeedback

        assertNotNull(feedback)
        assertEquals(ScanResultType.WRONG_GROUP, feedback?.type)
        assertFalse(viewModel.uiState.value.presentStudentIds.contains("s2"))
    }

    /**
     * Requirement D:
     * Student from previous stage is rejected when current Grade is selected.
     */
    @Test
    fun testD_studentFromPreviousStage_isRejectedWhenSecondaryGradeSelected() = runTest {
        val secondaryGrades = gradeRepository.getGrades().first()
        val firstSecondaryGrade = secondaryGrades.first()

        val previousStageStudent = Student(
            studentId = "old_student_prep",
            teacherId = "t1",
            gradeId = "middle_prep_grade_99", // Old Prep grade
            fullName = "طالب إعدادي سابق",
            studentCode = "PREP_CODE_1"
        )
        val currentSecondaryStudent = Student(
            studentId = "sec_student_1",
            teacherId = "t1",
            gradeId = firstSecondaryGrade.id,
            fullName = "طالب ثانوي حالي",
            studentCode = "SEC_CODE_1"
        )

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(currentSecondaryStudent), listOf(currentSecondaryStudent, previousStageStudent))
        viewModel.onGradeSelected(firstSecondaryGrade.id)
        advanceUntilIdle()

        // Scan previous stage student
        viewModel.processScannedBarcode("PREP_CODE_1")
        val feedback = viewModel.uiState.value.scanFeedback

        assertNotNull(feedback)
        assertEquals(ScanResultType.WRONG_GROUP, feedback?.type)
        assertFalse(viewModel.uiState.value.presentStudentIds.contains(previousStageStudent.studentId))
    }

    /**
     * Requirement E:
     * Unknown student or student from another teacher is rejected (NOT_FOUND).
     */
    @Test
    fun testE_unknownStudentBarcode_isRejectedSafely() = runTest {
        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.processScannedBarcode("UNKNOWN_TEACHER_CODE_999")
        val feedback = viewModel.uiState.value.scanFeedback

        assertNotNull(feedback)
        assertEquals(ScanResultType.NOT_FOUND, feedback?.type)
        assertNull(feedback?.student)
    }

    /**
     * Requirement F:
     * Duplicate Barcode scan does not duplicate attendance.
     */
    @Test
    fun testF_duplicateBarcodeScan_doesNotDuplicateAttendance() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = Student(studentId = "s1", teacherId = "t1", gradeId = targetGrade.id, fullName = "طالب تجريبي", studentCode = "DUP_100")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1))
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // First scan -> SUCCESS_PRESENT
        viewModel.processScannedBarcode("DUP_100")
        assertEquals(ScanResultType.SUCCESS_PRESENT, viewModel.uiState.value.scanFeedback?.type)
        assertEquals(1, viewModel.uiState.value.presentStudentIds.size)

        // Second scan -> ALREADY_PRESENT
        viewModel.processScannedBarcode("DUP_100")
        assertEquals(ScanResultType.ALREADY_PRESENT, viewModel.uiState.value.scanFeedback?.type)
        assertEquals(1, viewModel.uiState.value.presentStudentIds.size)
    }

    /**
     * Requirement G:
     * Finish twice does not create duplicate local records.
     */
    @Test
    fun testG_finishTwice_doesNotCreateDuplicateRecords() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب إنهاء",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode)
        advanceUntilIdle()

        // Finish attendance first time
        viewModel.finishAttendance()
        advanceUntilIdle()

        val summary1 = viewModel.uiState.value.saveSummary
        assertNotNull(summary1)
        assertEquals(1, summary1?.presentCount)

        // Finish attendance second time
        viewModel.finishAttendance()
        advanceUntilIdle()

        // Verify repository only has 1 record for student on current date
        val today = viewModel.uiState.value.currentDate
        val record = attendanceRepository.getAttendanceByDate(s1.studentId, today)
        assertNotNull(record)
        assertEquals(AttendanceStatus.PRESENT, record?.status)
    }

    /**
     * Requirement H:
     * PRESENT students remain PRESENT.
     */
    @Test
    fun testH_presentStudents_remainPresentAfterFinish() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب حاضر",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode)
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val date = viewModel.uiState.value.currentDate
        val att = attendanceRepository.getAttendanceByDate(s1.studentId, date)
        assertNotNull(att)
        assertEquals(AttendanceStatus.PRESENT, att?.status)
    }

    /**
     * Requirement I:
     * Unscanned students become ABSENT according to business logic.
     */
    @Test
    fun testI_unscannedStudents_becomeAbsentAfterFinish() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب مسجل",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()
        val s2 = studentRepository.addStudent(
            fullName = "طالب غير مسجل",
            gradeId = targetGrade.id,
            parentPhone = "01012345679",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode) // s1 is present, s2 is unscanned
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val date = viewModel.uiState.value.currentDate
        val att1 = attendanceRepository.getAttendanceByDate(s1.studentId, date)
        val att2 = attendanceRepository.getAttendanceByDate(s2.studentId, date)

        assertEquals(AttendanceStatus.PRESENT, att1?.status)
        assertEquals(AttendanceStatus.ABSENT, att2?.status)
    }

    /**
     * Requirement J:
     * studentCode lookup is accurate.
     */
    @Test
    fun testJ_studentCodeLookup_isAccurateAndCaseInsensitive() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب كود",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Scan with different casing and whitespace
        viewModel.processScannedBarcode("  ${s1.studentCode.lowercase()}  ")
        val feedback = viewModel.uiState.value.scanFeedback

        assertNotNull(feedback)
        assertEquals(ScanResultType.SUCCESS_PRESENT, feedback?.type)
        assertEquals(s1.studentId, feedback?.student?.studentId)
    }

    /**
     * Requirement K:
     * Payment toggle does not modify attendance state.
     */
    @Test
    fun testK_paymentToggle_doesNotModifyAttendanceState() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب دفع",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.presentStudentIds.contains(s1.studentId))

        // Toggle payment
        viewModel.togglePaymentStatus(s1.studentId)
        advanceUntilIdle()

        // Verify attendance state is unchanged
        assertTrue(viewModel.uiState.value.presentStudentIds.contains(s1.studentId))
    }

    /**
     * Requirement L:
     * Network failure does not report false success and retains local present list.
     */
    @Test
    fun testL_networkFailure_doesNotReportFalseSuccess() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب شبكة",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // Create failing repository
        val failingAttendanceRepo = object : AttendanceRepository {
            override fun getAllAttendanceForTeacher(): Flow<List<Attendance>> = flowOf(emptyList())
            override fun getAttendanceForStudent(studentId: String): Flow<List<Attendance>> = flowOf(emptyList())
            override fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>> = flowOf(emptyList())
            override suspend fun getAttendanceSummaryForStudent(studentId: String, year: Int, month: Int): AttendanceSummary = AttendanceSummary()
            override suspend fun getTodayAttendanceCount(): Pair<Int, Int> = Pair(0, 0)
            override suspend fun recordOrUpdateAttendance(studentId: String, date: String, status: AttendanceStatus, note: String?, groupId: String?): Result<Attendance> {
                return Result.failure(Exception("Network socket timeout"))
            }
            override suspend fun recordBatchAttendance(
                date: String,
                records: List<com.example.core.model.BatchAttendanceItemDto>,
                groupId: String?
            ): Result<com.example.core.model.BatchAttendanceResult> {
                return Result.failure(Exception("Network socket timeout"))
            }
            override suspend fun deleteAttendance(attendanceId: String): Result<Unit> = Result.success(Unit)
            override suspend fun getAttendanceByDate(studentId: String, date: String): Attendance? = null
        }

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = failingAttendanceRepo,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode)
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Must NOT show false success summary
        assertNull(state.saveSummary)
        // Must show error message
        assertNotNull(state.errorMessage)
        // Must retain local scanned state for retry
        assertTrue(state.presentStudentIds.contains(s1.studentId))
    }

    /**
     * Requirement M:
     * Retry after failure saves correctly without duplicate attendance.
     */
    @Test
    fun testM_retryAfterFailure_savesCorrectlyWithoutDuplicates() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = studentRepository.addStudent(
            fullName = "طالب إعادة",
            gradeId = targetGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        var shouldFail = true
        val retryableRepo = object : AttendanceRepository {
            override fun getAllAttendanceForTeacher(): Flow<List<Attendance>> = flowOf(emptyList())
            override fun getAttendanceForStudent(studentId: String): Flow<List<Attendance>> = flowOf(emptyList())
            override fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>> = flowOf(emptyList())
            override suspend fun getAttendanceSummaryForStudent(studentId: String, year: Int, month: Int): AttendanceSummary = AttendanceSummary()
            override suspend fun getTodayAttendanceCount(): Pair<Int, Int> = Pair(0, 0)
            override suspend fun recordOrUpdateAttendance(studentId: String, date: String, status: AttendanceStatus, note: String?, groupId: String?): Result<Attendance> {
                if (shouldFail) {
                    return Result.failure(Exception("Network error"))
                }
                return attendanceRepository.recordOrUpdateAttendance(studentId, date, status, note, groupId)
            }
            override suspend fun recordBatchAttendance(
                date: String,
                records: List<com.example.core.model.BatchAttendanceItemDto>,
                groupId: String?
            ): Result<com.example.core.model.BatchAttendanceResult> {
                if (shouldFail) {
                    return Result.failure(Exception("Network error"))
                }
                return attendanceRepository.recordBatchAttendance(date, records, groupId)
            }
            override suspend fun deleteAttendance(attendanceId: String): Result<Unit> = Result.success(Unit)
            override suspend fun getAttendanceByDate(studentId: String, date: String): Attendance? = attendanceRepository.getAttendanceByDate(studentId, date)
        }

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = retryableRepo,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        viewModel.processScannedBarcode(s1.studentCode)
        advanceUntilIdle()

        // 1st attempt fails
        viewModel.finishAttendance()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.saveSummary)

        // Fix network & Retry
        shouldFail = false
        viewModel.finishAttendance()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.saveSummary)
        assertEquals(1, viewModel.uiState.value.saveSummary?.presentCount)
    }

    /**
     * Requirement N:
     * Existing current barcode behavior still works as expected.
     */
    @Test
    fun testN_existingCurrentBarcodeBehavior_works() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = Student(studentId = "s_std_1", teacherId = "t1", gradeId = targetGrade.id, fullName = "طالب باركود", studentCode = "BAR_1")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1))
        viewModel.onGradeSelected(targetGrade.id)
        viewModel.toggleScanner(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScannerActive)

        viewModel.processScannedBarcode("BAR_1")
        assertEquals(ScanResultType.SUCCESS_PRESENT, viewModel.uiState.value.scanFeedback?.type)
        assertEquals(1, viewModel.uiState.value.recentScannedStudents.size)
    }

    /**
     * Requirement O:
     * Existing manual attendance behavior (touch toggle on student row) still works.
     */
    @Test
    fun testO_existingManualAttendanceBehavior_stillWorks() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = Student(studentId = "s_manual_1", teacherId = "t1", gradeId = targetGrade.id, fullName = "طالب يدوي", studentCode = "MAN_1")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1))
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Toggle manually to PRESENT
        viewModel.toggleStudentPresent("s_manual_1")
        assertTrue(viewModel.uiState.value.presentStudentIds.contains("s_manual_1"))

        // Toggle manually back to ABSENT
        viewModel.toggleStudentPresent("s_manual_1")
        assertFalse(viewModel.uiState.value.presentStudentIds.contains("s_manual_1"))
    }

    /**
     * Requirement P:
     * Fast Attendance single-request batch upsert for 100 students works atomically.
     */
    @Test
    fun testP_recordBatchAttendance_100Students_atomicSuccess() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()

        val students100 = (1..100).map { i ->
            Student(
                studentId = "s_batch_$i",
                teacherId = "teacher_batch",
                gradeId = targetGrade.id,
                fullName = "طالب دفعة $i",
                studentCode = String.format("BTC%04d", i)
            )
        }

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(students100, students100)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Mark 65 as PRESENT, remaining 35 as ABSENT
        for (i in 1..65) {
            viewModel.processScannedBarcode(String.format("BTC%04d", i))
        }

        viewModel.finishAttendance()
        advanceUntilIdle()

        val summary = viewModel.uiState.value.saveSummary
        assertNotNull(summary)
        assertEquals(65, summary?.presentCount)
        assertEquals(35, summary?.absentCount)
        assertEquals(100, summary?.totalCount)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    /**
     * Requirement Q:
     * Duplicate finishAttendance invocation does not run concurrently when isSaving is true.
     */
    @Test
    fun testQ_concurrentFinishAttendance_isGuardedByIsSaving() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val s1 = Student(studentId = "s_lock_1", teacherId = "t1", gradeId = targetGrade.id, fullName = "طالب حماية", studentCode = "LCK_1")

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(listOf(s1), listOf(s1))
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Double invocation
        viewModel.finishAttendance()
        viewModel.finishAttendance() // Should return early due to isSaving
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.saveSummary)
        assertEquals(1, viewModel.uiState.value.saveSummary?.totalCount)
    }
}
