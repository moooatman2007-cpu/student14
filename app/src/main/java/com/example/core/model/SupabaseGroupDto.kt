package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseGroupDto(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("grade_id") val gradeId: String,
    val name: String,
    val active: Boolean = true,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val capacity: Int? = null,
    val location: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

fun Group.toSupabaseDto(): SupabaseGroupDto = SupabaseGroupDto(
    id = id,
    teacherId = teacherId,
    gradeId = gradeId,
    name = name,
    active = active,
    startTime = startTime,
    endTime = endTime,
    capacity = capacity,
    location = location,
    createdAt = null,
    updatedAt = null
)

fun SupabaseGroupDto.toDomain(): Group = Group(
    id = id,
    teacherId = teacherId,
    gradeId = gradeId,
    name = name,
    active = active,
    startTime = startTime,
    endTime = endTime,
    capacity = capacity,
    location = location,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)
