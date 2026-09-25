package com.example.data.repository

import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockGradeRepository(
    initialStage: String = EducationalStages.PREPARATORY
) : GradeRepository {

    // Store all existing grades ever generated in DB (all stages)
    private val _allGrades = mutableMapOf<String, Grade>()
    private val _grades = MutableStateFlow<List<Grade>>(emptyList())

    init {
        setStage(initialStage)
    }

    fun setStage(stage: String?) {
        val names = EducationalStages.getGradeNamesForStage(stage)
        val stageGrades = names.mapIndexed { index, name ->
            val stagePrefix = when (stage) {
                EducationalStages.PRIMARY -> "primary"
                EducationalStages.SECONDARY -> "sec"
                else -> "prep"
            }
            val gradeId = "grade_${stagePrefix}_${index + 1}"
            val grade = Grade(
                id = gradeId,
                name = name,
                displayOrder = index + 1,
                studentCount = if (stage == EducationalStages.PREPARATORY || stage == null) {
                    when (index) {
                        0 -> 30
                        1 -> 28
                        2 -> 28
                        else -> 0
                    }
                } else 0
            )
            _allGrades[gradeId] = grade
            grade
        }
        // Also support legacy "grade_1", "grade_2", "grade_3" mapped to preparatory for test backwards compatibility
        if (stage == EducationalStages.PREPARATORY || stage == null) {
            _allGrades["grade_1"] = Grade(id = "grade_1", name = "الأول الإعدادي", displayOrder = 1)
            _allGrades["grade_2"] = Grade(id = "grade_2", name = "الثاني الإعدادي", displayOrder = 2)
            _allGrades["grade_3"] = Grade(id = "grade_3", name = "الثالث الإعدادي", displayOrder = 3)
        }
        _grades.value = stageGrades
    }

    override fun getGrades(): Flow<List<Grade>> = _grades.asStateFlow()

    override suspend fun getGradeById(id: String): Grade? {
        return _allGrades[id] ?: _grades.value.find { it.id == id }
    }

    override suspend fun addGrade(grade: Grade): Boolean {
        _allGrades[grade.id] = grade
        _grades.update { current -> current + grade }
        return true
    }

    override suspend fun updateGrade(grade: Grade): Boolean {
        _allGrades[grade.id] = grade
        _grades.update { current ->
            current.map { if (it.id == grade.id) grade else it }
        }
        return true
    }

    override suspend fun deleteGrade(id: String): Boolean {
        _allGrades.remove(id)
        _grades.update { current ->
            current.filterNot { it.id == id }
        }
        return true
    }

    override suspend fun ensureGradesForStage(stage: String?): List<Grade> {
        setStage(stage)
        return _grades.value
    }

    override suspend fun refreshGrades(stage: String?): List<Grade> {
        if (!stage.isNullOrBlank()) {
            setStage(stage)
        }
        return _grades.value
    }

    fun updateStudentCounts(counts: Map<String, Int>) {
        _grades.update { current ->
            current.map { grade ->
                grade.copy(studentCount = counts[grade.id] ?: 0)
            }
        }
    }
}
