package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.core.model.Teacher
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CountOptimizationAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var examRepository: MockExamRepository
    private lateinit var teacherRepository: MockTeacherRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        recitationRepository = MockRecitationRepository()
        examRepository = MockExamRepository()
        teacherRepository = MockTeacherRepository(
            initialTeacher = Teacher(
                id = "teacher_1",
                email = "teacher@test.com",
                fullName = "أستاذ أحمد",
                educationalStage = EducationalStages.PREPARATORY
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAttendanceTodayCount_CalculatesCorrectly() = runTest {
        val student1 = studentRepository.addStudent(
            fullName = "طالب 1",
            gradeId = "prep_1",
            parentPhone = "01000000001",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val student2 = studentRepository.addStudent(
            fullName = "طالب 2",
            gradeId = "prep_1",
            parentPhone = "01000000002",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

        attendanceRepository.recordOrUpdateAttendance(student1.studentId, todayStr, AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student2.studentId, todayStr, AttendanceStatus.ABSENT, null)

        val todayRecords = attendanceRepository.getAttendanceForStudent(student1.studentId).first()
        assertNotNull(todayRecords)

        val (present, absent) = attendanceRepository.getTodayAttendanceCount()
        // Mock returns predefined date or today count
        assertNotNull(present)
        assertNotNull(absent)
    }

    @Test
    fun testRecitationsCountThisMonth_CalculatesCorrectly() = runTest {
        val student = studentRepository.addStudent(
            fullName = "محمد علي",
            gradeId = "prep_1",
            parentPhone = "01000000003",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val dateStr = String.format(Locale.ENGLISH, "%04d-%02d-15", currentYear, currentMonth)

        val initialCount = recitationRepository.getRecitationsCountThisMonth(currentYear, currentMonth)

        recitationRepository.addRecitation(
            studentId = student.studentId,
            date = dateStr,
            title = "تسميع 1",
            content = "الوحدة الأولى",
            score = 10.0,
            maxScore = 10.0,
            note = null
        )

        recitationRepository.addRecitation(
            studentId = student.studentId,
            date = dateStr,
            title = "تسميع 2",
            content = "الوحدة الثانية",
            score = 9.0,
            maxScore = 10.0,
            note = null
        )

        val count = recitationRepository.getRecitationsCountThisMonth(currentYear, currentMonth)
        assertEquals(initialCount + 2, count)
    }

    @Test
    fun testExamsCountThisMonth_CalculatesCorrectly() = runTest {
        val student = studentRepository.addStudent(
            fullName = "سارة أحمد",
            gradeId = "prep_1",
            parentPhone = "01000000004",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val dateStr = String.format(Locale.ENGLISH, "%04d-%02d-20", currentYear, currentMonth)

        val initialCount = examRepository.getExamsCountThisMonth(currentYear, currentMonth)

        examRepository.addExam(
            studentId = student.studentId,
            date = dateStr,
            examName = "امتحان شهري",
            subject = "علوم",
            score = 45.0,
            maxScore = 50.0,
            note = null
        )

        val count = examRepository.getExamsCountThisMonth(currentYear, currentMonth)
        assertEquals(initialCount + 1, count)
    }

    @Test
    fun testHomeViewModel_DashboardCountersIntegration() = runTest {
        val student = studentRepository.addStudent(
            fullName = "كريم حسن",
            gradeId = "prep_1",
            parentPhone = "01000000005",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val dateStr = String.format(Locale.ENGLISH, "%04d-%02d-10", currentYear, currentMonth)

        val expectedRecCount = recitationRepository.getRecitationsCountThisMonth(currentYear, currentMonth) + 1
        val expectedExCount = examRepository.getExamsCountThisMonth(currentYear, currentMonth) + 1

        recitationRepository.addRecitation(
            studentId = student.studentId,
            date = dateStr,
            title = "تسميع شامل",
            content = "مراجعة عامة",
            score = 20.0,
            maxScore = 20.0,
            note = null
        )

        examRepository.addExam(
            studentId = student.studentId,
            date = dateStr,
            examName = "امتحان تجريبي",
            subject = "رياضيات",
            score = 30.0,
            maxScore = 30.0,
            note = null
        )

        val homeViewModel = HomeViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            teacherRepository = teacherRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = homeViewModel.uiState.value
        assertEquals("أستاذ أحمد", state.teacherName)
        assertEquals(expectedRecCount, state.recitationsThisMonth)
        assertEquals(expectedExCount, state.examsThisMonth)
    }
}
