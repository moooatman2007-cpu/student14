package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Student(
    @SerialName("id") val studentId: String = "",
    @SerialName("student_code") val studentCode: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("grade_id") val gradeId: String = "",
    @SerialName("parent_phone") val parentPhone: String = "",
    @SerialName("has_whatsapp") val hasWhatsApp: Boolean = true,
    @SerialName("alternative_phone") val alternativePhone: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAtRaw: String? = null,
    @SerialName("updated_at") val updatedAtRaw: String? = null
) {
    val createdAt: Long
        get() = parseIsoToMillis(createdAtRaw)

    val updatedAt: Long
        get() = parseIsoToMillis(updatedAtRaw)
}

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

@Serializable
data class InsertStudentRequest(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("grade_id") val gradeId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("parent_phone") val parentPhone: String,
    @SerialName("has_whatsapp") val hasWhatsApp: Boolean = true,
    @SerialName("alternative_phone") val alternativePhone: String? = null
)

@Serializable
data class UpdateStudentRequest(
    @SerialName("grade_id") val gradeId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("parent_phone") val parentPhone: String,
    @SerialName("has_whatsapp") val hasWhatsApp: Boolean,
    @SerialName("alternative_phone") val alternativePhone: String? = null
)

@Serializable
data class SoftDeleteStudentRequest(
    @SerialName("deleted_at") val deletedAt: String
)
