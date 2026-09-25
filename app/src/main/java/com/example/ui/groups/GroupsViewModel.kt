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
import java.util.UUID

data class GroupsUiState(
    val isLoading: Boolean = false,
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
    val formStartTime: String = "10:00",
    val formEndTime: String = "12:00",
    val formCapacity: String = "",
    val formLocation: String = "",
    val formActive: Boolean = true,
    val formError: String? = null,
    val isSaving: Boolean = false
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

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val teacher = teacherRepository.fetchCurrentTeacher()
            if (teacher != null) {
                teacherId = teacher.id
                // Start observing groups, students and group days
                combine(
                    groupRepository.observeGroups(teacher.id),
                    studentRepository.getStudents(),
                    groupRepository.observeAllGroupDays(teacher.id)
                ) { groups, students, days ->
                    val studentCountMap = groups.associate { group ->
                        group.id to students.count { it.groupId == group.id }
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
            // Filter grades that belong to the stage, then get matching groups
            val matchingGradeIds = state.grades.filter { grade ->
                EducationalStages.isGradeMatchingStage(grade.name, state.selectedStage)
            }.map { it.id }
            list = list.filter { it.gradeId in matchingGradeIds }
        }

        _uiState.update { it.copy(filteredGroups = list) }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingGroup = null,
                formName = "",
                formGradeId = it.grades.firstOrNull()?.id ?: "",
                formDays = emptyList(),
                formStartTime = "10:00",
                formEndTime = "12:00",
                formCapacity = "",
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
        _uiState.update { it.copy(showAddEditDialog = false) }
    }

    fun onFormNameChange(name: String) {
        _uiState.update { it.copy(formName = name) }
    }

    fun onFormGradeChange(gradeId: String) {
        _uiState.update { it.copy(formGradeId = gradeId) }
    }

    fun onFormDayToggle(day: String) {
        _uiState.update { current ->
            val updated = if (current.formDays.contains(day)) {
                current.formDays - day
            } else {
                current.formDays + day
            }
            current.copy(formDays = updated)
        }
    }

    fun onFormStartTimeChange(time: String) {
        _uiState.update { it.copy(formStartTime = time) }
    }

    fun onFormEndTimeChange(time: String) {
        _uiState.update { it.copy(formEndTime = time) }
    }

    fun onFormCapacityChange(cap: String) {
        _uiState.update { it.copy(formCapacity = cap.filter { c -> c.isDigit() }) }
    }

    fun onFormLocationChange(loc: String) {
        _uiState.update { it.copy(formLocation = loc) }
    }

    fun onFormActiveChange(active: Boolean) {
        _uiState.update { it.copy(formActive = active) }
    }

    fun saveGroup() {
        val state = _uiState.value
        if (state.formName.isBlank()) {
            _uiState.update { it.copy(formError = "يرجى كتابة اسم المجموعة") }
            return
        }
        if (state.formGradeId.isBlank()) {
            _uiState.update { it.copy(formError = "يرجى اختيار الصف") }
            return
        }

        val tId = teacherId ?: return

        _uiState.update { it.copy(isSaving = true, formError = null) }

        viewModelScope.launch {
            val capacityInt = state.formCapacity.toIntOrNull()
            val locationStr = state.formLocation.ifBlank { null }
            val id = state.editingGroup?.id ?: UUID.randomUUID().toString()

            val group = Group(
                id = id,
                teacherId = tId,
                gradeId = state.formGradeId,
                name = state.formName,
                active = state.formActive,
                startTime = state.formStartTime,
                endTime = state.formEndTime,
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
                groupRepository.replaceGroupDays(tId, id, state.formDays)
                _uiState.update { it.copy(showAddEditDialog = false, isSaving = false) }
            } else {
                _uiState.update { it.copy(formError = "فشل في حفظ المجموعة: ${result.exceptionOrNull()?.message}", isSaving = false) }
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
}
