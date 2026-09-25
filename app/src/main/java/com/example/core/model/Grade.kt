package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Grade(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("display_order") val displayOrder: Int = 1,
    @SerialName("student_count") val studentCount: Int = 0,
    @SerialName("teacher_id") val teacherId: String? = null
)

