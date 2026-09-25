package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseGroupDto(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("grade_id") val gradeId: String,
    val name: String,
    val active: Boolean,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val capacity: Int?,
    val location: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
