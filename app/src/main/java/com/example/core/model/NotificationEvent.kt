package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Supported notification event types in MIDAR.
 */
@Serializable
enum class NotificationEventType(val titleAr: String, val badgeColorHex: Long) {
    @SerialName("ABSENCE")
    ABSENCE("إشعار غياب", 0xFFE65100),

    @SerialName("RECITATION")
    RECITATION("تقييم تسميع", 0xFF2E7D32),

    @SerialName("EXAM")
    EXAM("نتيجة اختبار", 0xFF1565C0),

    @SerialName("HOMEWORK")
    HOMEWORK("متابعة واجب", 0xFF6A1B9A),

    @SerialName("MONTHLY_REPORT")
    MONTHLY_REPORT("تقرير شهري", 0xFF00838F);

    val tableName: String
        get() = when (this) {
            ABSENCE -> "notification_events"
            RECITATION -> "recitation_notification_events"
            HOMEWORK -> "homework_notification_events"
            EXAM -> "exam_notification_events"
            MONTHLY_REPORT -> "monthly_report_notification_events"
        }

    val sourceColumnName: String
        get() = when (this) {
            ABSENCE -> "attendance_id"
            RECITATION -> "recitation_id"
            HOMEWORK -> "homework_id"
            EXAM -> "exam_id"
            MONTHLY_REPORT -> "monthly_report_id"
        }
}

/**
 * Clean domain representation of a notification event.
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

    data class MonthlyReport(
        override val id: String = UUID.randomUUID().toString(),
        override val teacherId: String,
        override val studentId: String,
        val monthlyReportId: String,
        override val status: String = "PENDING",
        override val attempts: Int = 0
    ) : NotificationEvent {
        override val sourceId: String get() = monthlyReportId
        override val eventType: NotificationEventType get() = NotificationEventType.MONTHLY_REPORT
    }
}

// ----------------------------------------------------------------------------
// Delivery Status & Recipient Models for UI & ViewModel
// ----------------------------------------------------------------------------

@Serializable
enum class NotificationDeliveryStatus(val labelAr: String) {
    IDLE("جاهز للإرسال"),
    QUEUED("في الانتظار"),
    SENDING("جارٍ الإرسال..."),
    SENT("تم الإرسال بنجاح ✅"),
    FAILED("فشل الإرسال ❌"),
    NO_PHONE("لا يوجد رقم هاتف ⚠️"),
    ALREADY_SENT("تم الإرسال مسبقاً ✓")
}

@Serializable
data class NotificationRecipient(
    val studentId: String,
    val studentName: String,
    val parentPhone: String?,
    val hasWhatsApp: Boolean = true,
    val sourceId: String,
    val notificationType: NotificationEventType,
    val previewDetails: String = "",
    val isSelectable: Boolean = true,
    val isSelected: Boolean = true,
    val deliveryStatus: NotificationDeliveryStatus = NotificationDeliveryStatus.IDLE,
    val deliveryError: String? = null
)

@Serializable
data class NotificationBatchResult(
    val totalCount: Int,
    val queuedCount: Int,
    val alreadySentCount: Int,
    val failedCount: Int,
    val recipientStatuses: Map<String, NotificationDeliveryStatus> = emptyMap()
)

// ----------------------------------------------------------------------------
// Centralized Message Formatter Architecture (No scattered hardcoded strings)
// ----------------------------------------------------------------------------

object NotificationMessageFormatter {

    fun formatMessage(
        type: NotificationEventType,
        studentName: String,
        teacherName: String = "المعلم",
        subject: String? = null,
        date: String? = null,
        score: Double? = null,
        maxScore: Double? = null,
        titleOrTopic: String? = null,
        statusText: String? = null,
        notes: String? = null,
        month: Int? = null,
        year: Int? = null,
        attendanceRate: Float? = null
    ): String {
        return buildString {
            when (type) {
                NotificationEventType.ABSENCE -> {
                    appendLine("مدار | إشعار غياب ⚠️")
                    appendLine()
                    appendLine("السلام عليكم ورحمة الله وبركاته 🌷")
                    appendLine()
                    appendLine("نود إحاطة سيادتكم بأن الطالب/ة: *$studentName*")
                    appendLine("تم تسجيل غيابه عن حصة اليوم.")
                    if (!date.isNullOrBlank()) appendLine("📅 التاريخ: $date")
                    if (!subject.isNullOrBlank()) appendLine("📚 المادة: $subject")
                    appendLine("👨‍🏫 المعلم: $teacherName")
                    appendLine()
                    appendLine("نرجو متابعة انتظام الطالب في الحضور حرصاً على مستواه التعليمي.")
                    appendLine()
                    append("مدار — منصة إدارة ومتابعة الطلاب")
                }
                NotificationEventType.RECITATION -> {
                    appendLine("مدار | تقييم التسميع 📖")
                    appendLine()
                    appendLine("السلام عليكم ورحمة الله وبركاته 🌷")
                    appendLine()
                    appendLine("نود إعلامكم بنتيجة تسميع الطالب/ة: *$studentName*")
                    if (!titleOrTopic.isNullOrBlank()) appendLine("📖 الموضع: $titleOrTopic")
                    if (score != null) {
                        val maxStr = if (maxScore != null && maxScore > 0) " من $maxScore" else ""
                        appendLine("⭐️ التقييم: $score$maxStr")
                    }
                    if (!notes.isNullOrBlank()) appendLine("📝 ملاحظة المعلم: $notes")
                    appendLine("👨‍🏫 المعلم: $teacherName")
                    appendLine()
                    appendLine("بارك الله في جهود الطالب ووفقه دائماً 🌟")
                    appendLine()
                    append("مدار — منصة إدارة ومتابعة الطلاب")
                }
                NotificationEventType.EXAM -> {
                    appendLine("مدار | نتيجة الاختبار 📝")
                    appendLine()
                    appendLine("السلام عليكم ورحمة الله وبركاته 🌷")
                    appendLine()
                    appendLine("نود إعلامكم بنتيجة اختبار الطالب/ة: *$studentName*")
                    if (!titleOrTopic.isNullOrBlank()) appendLine("📋 الاختبار: $titleOrTopic")
                    if (score != null) {
                        val maxStr = if (maxScore != null && maxScore > 0) " من $maxScore" else ""
                        appendLine("🎯 الدرجة: $score$maxStr")
                    }
                    if (!notes.isNullOrBlank()) appendLine("📝 الملاحظات: $notes")
                    appendLine("👨‍🏫 المعلم: $teacherName")
                    appendLine()
                    appendLine("مع تمنياتنا للطالب بمزيد من التفوق والنجاح 🎯")
                    appendLine()
                    append("مدار — منصة إدارة ومتابعة الطلاب")
                }
                NotificationEventType.HOMEWORK -> {
                    appendLine("مدار | متابعة الواجب ✍️")
                    appendLine()
                    appendLine("السلام عليكم ورحمة الله وبركاته 🌷")
                    appendLine()
                    appendLine("نود إعلامكم بحالة الواجب للطالب/ة: *$studentName*")
                    if (!titleOrTopic.isNullOrBlank()) appendLine("📚 عنوان الواجب: $titleOrTopic")
                    if (!statusText.isNullOrBlank()) appendLine("📌 الحالة: *$statusText*")
                    if (!notes.isNullOrBlank()) appendLine("📝 ملاحظة: $notes")
                    appendLine("👨‍🏫 المعلم: $teacherName")
                    appendLine()
                    appendLine("نرجو المتابعة المستمرة لضمان التميز الدراسي ✨")
                    appendLine()
                    append("مدار — منصة إدارة ومتابعة الطلاب")
                }
                NotificationEventType.MONTHLY_REPORT -> {
                    appendLine("مدار | التقرير الشهري 📊")
                    appendLine()
                    appendLine("السلام عليكم ورحمة الله وبركاته 🌷")
                    appendLine()
                    appendLine("إليكم ملخص الأداء الشهري للطالب/ة: *$studentName*")
                    if (month != null && year != null) appendLine("🗓 عن شهر: $month / $year")
                    if (attendanceRate != null) appendLine("🟢 نسبة الحضور: ${String.format(java.util.Locale.US, "%.1f", attendanceRate * 100)}%")
                    if (!notes.isNullOrBlank()) appendLine("💬 تقييم المعلم: $notes")
                    appendLine("👨‍🏫 المعلم: $teacherName")
                    appendLine()
                    appendLine("شاكرين حسن تعاونكم الدائم معنا ❤️")
                    appendLine()
                    append("مدار — منصة إدارة ومتابعة الطلاب")
                }
            }
        }
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

@Serializable
data class MonthlyReportNotificationEventInsertDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("monthly_report_id") val monthlyReportId: String,
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
    @SerialName("monthly_report_id") val monthlyReportId: String? = null,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("processing_started_at") val processingStartedAt: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class NotificationHistoryItemDto(
    @SerialName("id") val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("notification_type") val notificationType: String,
    @SerialName("status") val status: String,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null
)
