package com.example

import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockStudentRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddStudentSuccessFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var viewModel: AddEditStudentViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        studentRepository = MockStudentRepository()
        gradeRepository = MockGradeRepository()
        viewModel = AddEditStudentViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `adding student successfully populates createdStudent and preserves data on reset`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("أحمد محمود خالد")
        viewModel.onGradeSelected("grade_1")
        viewModel.onParentPhoneChange("01012345678")
        viewModel.onWhatsAppToggle(true)

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.createdStudent)
        assertEquals("أحمد محمود خالد", state.createdStudent?.fullName)
        assertTrue(state.createdStudent?.studentCode?.startsWith("ST-") == true)

        // Test "Add Another Student" reset
        viewModel.resetForAnotherStudent()
        val resetState = viewModel.uiState.value

        assertNull(resetState.createdStudent)
        assertEquals("", resetState.fullName)
        assertEquals("", resetState.parentPhone)
        assertEquals("grade_1", resetState.selectedGradeId) // keeps selected grade for convenience
        assertTrue(resetState.hasWhatsApp)
    }
}
