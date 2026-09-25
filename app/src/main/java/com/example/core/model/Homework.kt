package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HomeworkStatus(val labelAr: String) {
    @SerialName("PENDING") PENDING("قيد الانتظار"),
    @SerialName("COMPLETED") COMPLETED("مكتمل"),
    @SerialName("NOT_COMPLETED") NOT_COMPLETED("غير مكتمل");

    companion object {
        fun fromString(value: String): HomeworkStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

@Serializable
data class SupabaseHomeworkDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("date") val date: String,
    @SerialName("title") val title: String,
    @SerialName("status") val status: String,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toHomework(teacherId: String? = null): Homework {
        return Homework(
            homeworkId = id ?: "",
            studentId = studentId,
            teacherId = teacherId ?: this.teacherId ?: "",
            date = date,
            title = title,
            status = HomeworkStatus.fromString(status),
            note = note,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

data class Homework(
    val homeworkId: String,
    val studentId: String,
    val teacherId: String = "",
    val date: String, // format "YYYY-MM-DD" e.g. "2026-09-18"
    val title: String,
    val status: HomeworkStatus,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
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
