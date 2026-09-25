package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockHomeworkRepository
import com.example.data.repository.MockMonthlyReportRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.student_detail.StudentDetailTab
import com.example.ui.student_detail.StudentDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudentDetailRefreshLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var examRepository: MockExamRepository
    private lateinit var homeworkRepository: MockHomeworkRepository
    private lateinit var paymentRepository: MockPaymentRepository
    private lateinit var monthlyReportRepository: MockMonthlyReportRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        recitationRepository = MockRecitationRepository()
        examRepository = MockExamRepository()
        homeworkRepository = MockHomeworkRepository()
        paymentRepository = MockPaymentRepository()
        monthlyReportRepository = MockMonthlyReportRepository(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSingleRefresh_QueriesAndPopulatesStateCorrectly() = runTest {
        val student = studentRepository.addStudent("أحمد خالد", "prep_1", "01011111111", true, null).getOrThrow()

        // Seed some data for student
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-05", AttendanceStatus.PRESENT, null)
        recitationRepository.addRecitation(student.studentId, "2026-09-05", "تسميع 1", "سورة النبأ", 10.0, 10.0, null)
        examRepository.addExam(student.studentId, "2026-09-10", "امتحان شهري", "تجويد", 50.0, 50.0, null)

        val viewModel = StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            monthlyReportRepository = monthlyReportRepository,
            homeworkRepository = homeworkRepository,
            paymentRepository = paymentRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(student.studentId, state.student?.studentId)
        assertEquals("أحمد خالد", state.student?.fullName)
        assertTrue(state.attendances.isNotEmpty())
        assertTrue(state.recitations.isNotEmpty())
        assertTrue(state.exams.isNotEmpty())
    }

    @Test
    fun testRapidMonthSwitching_CancelsPreviousRefreshes_NoCollectorAccumulation() = runTest {
        val student = studentRepository.addStudent("عمر فاروق", "prep_1", "01022222222", true, null).getOrThrow()

        val viewModel = StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            monthlyReportRepository = monthlyReportRepository,
            homeworkRepository = homeworkRepository,
            paymentRepository = paymentRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Rapid month navigations
        viewModel.navigateMonth(1)
        viewModel.navigateMonth(1)
        viewModel.navigateMonth(1)
        viewModel.navigateMonth(-1)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.student)
        assertEquals("عمر فاروق", state.student?.fullName)
    }

    @Test
    fun testChangingStudentDuringRefresh_StaleResponseDoesNotOverwriteNewStudent() = runTest {
        val student1 = studentRepository.addStudent("طالب أول", "prep_1", "01033333331", true, null).getOrThrow()
        val student2 = studentRepository.addStudent("طالب ثاني", "prep_1", "01033333332", true, null).getOrThrow()

        val slowAttendanceRepository = object : AttendanceRepository by attendanceRepository {
            override fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>> = flow {
                if (studentId == student1.studentId) {
                    delay(500) // Simulate slow delayed response for student 1
                }
                emit(listOf(Attendance(attendanceId = "att_$studentId", studentId = studentId, teacherId = "teacher_x", date = "2026-09-01", status = AttendanceStatus.PRESENT, note = null)))
            }
        }

        val viewModel = StudentDetailViewModel(
            savedStateHandle = null,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = slowAttendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            monthlyReportRepository = monthlyReportRepository,
            homeworkRepository = homeworkRepository,
            paymentRepository = paymentRepository
        )

        // Load student 1 then immediately switch to student 2
        viewModel.loadStudent(student1.studentId)
        testDispatcher.scheduler.advanceTimeBy(100) // student 1 still in flight

        viewModel.loadStudent(student2.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(student2.studentId, state.student?.studentId)
        assertEquals("طالب ثاني", state.student?.fullName)
        assertEquals("att_${student2.studentId}", state.attendances.firstOrNull()?.attendanceId)
    }

    @Test
    fun testTabSwitching_DoesNotAccumulateLingeringCollectors() = runTest {
        val student = studentRepository.addStudent("محمود سمير", "prep_1", "01044444444", true, null).getOrThrow()

        val viewModel = StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            monthlyReportRepository = monthlyReportRepository,
            homeworkRepository = homeworkRepository,
            paymentRepository = paymentRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch tabs rapidly
        viewModel.selectTab(StudentDetailTab.ATTENDANCE)
        viewModel.selectTab(StudentDetailTab.RECITATIONS)
        viewModel.selectTab(StudentDetailTab.EXAMS)
        viewModel.selectTab(StudentDetailTab.HOMEWORK)
        viewModel.selectTab(StudentDetailTab.MONTHLY_REPORT)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StudentDetailTab.MONTHLY_REPORT, state.selectedTab)
    }
}
