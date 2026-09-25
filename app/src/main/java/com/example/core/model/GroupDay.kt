package com.example.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GroupDay(
    val teacherId: String,
    val groupId: String,
    val dayOfWeek: String
)
