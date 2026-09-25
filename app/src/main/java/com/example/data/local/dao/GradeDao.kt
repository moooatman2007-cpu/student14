package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.local.entity.GradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE teacher_id = :teacherId ORDER BY display_order ASC, grade_name ASC")
    fun getAllGrades(teacherId: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE teacher_id = :teacherId ORDER BY display_order ASC, grade_name ASC")
    suspend fun getAllGradesSync(teacherId: String): List<GradeEntity>

    @Query("SELECT * FROM grades WHERE teacher_id = :teacherId AND grade_id = :gradeId LIMIT 1")
    suspend fun getGradeById(teacherId: String, gradeId: String): GradeEntity?

    @Upsert
    suspend fun upsertGrades(grades: List<GradeEntity>)

    @Upsert
    suspend fun upsertGrade(grade: GradeEntity)

    @Query("DELETE FROM grades WHERE teacher_id = :teacherId AND grade_id = :gradeId")
    suspend fun deleteGrade(teacherId: String, gradeId: String)

    @Query("DELETE FROM grades WHERE teacher_id = :teacherId")
    suspend fun deleteGradesByTeacher(teacherId: String)

    @Query("DELETE FROM grades")
    suspend fun clearAll()
}
