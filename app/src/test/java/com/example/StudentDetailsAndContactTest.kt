package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.LessonPayment
import com.example.core.model.Student
import com.example.data.repository.HomeworkRepository
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockMonthlyReportRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.PaymentRepository
import com.example.ui.student_detail.StudentDetailEvent
import com.example.ui.student_detail.StudentDetailViewModel
import com.example.util.PhoneUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

class FakeHomeworkRepository : HomeworkRepository {
    override fun getHomeworkForStudent(studentId: String): Flow<List<Homework>> = flowOf(emptyList())
    override fun getHomeworkForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Homework>> = flowOf(emptyList())
    override fun getHomeworkForTeacher(): Flow<List<Homework>> = flowOf(emptyList())
    override suspend fun addHomework(
        studentId: String,
        date: String,
        title: String,
        status: HomeworkStatus,
        note: String?
    ): Result<Homework> {
        val hw = Homework(
            homeworkId = "hw_1",
            studentId = studentId,
            teacherId = "t_1",
            date = date,
            title = title,
            status = status,
            note = note
        )
        return Result.success(hw)
    }
    override suspend fun updateHomework(homework: Homework): Result<Homework> = Result.success(homework)
    override suspend fun deleteHomework(homeworkId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getHomeworkById(homeworkId: String): Homework? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudentDetailsAndContactTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepo: MockGradeRepository
    private lateinit var studentRepo: MockStudentRepository
    private lateinit var attendanceRepo: MockAttendanceRepository
    private lateinit var recitationRepo: MockRecitationRepository
    private lateinit var examRepo: MockExamRepository
    private lateinit var monthlyReportRepo: MockMonthlyReportRepository
    private lateinit var fakeHomeworkRepo: FakeHomeworkRepository
    private lateinit var paymentRepo: MockPaymentRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepo = MockGradeRepository(initialStage = EducationalStages.PRIMARY)
        studentRepo = MockStudentRepository(gradeRepository = gradeRepo)
        attendanceRepo = MockAttendanceRepository()
        recitationRepo = MockRecitationRepository()
        examRepo = MockExamRepository()
        monthlyReportRepo = MockMonthlyReportRepository(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            recitationRepository = recitationRepo,
            examRepository = examRepo
        )
        fakeHomeworkRepo = FakeHomeworkRepository()
        paymentRepo = MockPaymentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(studentId: String, customPaymentRepo: PaymentRepository = paymentRepo): StudentDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("studentId" to studentId))
        return StudentDetailViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            recitationRepository = recitationRepo,
            examRepository = examRepo,
            monthlyReportRepository = monthlyReportRepo,
            homeworkRepository = fakeHomeworkRepo,
            paymentRepository = customPaymentRepo,
            groupRepository = MockGroupRepository()
        )
    }

    // ===================================
    // 1. STUDENT DETAILS & STAGES
    // ===================================

    @Test
    fun testOpenCurrentStageStudent_loadsDataAndGradeCorrectly() = runTest {
        val primaryGrade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent(
            fullName = "أحمد محمد محمود",
            gradeId = primaryGrade.id,
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = "01122334455"
        ).getOrThrow()

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.student)
        assertEquals("أحمد محمد محمود", state.student?.fullName)
        assertEquals(primaryGrade.id, state.grade?.id)
        assertFalse(state.isLoading)
    }

    @Test
    fun testOpenPreviousStageStudent_loadsDataAndPreservesStageDetails() = runTest {
        // Create prep student
        gradeRepo.setStage(EducationalStages.PREPARATORY)
        val prepGrade = gradeRepo.getGrades().first().first()
        val prepStudent = studentRepo.addStudent(
            fullName = "محمود علي حسن",
            gradeId = prepGrade.id,
            parentPhone = "01234567890",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // Teacher switches to Secondary stage
        gradeRepo.setStage(EducationalStages.SECONDARY)

        // Open student in StudentDetailViewModel
        val vm = createViewModel(prepStudent.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.student)
        assertEquals("محمود علي حسن", state.student?.fullName)
        assertEquals(prepGrade.id, state.student?.gradeId)
        assertFalse(state.isLoading)
    }

    // ===================================
    // 2. MONTHLY PAYMENTS
    // ===================================

    @Test
    fun testMissingPaymentRecord_defaultsToUnpaid() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("سامي عبد الله", grade.id, "01099998888", true, null).getOrThrow()

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.monthlyPayment)
        assertFalse(state.isPaymentPaid)
    }

    @Test
    fun testTogglePayment_unpaidToPaid_updatesState() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("عمر فاروق", grade.id, "01011112222", true, null).getOrThrow()

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isPaymentPaid)

        // Toggle to Paid
        vm.toggleMonthlyPayment()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isPaymentPaid)
        assertNotNull(state.monthlyPayment)
        assertTrue(state.monthlyPayment!!.isPaid)
    }

    @Test
    fun testTogglePayment_paidToUnpaid_updatesState() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("خالد وليد", grade.id, "01033334444", true, null).getOrThrow()

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Toggle Unpaid -> Paid
        vm.toggleMonthlyPayment()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isPaymentPaid)

        // 2. Toggle Paid -> Unpaid
        vm.toggleMonthlyPayment()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isPaymentPaid)
        assertFalse(vm.uiState.value.monthlyPayment!!.isPaid)
    }

    @Test
    fun testTogglePayment_rollbackOnNetworkFailure() = runTest {
        val failingPaymentRepo = object : PaymentRepository {
            override fun getMonthlyPayments(year: Int, month: Int): Flow<List<LessonPayment>> = flowOf(emptyList())
            override suspend fun getMonthlyPaymentsList(year: Int, month: Int): List<LessonPayment> = emptyList()
            override suspend fun getPaymentForStudent(studentId: String, year: Int, month: Int): LessonPayment? = null
            override suspend fun setPaymentStatus(
                studentId: String,
                year: Int,
                month: Int,
                isPaid: Boolean,
                amount: Double
            ): Result<LessonPayment> = Result.failure(Exception("network error simulated"))
            override suspend fun togglePaymentStatus(
                studentId: String,
                year: Int,
                month: Int,
                amount: Double
            ): Result<LessonPayment> = Result.failure(Exception("network error simulated"))
        }

        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("طالب خطأ شبكة", grade.id, "01055556666", true, null).getOrThrow()

        val vm = createViewModel(student.studentId, customPaymentRepo = failingPaymentRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        // Ensure initial state is Unpaid
        assertFalse(vm.uiState.value.isPaymentPaid)

        // Toggle payment status
        vm.toggleMonthlyPayment()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state rolled back to unpaid and toggling flag is reset to false
        val state = vm.uiState.value
        assertFalse(state.isPaymentPaid)
        assertFalse(state.isTogglingPayment)
    }

    @Test
    fun testTogglePayment_doesNotModifyAttendanceData() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("طالب الحضور والدفع", grade.id, "01077778888", true, null).getOrThrow()

        // Record 1 attendance record
        attendanceRepo.recordOrUpdateAttendance(
            studentId = student.studentId,
            date = "2026-09-18",
            status = AttendanceStatus.PRESENT,
            note = "حاضر في الموعد"
        )

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialAttendanceList = vm.uiState.value.attendances
        assertEquals(1, initialAttendanceList.size)
        assertEquals(AttendanceStatus.PRESENT, initialAttendanceList.first().status)

        // Toggle payment
        vm.toggleMonthlyPayment()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify attendance data is completely unchanged
        val afterToggleAttendanceList = vm.uiState.value.attendances
        assertEquals(1, afterToggleAttendanceList.size)
        assertEquals("2026-09-18", afterToggleAttendanceList.first().date)
        assertEquals(AttendanceStatus.PRESENT, afterToggleAttendanceList.first().status)
    }

    // ===================================
    // 3. PARENT CONTACT & PHONE UTIL
    // ===================================

    @Test
    fun testPhoneUtil_formattingAndValidation() {
        assertTrue(PhoneUtil.hasValidPhoneNumber("01012345678"))
        assertTrue(PhoneUtil.hasValidPhoneNumber("٠١٠١٢٣٤٥٦٧٨"))
        assertFalse(PhoneUtil.hasValidPhoneNumber(""))
        assertFalse(PhoneUtil.hasValidPhoneNumber(null))
        assertFalse(PhoneUtil.hasValidPhoneNumber("123"))

        assertEquals("01012345678", PhoneUtil.formatForDial("010-1234-5678"))
        assertEquals("+201012345678", PhoneUtil.formatForDial("+201012345678"))

        assertEquals("201012345678", PhoneUtil.formatForWhatsApp("01012345678"))
        assertEquals("201012345678", PhoneUtil.formatForWhatsApp("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("201012345678", PhoneUtil.formatForWhatsApp("+201012345678"))
    }

    @Test
    fun testParentContact_noPhone_safelyHandled() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val studentWithoutPhone = studentRepo.addStudent("طالب بدون هاتف", grade.id, "", false, null).getOrThrow()

        val vm = createViewModel(studentWithoutPhone.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val student = vm.uiState.value.student
        assertNotNull(student)
        assertFalse(PhoneUtil.hasValidPhoneNumber(student?.parentPhone))
    }

    @Test
    fun testParentContact_whatsAppDisabled_safelyHandled() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val studentNoWA = studentRepo.addStudent("طالب بدون واتساب", grade.id, "01099887766", false, null).getOrThrow()

        val vm = createViewModel(studentNoWA.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val student = vm.uiState.value.student
        assertNotNull(student)
        assertTrue(PhoneUtil.hasValidPhoneNumber(student?.parentPhone))
        assertFalse(student!!.hasWhatsApp)
    }

    // ===================================
    // 4. ISOLATION & SECURITY
    // ===================================

    @Test
    fun testTenantIsolation_studentBelongsToCurrentTeacher() = runTest {
        val grade = gradeRepo.getGrades().first().first()
        val student = studentRepo.addStudent("طالب معزول", grade.id, "01000000000", true, null).getOrThrow()

        val vm = createViewModel(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val loadedStudent = vm.uiState.value.student
        assertNotNull(loadedStudent)
        assertEquals(student.studentId, loadedStudent?.studentId)
    }
}
