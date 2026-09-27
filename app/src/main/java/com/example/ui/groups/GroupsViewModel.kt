package com.example.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Group
import com.example.core.model.GroupDay
import com.example.data.repository.GradeRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.TeacherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

data class GroupsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val groups: List<Group> = emptyList(),
    val filteredGroups: List<Group> = emptyList(),
    val groupStudentCounts: Map<String, Int> = emptyMap(),
    val groupDays: Map<String, List<String>> = emptyMap(),
    val grades: List<Grade> = emptyList(),
    val selectedStage: String? = null,
    val selectedGradeId: String? = null,
    
    // Create/Edit Group form state
    val showAddEditDialog: Boolean = false,
    val editingGroup: Group? = null,
    val formName: String = "",
    val formGradeId: String = "",
    val formDays: List<String> = emptyList(),
    val formStartTime: String = "13:00",
    val formEndTime: String = "14:00",
    val formCapacity: String = "",
    val formLocation: String = "",
    val formActive: Boolean = true,
    val formError: String? = null,
    val isSaving: Boolean = false,

    // Delete group state
    val groupToDelete: Group? = null,
    val isDeleting: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

class GroupsViewModel(
    private val groupRepository: GroupRepository = RepositoryProvider.groupRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val studentRepository: com.example.data.repository.StudentRepository = RepositoryProvider.studentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private var teacherId: String? = null

    init {
        loadData()
    }

    fun refresh() {
        val tId = teacherId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            groupRepository.refreshGroups(tId)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val teacher = teacherRepository.fetchCurrentTeacher()
            if (teacher != null) {
                teacherId = teacher.id

                // Sync remote groups on start
                launch {
                    groupRepository.refreshGroups(teacher.id)
                }

                // Observe groups, students and group days
                combine(
                    groupRepository.observeGroups(teacher.id),
                    studentRepository.getStudents(),
                    groupRepository.observeAllGroupDays(teacher.id)
                ) { groups, students, days ->
                    val studentCountMap = groups.associate { group ->
                        group.id to students.count { it.groupId == group.id && it.deletedAt == null }
                    }
                    val daysMap = days.groupBy { it.groupId }.mapValues { entry ->
                        entry.value.map { it.dayOfWeek }
                    }
                    Triple(groups, studentCountMap, daysMap)
                }.collectLatest { (groups, studentCounts, daysMap) ->
                    _uiState.update { current ->
                        current.copy(
                            groups = groups,
                            groupStudentCounts = studentCounts,
                            groupDays = daysMap,
                            isLoading = false
                        )
                    }
                    filterGroups()
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // Get grades list
        viewModelScope.launch {
            gradeRepository.getGrades().collectLatest { gradesList ->
                _uiState.update { it.copy(grades = gradesList) }
            }
        }
    }

    fun selectStage(stage: String?) {
        _uiState.update { it.copy(selectedStage = stage, selectedGradeId = null) }
        filterGroups()
    }

    fun selectGrade(gradeId: String?) {
        _uiState.update { it.copy(selectedGradeId = gradeId) }
        filterGroups()
    }

    private fun filterGroups() {
        val state = _uiState.value
        var list = state.groups

        if (state.selectedGradeId != null) {
            list = list.filter { it.gradeId == state.selectedGradeId }
        } else if (state.selectedStage != null) {
            val matchingGradeIds = state.grades.filter { grade ->
                EducationalStages.isGradeMatchingStage(grade.name, state.selectedStage)
            }.map { it.id }
            list = list.filter { it.gradeId in matchingGradeIds }
        }

        _uiState.update { it.copy(filteredGroups = list) }
    }

    fun openAddDialog() {
        val defaultGrade = _uiState.value.selectedGradeId ?: _uiState.value.grades.firstOrNull()?.id ?: ""
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingGroup = null,
                formName = "",
                formGradeId = defaultGrade,
                formDays = listOf("السبت"),
                formStartTime = "13:00",
                formEndTime = "14:00",
                formCapacity = "20",
                formLocation = "",
                formActive = true,
                formError = null
            )
        }
    }

    fun openEditDialog(group: Group) {
        val days = _uiState.value.groupDays[group.id] ?: emptyList()
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingGroup = group,
                formName = group.name,
                formGradeId = group.gradeId,
                formDays = days,
                formStartTime = group.startTime,
                formEndTime = group.endTime,
                formCapacity = group.capacity?.toString() ?: "",
                formLocation = group.location ?: "",
                formActive = group.active,
                formError = null
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(showAddEditDialog = false, formError = null) }
    }

    fun onFormNameChange(name: String) {
        _uiState.update { it.copy(formName = name, formError = null) }
    }

    fun onFormGradeChange(gradeId: String) {
        _uiState.update { it.copy(formGradeId = gradeId, formError = null) }
    }

    fun onFormDayToggle(day: String) {
        _uiState.update { current ->
            val updated = if (current.formDays.contains(day)) {
                current.formDays - day
            } else {
                current.formDays + day
            }
            current.copy(formDays = updated, formError = null)
        }
    }

    fun onFormStartTimeChange(time: String) {
        _uiState.update { it.copy(formStartTime = time, formError = null) }
    }

    fun onFormEndTimeChange(time: String) {
        _uiState.update { it.copy(formEndTime = time, formError = null) }
    }

    fun onFormCapacityChange(cap: String) {
        _uiState.update { it.copy(formCapacity = cap.filter { c -> c.isDigit() }, formError = null) }
    }

    fun onFormLocationChange(loc: String) {
        _uiState.update { it.copy(formLocation = loc, formError = null) }
    }

    fun onFormActiveChange(active: Boolean) {
        _uiState.update { it.copy(formActive = active) }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }

    fun requestDeleteGroup(group: Group) {
        _uiState.update { it.copy(groupToDelete = group) }
    }

    fun dismissDeleteGroup() {
        _uiState.update { it.copy(groupToDelete = null) }
    }

    fun confirmDeleteGroup() {
        val group = _uiState.value.groupToDelete ?: return
        val tId = teacherId ?: return

        _uiState.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            val result = groupRepository.deleteGroup(tId, group.id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        groupToDelete = null,
                        actionMessage = "تم حذف مجموعة \"${group.name}\" بنجاح."
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "فشل في حذف المجموعة."
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        groupToDelete = null,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    fun saveGroup() {
        val state = _uiState.value
        val trimmedName = state.formName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(formError = "يرجى كتابة اسم المجموعة") }
            return
        }
        if (state.formGradeId.isBlank() || !state.grades.any { it.id == state.formGradeId }) {
            _uiState.update { it.copy(formError = "يرجى اختيار صف دراسي صحيح تابع لك") }
            return
        }
        if (state.formDays.isEmpty()) {
            _uiState.update { it.copy(formError = "يرجى اختيار يوم واحد على الأقل للمجموعة") }
            return
        }

        // Validate time
        val timeError = validateTimes(state.formStartTime.trim(), state.formEndTime.trim())
        if (timeError != null) {
            _uiState.update { it.copy(formError = timeError) }
            return
        }

        val tId = teacherId ?: run {
            _uiState.update { it.copy(formError = "انتهت الجلسة، يرجى إعادة تسجيل الدخول") }
            return
        }

        _uiState.update { it.copy(isSaving = true, formError = null) }

        viewModelScope.launch {
            val capacityInt = state.formCapacity.toIntOrNull()?.takeIf { it > 0 }
            val locationStr = state.formLocation.trim().ifBlank { null }
            val id = state.editingGroup?.id ?: UUID.randomUUID().toString()

            val group = Group(
                id = id,
                teacherId = tId,
                gradeId = state.formGradeId,
                name = trimmedName,
                active = state.formActive,
                startTime = state.formStartTime.trim(),
                endTime = state.formEndTime.trim(),
                capacity = capacityInt,
                location = locationStr
            )

            val result = if (state.editingGroup == null) {
                groupRepository.createGroup(group)
            } else {
                groupRepository.updateGroup(group)
            }

            if (result.isSuccess) {
                // Save/replace group days
                val daysResult = groupRepository.replaceGroupDays(tId, id, state.formDays)
                if (daysResult.isSuccess) {
                    _uiState.update {
                        it.copy(
                            showAddEditDialog = false,
                            isSaving = false,
                            actionMessage = if (state.editingGroup == null) "تمت إضافة المجموعة بنجاح" else "تم حفظ تعديلات المجموعة بنجاح"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            showAddEditDialog = false,
                            isSaving = false,
                            actionMessage = "تم حفظ المجموعة مع تعذر حفظ بعض الأيام."
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        formError = "فشل في حفظ المجموعة: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun toggleGroupActive(group: Group) {
        val tId = teacherId ?: return
        viewModelScope.launch {
            if (group.active) {
                groupRepository.deactivateGroup(tId, group.id)
            } else {
                groupRepository.updateGroup(group.copy(active = true))
            }
        }
    }

    private fun validateTimes(startStr: String, endStr: String): String? {
        if (startStr.isBlank() || endStr.isBlank()) {
            return "يرجى تحديد وقت البدء ووقت الانتهاء"
        }

        val startMinutes = parseTimeToMinutes(startStr)
        val endMinutes = parseTimeToMinutes(endStr)

        if (startMinutes == null || endMinutes == null) {
            // If custom text cannot be parsed to standard time, require non-blank
            return null
        }

        if (endMinutes <= startMinutes) {
            return "وقت نهاية الحصة يجب أن يكون بعد وقت البدء"
        }

        return null
    }

    private fun parseTimeToMinutes(timeStr: String): Int? {
        val trimmed = timeStr.trim().uppercase(Locale.ENGLISH)
        val formats = listOf(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("hh:mm a"),
            DateTimeFormatter.ofPattern("h:mma"),
            DateTimeFormatter.ofPattern("hh:mma")
        )

        for (formatter in formats) {
            try {
                val time = LocalTime.parse(trimmed, formatter)
                return time.hour * 60 + time.minute
            } catch (_: DateTimeParseException) {}
        }

        // Try manual colon split e.g. "1:00"
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val h = parts[0].filter { it.isDigit() }.toIntOrNull()
            val m = parts[1].filter { it.isDigit() }.toIntOrNull()
            if (h != null && m != null) {
                return h * 60 + m
            }
        }

        return null
    }
}
