package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    fun getExamsByStudent(teacherId: String, studentId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    suspend fun getExamsByStudentSync(teacherId: String, studentId: String): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE teacher_id = :teacherId AND student_id = :studentId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExamsByStudentAndRange(teacherId: String, studentId: String, startDate: String, endDate: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE teacher_id = :teacherId AND exam_id = :examId LIMIT 1")
    suspend fun getExamByIdSync(teacherId: String, examId: String): ExamEntity?

    @Query("SELECT * FROM exams WHERE teacher_id = :teacherId ORDER BY date DESC")
    fun getAllExamsByTeacher(teacherId: String): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleExam(exam: ExamEntity)

    @Query("DELETE FROM exams WHERE teacher_id = :teacherId AND exam_id = :examId")
    suspend fun deleteById(teacherId: String, examId: String)

    @Query("DELETE FROM exams WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteByStudentId(teacherId: String, studentId: String)

    @Query("DELETE FROM exams WHERE teacher_id = :teacherId")
    suspend fun deleteExamsByTeacher(teacherId: String)

    @Query("DELETE FROM exams")
    suspend fun clearAll()
}
