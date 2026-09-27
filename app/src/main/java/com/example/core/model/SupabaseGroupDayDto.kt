package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseGroupDayDto(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("day_of_week") val dayOfWeek: String
)

fun GroupDay.toSupabaseDto(): SupabaseGroupDayDto = SupabaseGroupDayDto(
    teacherId = teacherId,
    groupId = groupId,
    dayOfWeek = dayOfWeek
)

fun SupabaseGroupDayDto.toDomain(): GroupDay = GroupDay(
    teacherId = teacherId,
    groupId = groupId,
    dayOfWeek = dayOfWeek
)
