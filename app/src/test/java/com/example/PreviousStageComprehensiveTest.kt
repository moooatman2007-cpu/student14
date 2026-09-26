package com.example

import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.attendance.FastAttendanceViewModel
import com.example.ui.students.StudentListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PreviousStageComprehensiveTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var paymentRepository: MockPaymentRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PRIMARY)
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
     * مدرس مرحلته الحالية ابتدائي ولديه طالب ابتدائي:
     * - يظهر بشكل طبيعي.
     */
    @Test
    fun testRequirementA_PrimaryTeacherWithPrimaryStudentDisplaysNormally() = runTest {
        val primaryGrades = gradeRepository.getGrades().first()
        val firstPrimaryGrade = primaryGrades.first { it.name == "الأول الابتدائي" }

        val addResult = studentRepository.addStudent(
            fullName = "ياسين أحمد إبراهيم",
            gradeId = firstPrimaryGrade.id,
            parentPhone = "01011112222",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow()

        val viewModel = StudentListViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val foundStudent = state.students.find { it.studentId == student.studentId }
        assertNotNull(foundStudent)
        assertEquals("الأول الابتدائي", state.gradeMap[student.gradeId])
        assertFalse(state.gradeMap[student.gradeId]?.contains("مرحلة سابقة") == true)
    }

    /**
     * Requirement B:
     * مدرس غيّر المرحلة من إعدادي إلى ثانوي ولديه طالب قديم في Grade إعدادي:
     * - الطالب لا يُحذف.
     * - يظهر في All Students (البحث أو بدون فلتر صف).
     * - gradeId يظل كما هو.
     * - الصف يظهر كـ Previous Stage ("الأول الإعدادي — مرحلة سابقة").
     */
    @Test
    fun testRequirementB_StageChangedFromPrepToSec_StudentPreservedAndShowsPreviousStageLabel() = runTest {
        // 1. Teacher starts in preparatory stage
        gradeRepository.setStage(EducationalStages.PREPARATORY)
        val prepGrades = gradeRepository.getGrades().first()
        val prepGrade1 = prepGrades.first { it.name == "الأول الإعدادي" }

        val addResult = studentRepository.addStudent(
            fullName = "زياد طارق المنشاوي",
            gradeId = prepGrade1.id,
            parentPhone = "01122334455",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow()

        // 2. Teacher switches stage to Secondary (ثانوي)
        gradeRepository.setStage(EducationalStages.SECONDARY)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Open StudentListViewModel
        val viewModel = StudentListViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value

        // - Student not deleted
        val studentInDb = studentRepository.getStudentById(student.studentId)
        assertNotNull(studentInDb)
        assertEquals(student.studentId, studentInDb?.studentId)

        // - gradeId remains unchanged
        assertEquals(prepGrade1.id, studentInDb?.gradeId)

        // - Student appears in all students
        val studentInList = state.students.find { it.studentId == student.studentId }
        assertNotNull(studentInList)

        // - Grade appears as Previous Stage
        val gradeDisplay = state.gradeMap[student.gradeId]
        assertNotNull(gradeDisplay)
        assertEquals("الأول الإعدادي — مرحلة سابقة", gradeDisplay)

        // - Original grade name in database remains pure without UI label
        val originalGradeInDb = gradeRepository.getGradeById(prepGrade1.id)
        assertEquals("الأول الإعدادي", originalGradeInDb?.name)
    }

    /**
     * Requirement C:
     * عند اختيار Grade ثانوي:
     * - لا يظهر الطالب الإعدادي.
     */
    @Test
    fun testRequirementC_SelectingSecondaryGradeDoesNotShowPreparatoryStudent() = runTest {
        // 1. Create preparatory student
        gradeRepository.setStage(EducationalStages.PREPARATORY)
        val prepGrades = gradeRepository.getGrades().first()
        val prepGrade = prepGrades.first()
        val prepStudent = studentRepository.addStudent(
            fullName = "علي كمال الشريف",
            gradeId = prepGrade.id,
            parentPhone = "01233445566",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // 2. Switch to Secondary stage and create a secondary student
        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrades = gradeRepository.getGrades().first()
        val secGrade1 = secGrades.first { it.name == "الأول الثانوي" }
        val secStudent = studentRepository.addStudent(
            fullName = "حسام عبد الله",
            gradeId = secGrade1.id,
            parentPhone = "01511223344",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        val viewModel = StudentListViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Filter strictly by Secondary 1st grade
        viewModel.onGradeSelected(secGrade1.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(secGrade1.id, state.selectedGradeId)
        assertTrue(state.students.any { it.studentId == secStudent.studentId })
        // Strictly verify preparatory student is NOT shown inside secondary grade
        assertFalse(state.students.any { it.studentId == prepStudent.studentId })
    }

    /**
     * Requirement D:
     * عند حساب Grade cards للمرحلة الثانوية:
     * - لا يتم احتساب الطالب الإعدادي داخل أي Grade ثانوي.
     */
    @Test
    fun testRequirementD_GradeCardsForSecondaryStageDoNotCountPreparatoryStudent() = runTest {
        // Preparatory student in prep grade
        gradeRepository.setStage(EducationalStages.PREPARATORY)
        val prepGrade = gradeRepository.getGrades().first().first()
        studentRepository.addStudent(
            fullName = "محمد عادل رشاد",
            gradeId = prepGrade.id,
            parentPhone = "01099887766",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // Switch to secondary
        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrades = gradeRepository.getGrades().first()
        val secGrade1 = secGrades.first()

        // Add 2 students in secondary grade 1
        studentRepository.addStudent("طالب ثانوي 1", secGrade1.id, "01000000001", true, null).getOrThrow()
        studentRepository.addStudent("طالب ثانوي 2", secGrade1.id, "01000000002", true, null).getOrThrow()

        val viewModel = StudentListViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val secCard1 = state.grades.find { it.id == secGrade1.id }
        assertEquals(2, secCard1?.studentCount)

        // Other secondary grades must be 0
        state.grades.filterNot { it.id == secGrade1.id }.forEach { grade ->
            assertEquals(0, grade.studentCount)
        }
    }

    /**
     * Requirement E:
     * totalStudents:
     * - يحافظ على المعنى الحالي (إجمالي كل طلاب المدرس النشطين).
     */
    @Test
    fun testRequirementE_TotalStudentsPreservesCurrentMeaningAcrossStages() = runTest {
        // Clear all students and start fresh
        val initialStats = studentRepository.getStats().first()
        val initialCount = initialStats.totalStudents

        // Add 1 student in prep
        gradeRepository.setStage(EducationalStages.PREPARATORY)
        val prepGrade = gradeRepository.getGrades().first().first()
        studentRepository.addStudent("طالب إعدادي", prepGrade.id, "01011110001", true, null).getOrThrow()

        // Switch to sec and add 1 student in sec
        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrade = gradeRepository.getGrades().first().first()
        studentRepository.addStudent("طالب ثانوي", secGrade.id, "01011110002", true, null).getOrThrow()

        val updatedStats = studentRepository.getStats().first()
        // Total students accounts for both students
        assertEquals(initialCount + 2, updatedStats.totalStudents)
    }

    /**
     * Requirement F:
     * Fast Attendance:
     * - لا يحدث cross-teacher.
     * - لا يحدث تغيير تلقائي للـ gradeId.
     * - لا يتم خلط الصفوف عند الفلترة بـ gradeId.
     */
    @Test
    fun testRequirementF_FastAttendancePreservesGradeIdAndScopeIsolation() = runTest {
        gradeRepository.setStage(EducationalStages.PREPARATORY)
        val prepGrade = gradeRepository.getGrades().first().first()
        val prepStudent = studentRepository.addStudent("طالب إعدادي للحضور", prepGrade.id, "01011110001", true, null).getOrThrow()

        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrade = gradeRepository.getGrades().first().first()
        val secStudent = studentRepository.addStudent("طالب ثانوي للحضور", secGrade.id, "01011110002", true, null).getOrThrow()

        val fastAttendanceViewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by secondary grade
        fastAttendanceViewModel.onGradeSelected(secGrade.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = fastAttendanceViewModel.uiState.value
        // Only secondary student is in scope
        assertTrue(state.allStudentsInScope.any { it.studentId == secStudent.studentId })
        assertFalse(state.allStudentsInScope.any { it.studentId == prepStudent.studentId })

        // Check in repository that gradeId of both students remains completely unmodified
        assertEquals(prepGrade.id, studentRepository.getStudentById(prepStudent.studentId)?.gradeId)
        assertEquals(secGrade.id, studentRepository.getStudentById(secStudent.studentId)?.gradeId)
    }
}
