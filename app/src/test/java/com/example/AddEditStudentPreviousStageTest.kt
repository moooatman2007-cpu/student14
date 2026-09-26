package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.core.model.EducationalStages
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.data.repository.MockGroupRepository
import com.example.ui.add_edit_student.AddEditStudentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddEditStudentPreviousStageTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var teacherRepository: MockTeacherRepository
    private lateinit var groupRepository: MockGroupRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        teacherRepository = MockTeacherRepository()
        groupRepository = MockGroupRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editing student in current stage preserves grade and sets selectedGradeId correctly`() = runTest {
        // 1. Add student in current preparatory stage
        val addResult = studentRepository.addStudent(
            fullName = "أحمد مصطفى محمود",
            gradeId = "grade_prep_1",
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val createdStudent = addResult.getOrThrow()
        val savedStateHandle = SavedStateHandle(mapOf("studentId" to createdStudent.studentId))

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.isEditMode)
        assertEquals(createdStudent.studentId, state.studentId)
        assertEquals("grade_prep_1", state.selectedGradeId)
        assertTrue(state.grades.any { it.id == "grade_prep_1" })
    }

    @Test
    fun `editing student from previous stage preserves legacy gradeId and displays with previous stage label`() = runTest {
        // 1. Add student in preparatory grade
        val addResult = studentRepository.addStudent(
            fullName = "عمر أحمد الشناوي",
            gradeId = "grade_prep_1",
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val createdStudent = addResult.getOrThrow()

        // 2. Teacher switches educational stage to Secondary (ثانوي)
        gradeRepository.setStage(EducationalStages.SECONDARY)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Open edit screen for the preparatory student while in secondary stage
        val savedStateHandle = SavedStateHandle(mapOf("studentId" to createdStudent.studentId))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.isEditMode)
        assertEquals(createdStudent.studentId, state.studentId)
        // Ensure legacy gradeId is preserved and automatically selected
        assertEquals("grade_prep_1", state.selectedGradeId)

        // Ensure legacy grade is present in the UI grades dropdown list with legacy label
        val legacyGradeInList = state.grades.find { it.id == "grade_prep_1" }
        assertNotNull(legacyGradeInList)
        assertTrue(legacyGradeInList!!.name.contains("مرحلة سابقة"))
    }

    @Test
    fun `saving student from previous stage without changing grade preserves the original gradeId in database`() = runTest {
        // 1. Create preparatory student
        val addResult = studentRepository.addStudent(
            fullName = "مصطفى يوسف فوزي",
            gradeId = "grade_prep_2",
            parentPhone = "01122334455",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow()

        // 2. Teacher switches stage to Secondary
        gradeRepository.setStage(EducationalStages.SECONDARY)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Open edit screen and modify name & phone only without touching grade
        val savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("مصطفى يوسف فوزي المعدل")
        viewModel.onParentPhoneChange("01199887766")
        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify in repository that student still retains the original gradeId and unchanged grade name in DB
        val updatedStudentInDb = studentRepository.getStudentById(student.studentId)
        assertNotNull(updatedStudentInDb)
        assertEquals("مصطفى يوسف فوزي المعدل", updatedStudentInDb?.fullName)
        assertEquals("01199887766", updatedStudentInDb?.parentPhone)
        assertEquals("grade_prep_2", updatedStudentInDb?.gradeId)

        // Verify grade entity in DB still has pure name without label
        val gradeInDb = gradeRepository.getGradeById("grade_prep_2")
        assertEquals("الثاني الإعدادي", gradeInDb?.name)
    }

    @Test
    fun `explicitly choosing new grade updates gradeId correctly`() = runTest {
        // 1. Create student in preparatory grade
        val addResult = studentRepository.addStudent(
            fullName = "خالد إبراهيم سعيد",
            gradeId = "grade_prep_1",
            parentPhone = "01511223344",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow()

        // 2. Teacher switches stage to Secondary
        gradeRepository.setStage(EducationalStages.SECONDARY)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Open edit screen
        val savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Explicitly select secondary 1st grade
        val secondaryGrades = gradeRepository.ensureGradesForStage(EducationalStages.SECONDARY)
        val firstSecondaryGrade = secondaryGrades.first()
        viewModel.onGradeSelected(firstSecondaryGrade.id)

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify in DB that gradeId was updated to the new valid secondary grade
        val updatedStudent = studentRepository.getStudentById(student.studentId)
        assertNotNull(updatedStudent)
        assertEquals(firstSecondaryGrade.id, updatedStudent?.gradeId)
    }
}
