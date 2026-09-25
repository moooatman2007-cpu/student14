package com.example.data.repository

import com.example.core.model.Grade
import kotlinx.coroutines.flow.Flow

interface GradeRepository {
    fun getGrades(): Flow<List<Grade>>
    suspend fun getGradeById(id: String): Grade?
    suspend fun addGrade(grade: Grade): Boolean
    suspend fun updateGrade(grade: Grade): Boolean
    suspend fun deleteGrade(id: String): Boolean
    suspend fun ensureGradesForStage(stage: String?): List<Grade> = emptyList()
    suspend fun refreshGrades(stage: String? = null): List<Grade> = emptyList()
}
