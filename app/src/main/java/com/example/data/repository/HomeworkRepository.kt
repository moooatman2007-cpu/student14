package com.example.data.repository

import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import kotlinx.coroutines.flow.Flow

interface HomeworkRepository {
    fun getHomeworkForStudent(studentId: String): Flow<List<Homework>>
    fun getHomeworkForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Homework>>
    fun getHomeworkForTeacher(): Flow<List<Homework>>
    suspend fun addHomework(
        studentId: String,
        date: String,
        title: String,
        status: HomeworkStatus,
        note: String? = null
    ): Result<Homework>
    suspend fun updateHomework(homework: Homework): Result<Homework>
    suspend fun deleteHomework(homeworkId: String): Result<Unit>
    suspend fun getHomeworkById(homeworkId: String): Homework?
}
