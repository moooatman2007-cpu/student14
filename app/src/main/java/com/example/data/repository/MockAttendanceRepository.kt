package com.example.data.repository

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

class MockAttendanceRepository : AttendanceRepository {

    private val _attendances = MutableStateFlow<List<Attendance>>(emptyList())

    init {
        seedInitialAttendances()
    }

    private fun seedInitialAttendances() {
        val initialList = mutableListOf<Attendance>()
        // Seed attendance for September 2026 (2026-09-01 to 2026-09-18)
        val studentIds = (1..86).map { "s_" + String.format("%02d", it) }
        
        for (studentId in studentIds) {
            val studentNum = studentId.substringAfter("s_").toIntOrNull() ?: 1
            // Generate realistic records for 14 school days in September 2026
            val days = listOf("01", "02", "03", "06", "07", "08", "09", "10", "13", "14", "15", "16", "17", "18")
            for ((dayIdx, day) in days.withIndex()) {
                val date = "2026-09-$day"
                val status = when {
                    (studentNum + dayIdx) % 17 == 0 -> AttendanceStatus.ABSENT
                    (studentNum + dayIdx) % 11 == 0 -> AttendanceStatus.LATE
                    (studentNum + dayIdx) % 23 == 0 -> AttendanceStatus.EXCUSED
                    else -> AttendanceStatus.PRESENT
                }
                val note = when (status) {
                    AttendanceStatus.LATE -> "تأخر 15 دقيقة"
                    AttendanceStatus.EXCUSED -> "عذر طبي مقدم من ولي الأمر"
                    AttendanceStatus.ABSENT -> if (dayIdx % 2 == 0) "غياب بدون إخطار" else null
                    AttendanceStatus.PRESENT -> null
                }

                initialList.add(
                    Attendance(
                        attendanceId = "att_${studentId}_$date",
                        studentId = studentId,
                        date = date,
                        status = status,
                        note = note,
                        createdAt = 1726650000000L,
                        updatedAt = 1726650000000L
                    )
                )
            }
        }
        _attendances.value = initialList
    }

    override fun getAttendanceForStudent(studentId: String): Flow<List<Attendance>> {
        return _attendances.map { list ->
            list.filter { it.studentId == studentId }.sortedByDescending { it.date }
        }
    }

    override fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>> {
        val monthPrefix = String.format("%04d-%02d", year, month)
        return _attendances.map { list ->
            list.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
                .sortedByDescending { it.date }
        }
    }

    override suspend fun getAttendanceSummaryForStudent(studentId: String, year: Int, month: Int): AttendanceSummary {
        val monthPrefix = String.format("%04d-%02d", year, month)
        val records = _attendances.value.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
        
        val totalDays = records.size
        if (totalDays == 0) return AttendanceSummary()

        val present = records.count { it.status == AttendanceStatus.PRESENT }
        val absent = records.count { it.status == AttendanceStatus.ABSENT }
        val late = records.count { it.status == AttendanceStatus.LATE }
        val excused = records.count { it.status == AttendanceStatus.EXCUSED }

        val rate = (present.toFloat() / totalDays.toFloat()) * 100f

        return AttendanceSummary(
            totalDays = totalDays,
            presentCount = present,
            absentCount = absent,
            lateCount = late,
            excusedCount = excused,
            attendanceRate = rate
        )
    }

    override suspend fun getTodayAttendanceCount(): Pair<Int, Int> {
        val today = "2026-09-18"
        val todayRecords = _attendances.value.filter { it.date == today }
        val presentCount = todayRecords.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val absentCount = todayRecords.count { it.status == AttendanceStatus.ABSENT || it.status == AttendanceStatus.EXCUSED }
        return Pair(presentCount, absentCount)
    }

    override suspend fun recordOrUpdateAttendance(
        studentId: String,
        date: String,
        status: AttendanceStatus,
        note: String?
    ): Result<Attendance> {
        var updatedAttendance: Attendance? = null
        _attendances.update { list ->
            val existingIndex = list.indexOfFirst { it.studentId == studentId && it.date == date }
            if (existingIndex >= 0) {
                val existing = list[existingIndex]
                val modified = existing.copy(
                    status = status,
                    note = note,
                    updatedAt = System.currentTimeMillis()
                )
                updatedAttendance = modified
                list.toMutableList().apply { set(existingIndex, modified) }
            } else {
                val newRecord = Attendance(
                    attendanceId = "att_" + UUID.randomUUID().toString().take(8),
                    studentId = studentId,
                    date = date,
                    status = status,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                updatedAttendance = newRecord
                listOf(newRecord) + list
            }
        }
        return Result.success(updatedAttendance!!)
    }

    override suspend fun recordBatchAttendance(
        date: String,
        records: List<com.example.core.model.BatchAttendanceItemDto>
    ): Result<com.example.core.model.BatchAttendanceResult> {
        var presentCount = 0
        var absentCount = 0
        var lateCount = 0
        var excusedCount = 0

        _attendances.update { currentList ->
            val updated = currentList.toMutableList()
            for (rec in records) {
                val status = AttendanceStatus.fromString(rec.status)
                when (status) {
                    AttendanceStatus.PRESENT -> presentCount++
                    AttendanceStatus.ABSENT -> absentCount++
                    AttendanceStatus.LATE -> lateCount++
                    AttendanceStatus.EXCUSED -> excusedCount++
                }
                val existingIndex = updated.indexOfFirst { it.studentId == rec.studentId && it.date == date }
                val item = Attendance(
                    attendanceId = if (existingIndex >= 0) updated[existingIndex].attendanceId else "att_${rec.studentId}_$date",
                    studentId = rec.studentId,
                    date = date,
                    status = status,
                    note = rec.note,
                    createdAt = if (existingIndex >= 0) updated[existingIndex].createdAt else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (existingIndex >= 0) {
                    updated[existingIndex] = item
                } else {
                    updated.add(item)
                }
            }
            updated
        }

        return Result.success(
            com.example.core.model.BatchAttendanceResult(
                total = records.size,
                presentCount = presentCount,
                absentCount = absentCount,
                lateCount = lateCount,
                excusedCount = excusedCount,
                date = date
            )
        )
    }

    override suspend fun deleteAttendance(attendanceId: String): Result<Unit> {
        _attendances.update { list ->
            list.filterNot { it.attendanceId == attendanceId }
        }
        return Result.success(Unit)
    }

    override suspend fun getAttendanceByDate(studentId: String, date: String): Attendance? {
        return _attendances.value.find { it.studentId == studentId && it.date == date }
    }

    override fun getAllAttendanceForTeacher(): Flow<List<Attendance>> {
        return _attendances.asStateFlow()
    }
}
