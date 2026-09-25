package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseExamDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("date") val date: String,
    @SerialName("exam_name") val examName: String,
    @SerialName("subject") val subject: String? = null,
    @SerialName("score") val score: Double,
    @SerialName("max_score") val maxScore: Double = 100.0,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toExam(teacherId: String? = null): Exam {
        return Exam(
            examId = id ?: "",
            studentId = studentId,
            teacherId = teacherId ?: this.teacherId ?: "",
            date = date,
            examName = examName,
            subject = subject,
            score = score,
            maxScore = maxScore,
            note = note,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

data class Exam(
    val examId: String,
    val studentId: String,
    val teacherId: String = "",
    val date: String, // format "YYYY-MM-DD" e.g. "2026-09-18"
    val examName: String, // e.g. "امتحان منتصف الفصل"
    val subject: String? = null, // e.g. "القرآن الكريم والتجويد"
    val score: Double, // e.g. 85.0
    val maxScore: Double = 100.0, // e.g. 100.0
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ExamSummary(
    val totalCount: Int = 0,
    val averageScore: Double = 0.0,
    val averagePercentage: Float = 0f,
    val highestPercentage: Float = 0f,
    val lowestPercentage: Float = 0f
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
