package com.example.ui.add_edit_student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Grade
import com.example.core.model.Group
import com.example.core.model.Student
import com.example.data.repository.GradeRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import com.example.data.repository.TeacherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditStudentUiState(
    val isEditMode: Boolean = false,
    val studentId: String? = null,
    val existingStudentCode: String? = null,
    val fullName: String = "",
    val nameError: String? = null,
    val selectedGradeId: String = "",
    val gradeError: String? = null,
    val parentPhone: String = "",
    val phoneError: String? = null,
    val hasWhatsApp: Boolean = true,
    val alternativePhone: String = "",
    val grades: List<Grade> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: String? = null,
    val isSaving: Boolean = false,
    val createdStudent: Student? = null,
    val showSuccessDialog: Boolean = false
)

sealed class AddEditStudentEvent {
    data object StudentUpdated : AddEditStudentEvent()
    data class ShowError(val message: String) : AddEditStudentEvent()
}

class AddEditStudentViewModel(
    savedStateHandle: SavedStateHandle? = null,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val groupRepository: GroupRepository = RepositoryProvider.groupRepository
) : ViewModel() {

    private val studentIdArg: String? = savedStateHandle?.get<String>("studentId")
    private val gradeIdArg: String? = savedStateHandle?.get<String>("gradeId")

    private val _uiState = MutableStateFlow(
        AddEditStudentUiState(
            isEditMode = studentIdArg != null,
            studentId = studentIdArg,
            selectedGradeId = gradeIdArg ?: ""
        )
    )
    val uiState: StateFlow<AddEditStudentUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddEditStudentEvent>()
    val events: SharedFlow<AddEditStudentEvent> = _events.asSharedFlow()

    private var previousStageGrade: Grade? = null
    private var groupsObserveJob: Job? = null
    private var initialStudentGroupId: String? = null

    init {
        loadGrades()
        if (studentIdArg != null) {
            loadExistingStudent(studentIdArg)
        } else if (!gradeIdArg.isNullOrBlank()) {
            loadGroups(gradeIdArg, null)
        }
    }

    private fun loadGrades() {
        viewModelScope.launch {
            gradeRepository.refreshGrades()
            gradeRepository.getGrades().collect { gradesList ->
                val sorted = gradesList.sortedBy { it.displayOrder }
                _uiState.update { current ->
                    val combinedGrades = buildCombinedGradesList(sorted, previousStageGrade)
                    val nextGradeId = if (current.selectedGradeId.isBlank() && combinedGrades.isNotEmpty()) {
                        gradeIdArg ?: combinedGrades.first().id
                    } else {
                        current.selectedGradeId
                    }
                    if (nextGradeId.isNotBlank() && current.selectedGradeId != nextGradeId) {
                        loadGroups(nextGradeId, current.selectedGroupId)
                    }
                    current.copy(
                        grades = combinedGrades,
                        selectedGradeId = nextGradeId
                    )
                }
            }
        }
    }

    fun loadGroups(gradeId: String, currentAssignedGroupId: String? = _uiState.value.selectedGroupId) {
        groupsObserveJob?.cancel()
        if (gradeId.isBlank()) {
            _uiState.update { it.copy(groups = emptyList(), selectedGroupId = null) }
            return
        }

        groupsObserveJob = viewModelScope.launch {
            val teacher = teacherRepository.fetchCurrentTeacher()
            val teacherId = teacher?.id ?: com.example.data.SupabaseClientProvider.mockTeacherId ?: ""
            if (teacherId.isBlank()) return@launch

            groupRepository.observeGroupsByGrade(teacherId, gradeId).collect { groupsList ->
                // Filter: Active groups for this grade, PLUS if currentAssignedGroupId is in groupsList (even if inactive), include it
                val eligibleGroups = groupsList.filter { group ->
                    group.active || (group.id == currentAssignedGroupId)
                }

                _uiState.update { current ->
                    val isSelectedStillValid = eligibleGroups.any { it.id == current.selectedGroupId }
                    val updatedGroupId = if (isSelectedStillValid) current.selectedGroupId else null

                    current.copy(
                        groups = eligibleGroups,
                        selectedGroupId = updatedGroupId
                    )
                }
            }
        }
    }

    private fun loadExistingStudent(studentId: String) {
        viewModelScope.launch {
            val student = studentRepository.getStudentById(studentId)
            if (student != null) {
                initialStudentGroupId = student.groupId
                val existingGrade = gradeRepository.getGradeById(student.gradeId)
                if (existingGrade != null) {
                    val isAlreadyInCurrentGrades = _uiState.value.grades.any { it.id == existingGrade.id }
                    if (!isAlreadyInCurrentGrades) {
                        previousStageGrade = existingGrade.copy(name = "${existingGrade.name} — مرحلة سابقة")
                    }
                }

                _uiState.update { current ->
                    val combinedGrades = buildCombinedGradesList(current.grades, previousStageGrade)
                    current.copy(
                        fullName = student.fullName,
                        selectedGradeId = student.gradeId,
                        parentPhone = student.parentPhone,
                        hasWhatsApp = student.hasWhatsApp,
                        alternativePhone = student.alternativePhone ?: "",
                        existingStudentCode = student.studentCode,
                        selectedGroupId = student.groupId,
                        grades = combinedGrades
                    )
                }
                loadGroups(student.gradeId, student.groupId)
            }
        }
    }

    private fun buildCombinedGradesList(currentGrades: List<Grade>, legacyGrade: Grade?): List<Grade> {
        if (legacyGrade == null) return currentGrades
        val withoutLegacy = currentGrades.filterNot { it.id == legacyGrade.id }
        return listOf(legacyGrade) + withoutLegacy
    }

    fun onFullNameChange(name: String) {
        _uiState.update {
            it.copy(
                fullName = name,
                nameError = if (it.nameError != null && name.isNotBlank()) null else it.nameError
            )
        }
    }

    fun onGradeSelected(gradeId: String) {
        if (_uiState.value.selectedGradeId == gradeId) return

        _uiState.update {
            it.copy(
                selectedGradeId = gradeId,
                selectedGroupId = null,
                gradeError = null
            )
        }
        loadGroups(gradeId, null)
    }

    fun onGroupSelected(groupId: String?) {
        _uiState.update { it.copy(selectedGroupId = groupId) }
    }

    fun onParentPhoneChange(phone: String) {
        val filtered = phone.filter { it.isDigit() }.take(11)
        _uiState.update {
            it.copy(
                parentPhone = filtered,
                phoneError = if (it.phoneError != null && validatePhone(filtered)) null else it.phoneError
            )
        }
    }

    fun onWhatsAppToggle(enabled: Boolean) {
        _uiState.update { it.copy(hasWhatsApp = enabled) }
    }

    fun onAlternativePhoneChange(phone: String) {
        val filtered = phone.filter { it.isDigit() }.take(11)
        _uiState.update { it.copy(alternativePhone = filtered) }
    }

    private fun validatePhone(phone: String): Boolean {
        // Egyptian phone number check (010, 011, 012, 015 + 8 digits = 11 digits)
        return phone.length == 11 && phone.startsWith("01")
    }

    fun saveStudent() {
        if (_uiState.value.isSaving) return

        val state = _uiState.value
        var hasError = false

        val nameError = if (state.fullName.isBlank()) {
            hasError = true
            "اكتب اسم الطالب"
        } else null

        val gradeError = if (state.selectedGradeId.isBlank()) {
            hasError = true
            "اختر الصف الدراسي"
        } else null

        val phoneError = if (state.parentPhone.isBlank()) {
            hasError = true
            "اكتب رقم ولي الأمر"
        } else if (!validatePhone(state.parentPhone)) {
            hasError = true
            "تأكد من رقم الهاتف (يجب أن يبدأ بـ 01 ويتكون من 11 رقم)"
        } else null

        if (hasError) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    gradeError = gradeError,
                    phoneError = phoneError
                )
            }
            return
        }

        // Set isSaving synchronously to block subsequent clicks immediately
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val teacher = teacherRepository.fetchCurrentTeacher()
            val tId = teacher?.id ?: ""

            // Validation: Group must belong to current teacher and match selected gradeId
            if (state.selectedGroupId != null) {
                val group = groupRepository.getGroupById(tId, state.selectedGroupId)
                if (group == null || group.teacherId != tId || group.gradeId != state.selectedGradeId) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(AddEditStudentEvent.ShowError("المجموعة غير متوافقة مع الصف الدراسي."))
                    return@launch
                }
            }

            if (state.isEditMode && state.studentId != null) {
                val updatedStudent = Student(
                    studentId = state.studentId,
                    studentCode = state.existingStudentCode ?: "",
                    fullName = state.fullName,
                    gradeId = state.selectedGradeId,
                    parentPhone = state.parentPhone,
                    hasWhatsApp = state.hasWhatsApp,
                    alternativePhone = state.alternativePhone.ifBlank { null },
                    teacherId = tId,
                    groupId = state.selectedGroupId
                )
                val result = studentRepository.updateStudent(updatedStudent)
                if (result.isSuccess) {
                    groupRepository.assignStudentToGroup(tId, state.studentId, state.selectedGroupId)
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(AddEditStudentEvent.StudentUpdated)
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                    val errorMsg = result.exceptionOrNull()?.message ?: "فشل تحديث بيانات الطالب"
                    _events.emit(AddEditStudentEvent.ShowError(errorMsg))
                }
            } else {
                val result = studentRepository.addStudent(
                    fullName = state.fullName,
                    gradeId = state.selectedGradeId,
                    parentPhone = state.parentPhone,
                    hasWhatsApp = state.hasWhatsApp,
                    alternativePhone = state.alternativePhone.ifBlank { null }
                )
                if (result.isSuccess) {
                    val newStudent = result.getOrNull()
                    if (newStudent != null && state.selectedGroupId != null) {
                        groupRepository.assignStudentToGroup(tId, newStudent.studentId, state.selectedGroupId)
                    }
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            createdStudent = if (state.selectedGroupId != null && newStudent != null) newStudent.copy(groupId = state.selectedGroupId) else newStudent,
                            showSuccessDialog = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                    val errorMsg = result.exceptionOrNull()?.message ?: "فشل إضافة الطالب"
                    _events.emit(AddEditStudentEvent.ShowError(errorMsg))
                }
            }
        }
    }

    fun resetForAnotherStudent() {
        _uiState.update { current ->
            AddEditStudentUiState(
                isEditMode = false,
                studentId = null,
                existingStudentCode = null,
                fullName = "",
                nameError = null,
                selectedGradeId = current.selectedGradeId,
                gradeError = null,
                parentPhone = "",
                phoneError = null,
                hasWhatsApp = true,
                alternativePhone = "",
                grades = current.grades,
                groups = current.groups,
                selectedGroupId = null,
                isSaving = false,
                createdStudent = null,
                showSuccessDialog = false
            )
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(showSuccessDialog = false, createdStudent = null) }
    }
}
