package com.example.data.repository

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import kotlinx.coroutines.flow.Flow

interface AttendanceRepository {
    fun getAttendanceForStudent(studentId: String): Flow<List<Attendance>>
    fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>>
    suspend fun getAttendanceSummaryForStudent(studentId: String, year: Int, month: Int): AttendanceSummary
    suspend fun getTodayAttendanceCount(): Pair<Int, Int> // (presentCount, absentCount)
    fun getAllAttendanceForTeacher(): Flow<List<Attendance>>
    suspend fun recordOrUpdateAttendance(
        studentId: String,
        date: String, // "YYYY-MM-DD"
        status: AttendanceStatus,
        note: String? = null
    ): Result<Attendance>
    suspend fun recordBatchAttendance(
        date: String,
        records: List<com.example.core.model.BatchAttendanceItemDto>
    ): Result<com.example.core.model.BatchAttendanceResult>
    suspend fun deleteAttendance(attendanceId: String): Result<Unit>
    suspend fun getAttendanceByDate(studentId: String, date: String): Attendance?
}
