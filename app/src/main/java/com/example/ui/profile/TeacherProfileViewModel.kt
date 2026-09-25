package com.example.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Grade
import com.example.core.model.Teacher
import com.example.data.repository.GradeRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherProfileUiState(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val subject: String = "",
    val customSubject: String = "",
    val isCustomSubject: Boolean = false,
    val centerName: String = "",
    val educationalStage: String = "",
    val avatarUrl: String? = null,
    val grades: List<Grade> = emptyList(),
    val isLoading: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val nameError: String? = null
)

val PREDEFINED_STAGES = listOf(
    "ابتدائي",
    "إعدادي",
    "ثانوي"
)

val PREDEFINED_SUBJECTS = listOf(
    "قرآن كريم",
    "لغة عربية",
    "رياضيات",
    "لغة إنجليزية",
    "علوم",
    "دراسات اجتماعية",
    "أخرى"
)

class TeacherProfileViewModel(
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherProfileUiState())
    val uiState: StateFlow<TeacherProfileUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // Load grades
            launch {
                combine(
                    gradeRepository.getGrades(),
                    studentRepository.getStudents()
                ) { gradesList, studentsList ->
                    val activeStudents = studentsList.filter { it.deletedAt == null }
                    val countsByGrade = activeStudents.groupBy { it.gradeId }.mapValues { it.value.size }
                    gradesList.map { grade ->
                        grade.copy(studentCount = countsByGrade[grade.id] ?: 0)
                    }.sortedBy { it.displayOrder }
                }.collect { updatedGrades ->
                    _uiState.update { it.copy(grades = updatedGrades) }
                }
            }

            // Fetch Teacher profile
            val teacher = teacherRepository.fetchCurrentTeacher()
            if (teacher != null) {
                val isCustom = teacher.subject != null && !PREDEFINED_SUBJECTS.contains(teacher.subject) && teacher.subject != "أخرى"
                val selectedSubj = if (isCustom) "أخرى" else (teacher.subject ?: "")

                _uiState.update {
                    it.copy(
                        id = teacher.id,
                        fullName = teacher.fullName,
                        email = teacher.email,
                        phoneNumber = teacher.phoneNumber ?: "",
                        subject = selectedSubj,
                        customSubject = if (isCustom) (teacher.subject ?: "") else "",
                        isCustomSubject = isCustom || selectedSubj == "أخرى",
                        centerName = teacher.centerName ?: "",
                        educationalStage = teacher.educationalStage ?: "",
                        avatarUrl = teacher.avatarUrl,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onFullNameChange(value: String) {
        _uiState.update { 
            it.copy(
                fullName = value,
                nameError = if (value.trim().isBlank()) "اسم المدرس إجباري ولا يمكن أن يكون فارغاً" else null
            ) 
        }
    }

    fun onPhoneNumberChange(value: String) {
        _uiState.update { it.copy(phoneNumber = value) }
    }

    fun onSubjectChange(value: String) {
        val isCustom = value == "أخرى"
        _uiState.update {
            it.copy(
                subject = value,
                isCustomSubject = isCustom
            )
        }
    }

    fun onCustomSubjectChange(value: String) {
        _uiState.update { it.copy(customSubject = value) }
    }

    fun onCenterNameChange(value: String) {
        _uiState.update { it.copy(centerName = value) }
    }

    fun onEducationalStageChange(value: String) {
        _uiState.update { it.copy(educationalStage = value) }
    }

    fun uploadAvatarFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null, successMessage = null) }
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null || bytes.isEmpty()) {
                    _uiState.update { it.copy(isUploadingAvatar = false, errorMessage = "تعذر قراءة ملف الصورة المحدد.") }
                    return@launch
                }

                if (bytes.size > 5 * 1024 * 1024) {
                    _uiState.update { it.copy(isUploadingAvatar = false, errorMessage = "حجم الصورة يتجاوز الحد المسموح به (5 ميجابايت).") }
                    return@launch
                }

                val extension = when {
                    mimeType.contains("png", ignoreCase = true) -> "png"
                    mimeType.contains("webp", ignoreCase = true) -> "webp"
                    mimeType.contains("gif", ignoreCase = true) -> "gif"
                    else -> "jpg"
                }

                val fileName = "avatar_${System.currentTimeMillis()}.$extension"
                val result = teacherRepository.uploadAvatar(bytes, fileName, mimeType)

                result.onSuccess { url ->
                    _uiState.update { 
                        it.copy(
                            avatarUrl = url,
                            isUploadingAvatar = false,
                            successMessage = "تم رفع الصورة بنجاح"
                        ) 
                    }
                }.onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isUploadingAvatar = false,
                            errorMessage = error.localizedMessage ?: "حدث خطأ أثناء رفع الصورة."
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = "حدث خطأ غير متوقع أثناء المعالجة: ${e.localizedMessage}"
                    ) 
                }
            }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null, successMessage = null) }
            val result = teacherRepository.deleteAvatar()
            result.onSuccess {
                _uiState.update { 
                    it.copy(
                        avatarUrl = null,
                        isUploadingAvatar = false,
                        successMessage = "تم حذف الصورة الشخصية"
                    ) 
                }
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = error.localizedMessage ?: "حدث خطأ أثناء حذف الصورة."
                    ) 
                }
            }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.fullName.trim().isBlank()) {
            _uiState.update { it.copy(nameError = "اسم المدرس إجباري ولا يمكن ترك الحقل فارغاً") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

            val finalSubject = if (state.subject == "أخرى") {
                state.customSubject.trim()
            } else {
                state.subject.trim()
            }

            val result = teacherRepository.updateTeacherProfile(
                fullName = state.fullName.trim(),
                phoneNumber = state.phoneNumber.trim(),
                subject = finalSubject,
                centerName = state.centerName.trim(),
                educationalStage = state.educationalStage.trim(),
                avatarUrl = state.avatarUrl
            )

            result.onSuccess {
                val newStage = state.educationalStage.trim()
                gradeRepository.ensureGradesForStage(newStage)
                gradeRepository.refreshGrades()
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        successMessage = "تم حفظ بيانات الحساب بنجاح"
                    ) 
                }
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        errorMessage = error.localizedMessage ?: "فشل حفظ بيانات الحساب، يرجى المحاولة مرة أخرى."
                    ) 
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
