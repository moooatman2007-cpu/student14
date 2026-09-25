package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.RecitationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecitationDao {
    @Query("SELECT * FROM recitations WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    fun getRecitationsByStudent(teacherId: String, studentId: String): Flow<List<RecitationEntity>>

    @Query("SELECT * FROM recitations WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    suspend fun getRecitationsByStudentSync(teacherId: String, studentId: String): List<RecitationEntity>

    @Query("SELECT * FROM recitations WHERE teacher_id = :teacherId AND student_id = :studentId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getRecitationsByStudentAndRange(teacherId: String, studentId: String, startDate: String, endDate: String): Flow<List<RecitationEntity>>

    @Query("SELECT * FROM recitations WHERE teacher_id = :teacherId AND recitation_id = :recitationId LIMIT 1")
    suspend fun getRecitationByIdSync(teacherId: String, recitationId: String): RecitationEntity?

    @Query("SELECT * FROM recitations WHERE teacher_id = :teacherId ORDER BY date DESC")
    fun getAllRecitationsByTeacher(teacherId: String): Flow<List<RecitationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecitations(recitations: List<RecitationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleRecitation(recitation: RecitationEntity)

    @Query("DELETE FROM recitations WHERE teacher_id = :teacherId AND recitation_id = :recitationId")
    suspend fun deleteById(teacherId: String, recitationId: String)

    @Query("DELETE FROM recitations WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteByStudentId(teacherId: String, studentId: String)

    @Query("DELETE FROM recitations WHERE teacher_id = :teacherId")
    suspend fun deleteRecitationsByTeacher(teacherId: String)

    @Query("DELETE FROM recitations")
    suspend fun clearAll()
}
