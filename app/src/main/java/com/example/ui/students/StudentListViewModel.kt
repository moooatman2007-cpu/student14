package com.example.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.repository.GradeRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentListUiState(
    val searchQuery: String = "",
    val selectedGradeId: String? = null,
    val grades: List<Grade> = emptyList(),
    val gradeMap: Map<String, String> = emptyMap(),
    val students: List<Student> = emptyList(),
    val isLoading: Boolean = false,
    val totalCount: Int = 0
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StudentListViewModel(
    savedStateHandle: SavedStateHandle? = null,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository
) : ViewModel() {

    private val initialGradeId: String? = savedStateHandle?.get<String>("gradeId")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGradeId = MutableStateFlow(initialGradeId)
    val selectedGradeId: StateFlow<String?> = _selectedGradeId.asStateFlow()

    private val _uiState = MutableStateFlow(StudentListUiState(selectedGradeId = initialGradeId))
    val uiState: StateFlow<StudentListUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            gradeRepository.refreshGrades()
            (studentRepository as? SupabaseStudentRepository)?.fetchStudents()
        }

        viewModelScope.launch {
            combine(
                gradeRepository.getGrades(),
                studentRepository.getStudents()
            ) { gradesList, studentsList ->
                val activeStudents = studentsList.filter { it.deletedAt == null }
                val countsByGrade = activeStudents.groupBy { it.gradeId }.mapValues { it.value.size }
                val currentGradeMap = gradesList.associate { it.id to it.name }.toMutableMap()

                // Check for students belonging to grades not in the current stage list
                val missingGradeIds = activeStudents.map { it.gradeId }.toSet().filterNot { currentGradeMap.containsKey(it) }
                for (legacyGradeId in missingGradeIds) {
                    val legacyGrade = gradeRepository.getGradeById(legacyGradeId)
                    if (legacyGrade != null) {
                        currentGradeMap[legacyGradeId] = "${legacyGrade.name} — مرحلة سابقة"
                    } else {
                        currentGradeMap[legacyGradeId] = "مرحلة سابقة"
                    }
                }

                val sortedGrades = gradesList.map { grade ->
                    grade.copy(studentCount = countsByGrade[grade.id] ?: 0)
                }.sortedBy { it.displayOrder }

                Pair(sortedGrades, currentGradeMap)
            }.collect { (updatedGrades, fullGradeMap) ->
                _uiState.update { 
                    it.copy(
                        grades = updatedGrades,
                        gradeMap = fullGradeMap
                    ) 
                }
            }
        }

        viewModelScope.launch {
            combine(
                _searchQuery.debounce(150),
                _selectedGradeId
            ) { query, gradeId ->
                Pair(query, gradeId)
            }.flatMapLatest { (query, gradeId) ->
                studentRepository.searchStudents(query, gradeId)
            }.collect { studentsList ->
                _uiState.update {
                    it.copy(
                        students = studentsList,
                        totalCount = studentsList.size,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onGradeSelected(gradeId: String?) {
        _selectedGradeId.value = gradeId
        _uiState.update { it.copy(selectedGradeId = gradeId) }
    }

    fun setInitialGradeId(gradeId: String?) {
        if (gradeId != _selectedGradeId.value) {
            _selectedGradeId.value = gradeId
            _uiState.update { it.copy(selectedGradeId = gradeId) }
        }
    }
}
