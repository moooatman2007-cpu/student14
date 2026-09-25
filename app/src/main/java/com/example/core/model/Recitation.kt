package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseRecitationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("date") val date: String,
    @SerialName("title") val title: String,
    @SerialName("content") val content: String,
    @SerialName("score") val score: Double,
    @SerialName("max_score") val maxScore: Double = 10.0,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toRecitation(teacherId: String? = null): Recitation {
        return Recitation(
            recitationId = id ?: "",
            studentId = studentId,
            teacherId = teacherId ?: this.teacherId ?: "",
            date = date,
            title = title,
            content = content,
            score = score,
            maxScore = maxScore,
            note = note,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

data class Recitation(
    val recitationId: String,
    val studentId: String,
    val teacherId: String = "",
    val date: String, // format "YYYY-MM-DD" e.g. "2026-09-18"
    val title: String, // e.g. "سورة البقرة"
    val content: String, // e.g. "من الآية 1 إلى 20"
    val score: Double, // e.g. 8.5
    val maxScore: Double = 10.0, // e.g. 10.0
    val note: String? = null, // e.g. "ممتاز مع مراعاة أحكام التجويد"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RecitationSummary(
    val totalCount: Int = 0,
    val averageScore: Double = 0.0,
    val averageMaxScore: Double = 10.0,
    val averagePercentage: Float = 0f
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
