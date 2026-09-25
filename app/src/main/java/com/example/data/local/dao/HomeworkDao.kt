package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.HomeworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    fun getHomeworkByStudent(teacherId: String, studentId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    suspend fun getHomeworkByStudentSync(teacherId: String, studentId: String): List<HomeworkEntity>

    @Query("SELECT * FROM homework WHERE teacher_id = :teacherId ORDER BY date DESC")
    fun getAllHomeworkByTeacher(teacherId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE teacher_id = :teacherId AND homework_id = :homeworkId LIMIT 1")
    suspend fun getHomeworkByIdSync(teacherId: String, homeworkId: String): HomeworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomeworkList(homework: List<HomeworkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleHomework(homework: HomeworkEntity)

    @Query("DELETE FROM homework WHERE teacher_id = :teacherId AND homework_id = :homeworkId")
    suspend fun deleteById(teacherId: String, homeworkId: String)

    @Query("DELETE FROM homework WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteByStudentId(teacherId: String, studentId: String)

    @Query("DELETE FROM homework WHERE teacher_id = :teacherId")
    suspend fun deleteHomeworkByTeacher(teacherId: String)

    @Query("DELETE FROM homework")
    suspend fun clearAll()
}
