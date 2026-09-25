package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.model.InsertStudentRequest
import com.example.core.model.SoftDeleteStudentRequest
import com.example.core.model.SupabaseGroupDayDto
import com.example.core.model.SupabaseGroupDto
import com.example.core.model.SupabaseRecitationDto
import com.example.core.model.UpdateStudentRequest
import com.example.core.model.UpsertAttendanceRequest
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.OutboxEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json

class OutboxSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = try { DatabaseProvider.getDatabase(context) } catch (_: Exception) { null }
    private val outboxDao = db?.outboxDao()
    private val client = SupabaseClientProvider.client

    companion object {
        internal var operationProcessor: (suspend (OutboxEntity) -> Unit)? = null
    }

    override suspend fun doWork(): Result {
        val dao = outboxDao ?: return Result.success()

        val user = try {
            client.auth.currentUserOrNull()
        } catch (e: Exception) {
            null
        }

        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
        if (teacherId == null) {
            return Result.success()
        }

        val operations = try {
            dao.getPendingOperationsForTeacher(teacherId).take(50)
        } catch (e: Exception) {
            return Result.retry()
        }

        if (operations.isEmpty()) {
            return Result.success()
        }

        var shouldRetryDueToTransientFailure = false

        for (op in operations) {
            // Skip operations that have already failed permanently or reached max retry threshold
            if (op.status == "FAILED" || op.retryCount >= 5) {
                continue
            }

            if (op.teacherId != null && op.teacherId != teacherId) {
                dao.updateOperation(
                    op.copy(
                        status = "FAILED",
                        lastError = "Tenant isolation mismatch"
                    )
                )
                continue
            }

            try {
                val processor = operationProcessor
                if (processor != null) {
                    processor(op)
                } else {
                    processOperation(op)
                }
                dao.updateOperation(
                    op.copy(
                        status = "SYNCED",
                        lastError = null
                    )
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                val isPermanent = isPermanentError(e)

                val newRetryCount = op.retryCount + 1
                val isMaxRetriesReached = newRetryCount >= 5
                val newStatus = if (isPermanent || isMaxRetriesReached) "FAILED" else "PENDING"

                dao.updateOperation(
                    op.copy(
                        status = newStatus,
                        retryCount = newRetryCount,
                        lastError = errorMsg
                    )
                )

                if (!isPermanent && !isMaxRetriesReached) {
                    shouldRetryDueToTransientFailure = true
                }
            }
        }

        val remaining = dao.getPendingOperationsForTeacher(teacherId)
        val hasPendingRemaining = remaining.any { it.status == "PENDING" && it.retryCount < 5 }
        return if (shouldRetryDueToTransientFailure || hasPendingRemaining) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun processOperation(op: OutboxEntity) {
        val entityType = op.entityType.uppercase()
        val opType = op.operationType.uppercase()
        val entityId = op.entityId

        when (entityType) {
            "STUDENT" -> {
                when (opType) {
                    "INSERT" -> {
                        val req = Json.decodeFromString<InsertStudentRequest>(op.payload)
                        val dataMap = mapOf(
                            "id" to entityId,
                            "teacher_id" to req.teacherId,
                            "grade_id" to req.gradeId,
                            "full_name" to req.fullName,
                            "parent_phone" to req.parentPhone,
                            "has_whatsapp" to req.hasWhatsApp,
                            "alternative_phone" to req.alternativePhone
                        )
                        client.postgrest["students"].upsert(dataMap)
                    }
                    "UPDATE" -> {
                        val req = Json.decodeFromString<UpdateStudentRequest>(op.payload)
                        client.postgrest["students"].update(req) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                    "DELETE" -> {
                        val req = Json.decodeFromString<SoftDeleteStudentRequest>(op.payload)
                        client.postgrest["students"].update(req) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                }
            }
            "ATTENDANCE" -> {
                when (opType) {
                    "UPSERT" -> {
                        val req = Json.decodeFromString<UpsertAttendanceRequest>(op.payload)
                        client.postgrest["attendance"].upsert(req) {
                            onConflict = "student_id,date"
                        }
                    }
                    "DELETE" -> {
                        val parts = entityId.split("_")
                        if (parts.size >= 2) {
                            val studentId = parts[0]
                            val date = parts[1]
                            client.postgrest["attendance"].delete {
                                filter {
                                    eq("student_id", studentId)
                                    eq("date", date)
                                    eq("teacher_id", op.teacherId ?: "")
                                }
                            }
                        } else {
                            client.postgrest["attendance"].delete {
                                filter {
                                    eq("id", entityId)
                                    eq("teacher_id", op.teacherId ?: "")
                                }
                            }
                        }
                    }
                }
            }
            "RECITATION" -> {
                when (opType) {
                    "INSERT" -> {
                        val dto = Json.decodeFromString<SupabaseRecitationDto>(op.payload)
                        val fixedDto = dto.copy(id = entityId)
                        client.postgrest["recitations"].upsert(fixedDto)
                    }
                    "UPDATE" -> {
                        val dto = Json.decodeFromString<SupabaseRecitationDto>(op.payload)
                        client.postgrest["recitations"].update(dto) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                    "DELETE" -> {
                        client.postgrest["recitations"].delete {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                }
            }
            "EXAM" -> {
                val dto = Json.decodeFromString<com.example.core.model.SupabaseExamDto>(op.payload)
                val fixedDto = dto.copy(id = entityId)
                when (opType) {
                    "INSERT" -> {
                        client.postgrest["exams"].upsert(fixedDto)
                    }
                    "UPDATE" -> {
                        client.postgrest["exams"].update(fixedDto) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                    "DELETE" -> {
                        client.postgrest["exams"].delete {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                }
            }
            "PAYMENT" -> {
                when (opType) {
                    "UPSERT" -> {
                        val req = Json.decodeFromString<com.example.core.model.UpsertLessonPaymentRequest>(op.payload)
                        client.postgrest["lesson_payments"].upsert(req) {
                            onConflict = "student_id,year,month"
                        }
                    }
                }
            }
            "GROUP" -> {
                val dto = Json.decodeFromString<SupabaseGroupDto>(op.payload)
                when (opType) {
                    "INSERT", "UPDATE" -> {
                        client.postgrest["groups"].upsert(dto)
                    }
                    "DELETE" -> {
                        client.postgrest["groups"].delete {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                }
            }
            "GROUP_DAYS" -> {
                val dtos = Json.decodeFromString<List<SupabaseGroupDayDto>>(op.payload)
                when (opType) {
                    "REPLACE" -> {
                        // Delete all days for this group first, then insert the new list
                        client.postgrest["group_days"].delete {
                            filter {
                                eq("group_id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                        if (dtos.isNotEmpty()) {
                            client.postgrest["group_days"].upsert(dtos)
                        }
                    }
                }
            }
            "GRADE" -> {
                val dto = Json.decodeFromString<com.example.core.model.Grade>(op.payload)
                when (opType) {
                    "INSERT" -> {
                        val dataMap = mapOf(
                            "id" to entityId,
                            "teacher_id" to dto.teacherId,
                            "name" to dto.name,
                            "display_order" to dto.displayOrder
                        )
                        client.postgrest["grades"].upsert(dataMap)
                    }
                    "UPDATE" -> {
                        val dataMap = mapOf(
                            "name" to dto.name,
                            "display_order" to dto.displayOrder
                        )
                        client.postgrest["grades"].update(dataMap) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                    "DELETE" -> {
                        client.postgrest["grades"].delete {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", op.teacherId ?: "")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isPermanentError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("400") || msg.contains("404") || msg.contains("422") ||
                msg.contains("violates foreign key") || msg.contains("violates unique constraint")
    }
}
