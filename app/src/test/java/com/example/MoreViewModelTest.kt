package com.example

import com.example.core.model.Teacher
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockSettingsRepository
import com.example.data.repository.TeacherRepository
import com.example.data.repository.ThemeMode
import com.example.ui.more.MoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeTeacherRepository : TeacherRepository {
    private val _teacher = MutableStateFlow<Teacher?>(null)
    override fun getCurrentTeacher(): Flow<Teacher?> = _teacher
    override suspend fun fetchCurrentTeacher(): Teacher? = _teacher.value
    override suspend fun updateTeacherProfile(
        fullName: String,
        phoneNumber: String?,
        subject: String?,
        centerName: String?,
        educationalStage: String?,
        avatarUrl: String?
    ): Result<Teacher> {
        val updated = Teacher(
            id = "teacher_id",
            email = "teacher@test.com",
            fullName = fullName,
            phoneNumber = phoneNumber,
            subject = subject,
            centerName = centerName,
            educationalStage = educationalStage,
            avatarUrl = avatarUrl
        )
        _teacher.value = updated
        return Result.success(updated)
    }
    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String): Result<String> {
        return Result.success("https://dummy.url/avatar.jpg")
    }
    override suspend fun deleteAvatar(): Result<Unit> {
        return Result.success(Unit)
    }

    fun setTeacher(teacher: Teacher?) {
        _teacher.value = teacher
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: MockSettingsRepository
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var teacherRepository: FakeTeacherRepository
    private lateinit var viewModel: MoreViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = MockSettingsRepository()
        gradeRepository = MockGradeRepository()
        teacherRepository = FakeTeacherRepository()

        // Set up initial teacher data
        teacherRepository.setTeacher(
            Teacher(
                id = "t_1",
                email = "teacher@test.com",
                fullName = "أستاذ تجريبي",
                phoneNumber = "01012345678",
                subject = "الرياضيات",
                centerName = "المركز التعليمي",
                educationalStage = "PRIMARY",
                avatarUrl = null
            )
        )

        viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSelectPairingTabUpdatesStateAndClearsError() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Default tab should be CODE (for 1-phone optimization)
        assertEquals("CODE", viewModel.uiState.value.selectedPairingTab)

        // Switch to QR
        viewModel.selectPairingTab("QR")
        assertEquals("QR", viewModel.uiState.value.selectedPairingTab)

        // Switch back to CODE
        viewModel.selectPairingTab("CODE")
        assertEquals("CODE", viewModel.uiState.value.selectedPairingTab)
    }

    @Test
    fun testOnPairingPhoneNumberChangeNormalizesDigits() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Test with Arabic-Indic numbers and spaces
        viewModel.onPairingPhoneNumberChange("٠١٠١٢٣٤٥٦٧٨ ")
        testDispatcher.scheduler.advanceUntilIdle()

        // Check if digits were properly normalized to Latin/ASCII digits
        assertEquals("01012345678", viewModel.uiState.value.pairingPhoneNumber)
        assertNull(viewModel.uiState.value.pairingCodeError)
    }

    @Test
    fun testRequestPairingCodeEmptyPhoneError() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Phone is blank initially
        viewModel.onPairingPhoneNumberChange("")
        viewModel.requestPairingCode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("يرجى إدخال رقم الهاتف أولاً.", viewModel.uiState.value.pairingCodeError)
        assertFalse(viewModel.uiState.value.isRequestingPairingCode)
    }

    @Test
    fun testRequestPairingCodeInvalidPhoneError() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Phone is too short
        viewModel.onPairingPhoneNumberChange("123")
        viewModel.requestPairingCode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pairingCodeError?.contains("غير صالح") == true)
        assertFalse(viewModel.uiState.value.isRequestingPairingCode)
    }

    @Test
    fun testOpenAndCloseWahaPairingDialog() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showWahaPairingDialog)

        // Opening dialog should update state and reset pairing variables
        viewModel.openWahaPairingDialog()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showWahaPairingDialog)
        assertEquals("CODE", viewModel.uiState.value.selectedPairingTab)
        assertEquals("01012345678", viewModel.uiState.value.pairingPhoneNumber)
        assertNull(viewModel.uiState.value.pairingCode)
        assertNull(viewModel.uiState.value.pairingCodeError)

        // Closing dialog
        viewModel.closeWahaPairingDialog()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showWahaPairingDialog)
    }

    @Test
    fun testLoadedDataInitialization() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("أستاذ تجريبي", state.teacherName)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
    }
}
