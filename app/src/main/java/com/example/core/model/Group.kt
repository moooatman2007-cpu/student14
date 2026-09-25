package com.example.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val teacherId: String,
    val gradeId: String,
    val name: String,
    val active: Boolean = true,
    val startTime: String,
    val endTime: String,
    val capacity: Int? = null,
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
