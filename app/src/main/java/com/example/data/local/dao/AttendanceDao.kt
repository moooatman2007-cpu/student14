package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    fun getAttendanceByStudent(teacherId: String, studentId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY date DESC")
    suspend fun getAttendanceByStudentSync(teacherId: String, studentId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId AND student_id = :studentId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getAttendanceByStudentAndRange(teacherId: String, studentId: String, startDate: String, endDate: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId AND student_id = :studentId AND date = :date LIMIT 1")
    suspend fun getAttendanceByDateSync(teacherId: String, studentId: String, date: String): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId AND date = :date")
    fun getAttendanceByDate(teacherId: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE teacher_id = :teacherId ORDER BY date DESC")
    fun getAllAttendanceByTeacher(teacherId: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(attendance: List<AttendanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleAttendance(attendance: AttendanceEntity)

    @Query("DELETE FROM attendance WHERE teacher_id = :teacherId AND attendance_id = :attendanceId")
    suspend fun deleteAttendanceById(teacherId: String, attendanceId: String)

    @Query("DELETE FROM attendance WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteByStudentId(teacherId: String, studentId: String)

    @Query("DELETE FROM attendance WHERE teacher_id = :teacherId")
    suspend fun deleteAttendanceByTeacher(teacherId: String)

    @Query("DELETE FROM attendance")
    suspend fun clearAll()
}
