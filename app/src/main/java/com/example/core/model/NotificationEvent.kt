package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Supported notification event types in Start Lesson flow.
 */
@Serializable
enum class NotificationEventType {
    @SerialName("ABSENCE")
    ABSENCE,

    @SerialName("RECITATION")
    RECITATION,

    @SerialName("HOMEWORK")
    HOMEWORK,

    @SerialName("EXAM")
    EXAM;

    val tableName: String
        get() = when (this) {
            ABSENCE -> "notification_events"
            RECITATION -> "recitation_notification_events"
            HOMEWORK -> "homework_notification_events"
            EXAM -> "exam_notification_events"
        }

    val sourceColumnName: String
        get() = when (this) {
            ABSENCE -> "attendance_id"
            RECITATION -> "recitation_id"
            HOMEWORK -> "homework_id"
            EXAM -> "exam_id"
        }
}

/**
 * Clean domain representation of a lesson notification event.
 */
sealed interface NotificationEvent {
    val id: String
    val teacherId: String
    val studentId: String
    val sourceId: String
    val eventType: NotificationEventType
    val status: String
    val attempts: Int

    data class Absence(
        override val id: String = UUID.randomUUID().toString(),
        override val teacherId: String,
        override val studentId: String,
        val attendanceId: String,
        override val status: String = "PENDING",
        override val attempts: Int = 0
    ) : NotificationEvent {
        override val sourceId: String get() = attendanceId
        override val eventType: NotificationEventType get() = NotificationEventType.ABSENCE
    }

    data class Recitation(
        override val id: String = UUID.randomUUID().toString(),
        override val teacherId: String,
        override val studentId: String,
        val recitationId: String,
        override val status: String = "PENDING",
        override val attempts: Int = 0
    ) : NotificationEvent {
        override val sourceId: String get() = recitationId
        override val eventType: NotificationEventType get() = NotificationEventType.RECITATION
    }

    data class Homework(
        override val id: String = UUID.randomUUID().toString(),
        override val teacherId: String,
        override val studentId: String,
        val homeworkId: String,
        override val status: String = "PENDING",
        override val attempts: Int = 0
    ) : NotificationEvent {
        override val sourceId: String get() = homeworkId
        override val eventType: NotificationEventType get() = NotificationEventType.HOMEWORK
    }

    data class Exam(
        override val id: String = UUID.randomUUID().toString(),
        override val teacherId: String,
        override val studentId: String,
        val examId: String,
        override val status: String = "PENDING",
        override val attempts: Int = 0
    ) : NotificationEvent {
        override val sourceId: String get() = examId
        override val eventType: NotificationEventType get() = NotificationEventType.EXAM
    }
}

// ----------------------------------------------------------------------------
// Typed Insert DTOs for Supabase PostgREST (Android -> Supabase)
// ----------------------------------------------------------------------------

@Serializable
data class AbsenceNotificationEventInsertDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("attendance_id") val attendanceId: String,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0
)

@Serializable
data class RecitationNotificationEventInsertDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("recitation_id") val recitationId: String,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0
)

@Serializable
data class HomeworkNotificationEventInsertDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("homework_id") val homeworkId: String,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0
)

@Serializable
data class ExamNotificationEventInsertDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("exam_id") val examId: String,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0
)

// ----------------------------------------------------------------------------
// Typed Response DTOs from Supabase PostgREST (Supabase -> Android)
// ----------------------------------------------------------------------------

@Serializable
data class NotificationEventResponseDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("attendance_id") val attendanceId: String? = null,
    @SerialName("recitation_id") val recitationId: String? = null,
    @SerialName("homework_id") val homeworkId: String? = null,
    @SerialName("exam_id") val examId: String? = null,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("processing_started_at") val processingStartedAt: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
