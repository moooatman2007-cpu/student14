package com.example.data.repository

import com.example.core.model.Student
import com.example.core.model.TeacherStats
import kotlinx.coroutines.flow.Flow

interface StudentRepository {
    fun getStudents(): Flow<List<Student>>
    fun getStudentsByGrade(gradeId: String): Flow<List<Student>>
    suspend fun getStudentById(studentId: String): Student?
    suspend fun getStudentByCode(studentCode: String): Student?
    suspend fun addStudent(
        fullName: String,
        gradeId: String,
        parentPhone: String,
        hasWhatsApp: Boolean,
        alternativePhone: String?
    ): Result<Student>
    suspend fun updateStudent(student: Student): Result<Student>
    suspend fun deleteStudent(studentId: String): Result<Unit>
    fun searchStudents(query: String, gradeId: String? = null): Flow<List<Student>>
    fun getStats(): Flow<TeacherStats>
}
