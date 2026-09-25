package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Teacher(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("subject") val subject: String? = null,
    @SerialName("center_name") val centerName: String? = null,
    @SerialName("educational_stage") val educationalStage: String? = null,
    @SerialName("theme_mode") val themeMode: String = "SYSTEM"
)

@Serializable
data class UpdateTeacherRequest(
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("subject") val subject: String? = null,
    @SerialName("center_name") val centerName: String? = null,
    @SerialName("educational_stage") val educationalStage: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class UpsertTeacherRequest(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("subject") val subject: String? = null,
    @SerialName("center_name") val centerName: String? = null,
    @SerialName("educational_stage") val educationalStage: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
