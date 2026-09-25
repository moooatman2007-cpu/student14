package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND deleted_at IS NULL ORDER BY full_name ASC")
    fun getAllStudents(teacherId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND deleted_at IS NULL ORDER BY full_name ASC")
    suspend fun getAllStudentsSync(teacherId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND student_id = :studentId LIMIT 1")
    fun getStudentById(teacherId: String, studentId: String): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND student_id = :studentId LIMIT 1")
    suspend fun getStudentByIdSync(teacherId: String, studentId: String): StudentEntity?

    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND grade_id = :gradeId AND deleted_at IS NULL ORDER BY full_name ASC")
    fun getStudentsByGrade(gradeId: String, teacherId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE teacher_id = :teacherId AND student_code = :studentCode AND deleted_at IS NULL LIMIT 1")
    suspend fun getStudentByCode(teacherId: String, studentCode: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudents(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteStudentById(teacherId: String, studentId: String)

    @Query("DELETE FROM students WHERE teacher_id = :teacherId")
    suspend fun deleteStudentsByTeacher(teacherId: String)

    @Query("DELETE FROM students")
    suspend fun clearAll()
}
