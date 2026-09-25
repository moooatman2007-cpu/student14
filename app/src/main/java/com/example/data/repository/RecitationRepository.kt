package com.example.data.repository

import com.example.core.model.Recitation
import com.example.core.model.RecitationSummary
import kotlinx.coroutines.flow.Flow

interface RecitationRepository {
    fun getRecitationsForStudent(studentId: String): Flow<List<Recitation>>
    fun getRecitationsForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Recitation>>
    suspend fun getRecitationSummaryForStudent(studentId: String, year: Int, month: Int): RecitationSummary
    suspend fun getRecitationsCountThisMonth(year: Int, month: Int): Int
    fun getAllRecitationsForTeacher(): Flow<List<Recitation>>
    suspend fun addRecitation(
        studentId: String,
        date: String,
        title: String,
        content: String,
        score: Double,
        maxScore: Double,
        note: String? = null
    ): Result<Recitation>
    suspend fun updateRecitation(recitation: Recitation): Result<Recitation>
    suspend fun deleteRecitation(recitationId: String): Result<Unit>
    suspend fun getRecitationById(recitationId: String): Recitation?
}
