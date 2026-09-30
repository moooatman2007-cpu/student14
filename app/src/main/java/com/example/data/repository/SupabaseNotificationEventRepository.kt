package com.example.data.repository

import com.example.core.model.AbsenceNotificationEventInsertDto
import com.example.core.model.ExamNotificationEventInsertDto
import com.example.core.model.HomeworkNotificationEventInsertDto
import com.example.core.model.NotificationEvent
import com.example.core.model.RecitationNotificationEventInsertDto
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production implementation of NotificationEventRepository communicating directly
 * with Supabase PostgREST for inserting PENDING events.
 */
class SupabaseNotificationEventRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : NotificationEventRepository {

    override suspend fun createNotificationEvent(
        event: NotificationEvent
    ): NotificationEventCreationResult = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext NotificationEventCreationResult.Failed(
                error = IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."),
                message = "انتهت الجلسة، يرجى تسجيل الدخول أولاً."
            )

        val targetTable = event.eventType.tableName

        try {
            when (event) {
                is NotificationEvent.Absence -> {
                    val dto = AbsenceNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        attendanceId = event.attendanceId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Recitation -> {
                    val dto = RecitationNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        recitationId = event.recitationId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Homework -> {
                    val dto = HomeworkNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        homeworkId = event.homeworkId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Exam -> {
                    val dto = ExamNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        examId = event.examId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
            }

            NotificationEventCreationResult.Created(event)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isDuplicate = msg.contains("23505", ignoreCase = true) ||
                    msg.contains("duplicate key", ignoreCase = true) ||
                    msg.contains("unique constraint", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true) ||
                    msg.contains("409", ignoreCase = true)

            if (isDuplicate) {
                NotificationEventCreationResult.AlreadyExists(
                    sourceId = event.sourceId,
                    eventType = event.eventType
                )
            } else {
                NotificationEventCreationResult.Failed(
                    error = e,
                    message = "فشل إنشاء حدث الإشعار: ${e.message ?: "خطأ غير معروف"}"
                )
            }
        }
    }
}
