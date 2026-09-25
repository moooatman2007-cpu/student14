package com.example.data.repository

import com.example.core.model.Exam
import com.example.core.model.ExamSummary
import kotlinx.coroutines.flow.Flow

interface ExamRepository {
    fun getExamsForStudent(studentId: String): Flow<List<Exam>>
    fun getExamsForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Exam>>
    suspend fun getExamSummaryForStudent(studentId: String, year: Int, month: Int): ExamSummary
    suspend fun getExamsCountThisMonth(year: Int, month: Int): Int
    fun getAllExamsForTeacher(): Flow<List<Exam>>
    suspend fun addExam(
        studentId: String,
        date: String,
        examName: String,
        subject: String?,
        score: Double,
        maxScore: Double,
        note: String? = null
    ): Result<Exam>
    suspend fun updateExam(exam: Exam): Result<Exam>
    suspend fun deleteExam(examId: String): Result<Unit>
    suspend fun getExamById(examId: String): Exam?
}
