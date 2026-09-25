package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AttendanceStatus(val labelAr: String) {
    @SerialName("PRESENT") PRESENT("حاضر"),
    @SerialName("ABSENT") ABSENT("غائب"),
    @SerialName("LATE") LATE("متأخر"),
    @SerialName("EXCUSED") EXCUSED("غياب بعذر");

    companion object {
        fun fromString(value: String): AttendanceStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PRESENT
        }
    }
}

@Serializable
data class SupabaseAttendanceDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("date") val date: String,
    @SerialName("status") val status: String,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toAttendance(teacherId: String? = null): Attendance {
        return Attendance(
            attendanceId = id ?: "",
            studentId = studentId,
            teacherId = teacherId ?: this.teacherId ?: "",
            groupId = groupId,
            date = date,
            status = AttendanceStatus.fromString(status),
            note = note,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

@Serializable
data class UpsertAttendanceRequest(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("date") val date: String,
    @SerialName("status") val status: String,
    @SerialName("note") val note: String? = null
)

data class Attendance(
    val attendanceId: String,
    val studentId: String,
    val teacherId: String = "",
    val groupId: String? = null,
    val date: String, // format "YYYY-MM-DD" e.g. "2026-09-18"
    val status: AttendanceStatus,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AttendanceSummary(
    val totalDays: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val excusedCount: Int = 0,
    val attendanceRate: Float = 0f // 0 - 100%
)

@Serializable
data class BatchAttendanceItemDto(
    @SerialName("student_id") val studentId: String,
    @SerialName("status") val status: String,
    @SerialName("note") val note: String? = null
)

@Serializable
data class BatchAttendanceResultDto(
    @SerialName("total") val total: Int = 0,
    @SerialName("present_count") val presentCount: Int = 0,
    @SerialName("absent_count") val absentCount: Int = 0,
    @SerialName("late_count") val lateCount: Int = 0,
    @SerialName("excused_count") val excusedCount: Int = 0,
    @SerialName("date") val date: String = ""
) {
    fun toBatchAttendanceResult(): BatchAttendanceResult {
        return BatchAttendanceResult(
            total = total,
            presentCount = presentCount,
            absentCount = absentCount,
            lateCount = lateCount,
            excusedCount = excusedCount,
            date = date
        )
    }
}

data class BatchAttendanceResult(
    val total: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val excusedCount: Int = 0,
    val date: String = ""
)

private fun parseIsoToMillis(isoString: String?): Long {
    if (isoString.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
