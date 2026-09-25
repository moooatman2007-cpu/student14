package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemMonitoringAlert(
    @SerialName("id") val id: String = "",
    @SerialName("teacher_id") val teacherId: String = "",
    @SerialName("alert_type") val alertType: String = "",
    @SerialName("status") val status: String = "OPEN",
    @SerialName("message") val message: String = "",
    @SerialName("failure_count") val failureCount: Int = 0,
    @SerialName("first_detected_at") val firstDetectedAt: String? = null,
    @SerialName("last_detected_at") val lastDetectedAt: String? = null,
    @SerialName("last_notified_at") val lastNotifiedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
