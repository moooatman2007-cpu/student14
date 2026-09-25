package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseLessonPaymentDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("year") val year: Int,
    @SerialName("month") val month: Int,
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toLessonPayment(): LessonPayment {
        return LessonPayment(
            paymentId = id ?: "",
            studentId = studentId,
            teacherId = teacherId ?: "",
            year = year,
            month = month,
            amount = amount,
            isPaid = isPaid,
            paidAt = paidAt,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

@Serializable
data class UpsertLessonPaymentRequest(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("year") val year: Int,
    @SerialName("month") val month: Int,
    @SerialName("amount") val amount: Double,
    @SerialName("is_paid") val isPaid: Boolean,
    @SerialName("paid_at") val paidAt: String? = null
)

data class LessonPayment(
    val paymentId: String = "",
    val studentId: String,
    val teacherId: String = "",
    val year: Int,
    val month: Int,
    val amount: Double = 0.0,
    val isPaid: Boolean = false,
    val paidAt: String? = null,
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
