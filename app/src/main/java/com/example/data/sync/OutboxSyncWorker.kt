package com.example.data.sync

import android.util.Log

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.model.InsertStudentRequest
import com.example.core.model.SoftDeleteStudentRequest
import com.example.core.model.Student
import com.example.core.model.SupabaseGroupDayDto
import com.example.core.model.SupabaseGroupDto
import com.example.core.model.SupabaseRecitationDto
import com.example.core.model.SupabaseAttendanceDto
import com.example.core.model.SupabaseStudentInsertDto
import com.example.core.model.UpdateStudentRequest
import com.example.core.model.UpsertAttendanceRequest
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.StudentDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json

class OutboxSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = try { DatabaseProvider.getDatabase(context) } catch (_: Exception) { null }
    private val outboxDao = db?.outboxDao()
    private val studentDao = db?.studentDao()
    private val client = SupabaseClientProvider.client
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    companion object {
        internal var operationProcessor: (suspend (OutboxEntity) -> Unit)? = null

        suspend fun reconcileStudentInRoom(
            studentDao: StudentDao?,
            serverStudent: Student,
            teacherId: String
        ) {
            if (studentDao == null || !com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            val existing = studentDao.getStudentByIdSync(teacherId, serverStudent.studentId)
            val reconciled = serverStudent.copy(
                groupId = existing?.groupId ?: serverStudent.groupId
            )
            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            studentDao.upsertStudent(reconciled.toEntity())
        }
    }

    override suspend fun doWork(): Result {
        val dao = outboxDao ?: return Result.success()

        val user = try {
            client.auth.currentUserOrNull()
        } catch (e: Exception) {
            null
        }

        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
        if (teacherId == null || !com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
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

        val blockedEntities = mutableSetOf<String>()
        var shouldRetryDueToTransientFailure = false

        for (op in operations) {
            if (isStopped || !com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                return Result.success()
            }

            val entityKey = getEntityKey(op)

            // Skip operations that have already failed permanently or reached max retry threshold
            if (op.status == "FAILED" || op.retryCount >= 5) {
                blockedEntities.add(entityKey)
                continue
            }

            // Entity-level dependency barrier: if an earlier operation on this entity failed, defer later operations
            if (blockedEntities.contains(entityKey)) {
                continue
            }

            if (op.teacherId != null && op.teacherId != teacherId) {
                dao.updateOperation(
                    op.copy(
                        status = "FAILED",
                        lastError = "Tenant isolation mismatch"
                    )
                )
                blockedEntities.add(entityKey)
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

                blockedEntities.add(entityKey)

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
                        Log.d("SyncDiag", "WORKER INSERT START: entityId=$entityId, opId=${op.id}")
                        val req = json.decodeFromString<InsertStudentRequest>(op.payload)
                        val targetTeacherId = op.teacherId ?: req.teacherId
                        val dto = SupabaseStudentInsertDto(
                            id = if (req.id.isNotBlank()) req.id else entityId,
                            teacherId = targetTeacherId,
                            gradeId = req.gradeId,
                            fullName = req.fullName,
                            parentPhone = req.parentPhone,
                            hasWhatsApp = req.hasWhatsApp,
                            alternativePhone = req.alternativePhone
                        )

                        // Idempotent upsert on Supabase:
                        // On first insert, server trigger generates official ST-XXXXX student_code.
                        // On replay / retry, existing student row is updated without creating duplicates,
                        // and trigger preserves original student_code.
                        val response = client.postgrest["students"].upsert(dto) {
                            select()
                        }.decodeSingleOrNull<Student>()

                        val reconciledStudent = response ?: try {
                            client.postgrest["students"].select {
                                filter {
                                    eq("id", entityId)
                                    eq("teacher_id", targetTeacherId)
                                }
                            }.decodeSingleOrNull<Student>()
                        } catch (_: Exception) { null }

                        if (reconciledStudent != null) {
                            reconcileStudentInRoom(studentDao, reconciledStudent, targetTeacherId)
                        }
                    }
                    "UPDATE" -> {
                        val req = json.decodeFromString<UpdateStudentRequest>(op.payload)
                        val targetTeacherId = op.teacherId ?: ""
                        val response = client.postgrest["students"].update(req) {
                            filter {
                                eq("id", entityId)
                                eq("teacher_id", targetTeacherId)
                            }
                            select()
                        }.decodeSingleOrNull<Student>()

                        if (response != null) {
                            reconcileStudentInRoom(studentDao, response, targetTeacherId)
                        }
                    }
                    "DELETE" -> {
                        val req = json.decodeFromString<SoftDeleteStudentRequest>(op.payload)
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
                        val teacherId = op.teacherId ?: client.auth.currentUserOrNull()?.id ?: req.teacherId

                        // 1. Query all attendance records for this student on this date for this teacher
                        val existingList = client.postgrest["attendance"].select {
                            filter {
                                eq("student_id", req.studentId)
                                eq("date", req.date)
                                eq("teacher_id", teacherId)
                            }
                        }.decodeList<SupabaseAttendanceDto>()

                        if (existingList.isNotEmpty()) {
                            // There is an existing record for this student on this date.
                            // Due to UNIQUE index (teacher_id, student_id, date), there should normally be at most one record.
                            val existingRecord = existingList.first()

                            // Check whether this operation is an exact idempotent retry of the same state,
                            // or a conflict (different group, status, or note).
                            val sameGroup = (existingRecord.groupId == req.groupId)
                            val sameStatus = existingRecord.status.equals(req.status, ignoreCase = true)
                            val sameNote = (existingRecord.note?.trim() ?: "") == (req.note?.trim() ?: "")

                            if (sameGroup && sameStatus && sameNote) {
                                // Idempotent replay: Server already has the identical record.
                                // Do not update or overwrite anything; record is completely intact and safe.
                                Log.i("OutboxSyncWorker", "Attendance record already exists identically for student ${req.studentId} on ${req.date}. No-op to preserve data integrity.")
                            } else {
                                // Conflict detected!
                                // The record on server differs in group_id, status, or note.
                                // Under strict non-destructive policy, we NEVER silently overwrite existing data.
                                val conflictDetails = buildString {
                                    append("Attendance conflict for student ${req.studentId} on ${req.date}: ")
                                    if (!sameGroup) append("group mismatch (server=${existingRecord.groupId}, op=${req.groupId}); ")
                                    if (!sameStatus) append("status mismatch (server=${existingRecord.status}, op=${req.status}); ")
                                    if (!sameNote) append("note mismatch; ")
                                }
                                throw IllegalStateException("23505 conflict: $conflictDetails")
                            }
                        } else {
                            // No existing record found during pre-check.
                            // Use safe INSERT rather than UPSERT with onConflict, to prevent silently overwriting
                            // an existing or concurrently created attendance record.
                            try {
                                client.postgrest["attendance"].insert(req)
                            } catch (insEx: Exception) {
                                // Specifically verify if the error is a UNIQUE constraint violation (23505)
                                val isUnique = isUniqueViolation(insEx)
                                if (isUnique) {
                                    // Re-query server to inspect the competing record
                                    val recheckList = client.postgrest["attendance"].select {
                                        filter {
                                            eq("student_id", req.studentId)
                                            eq("date", req.date)
                                            eq("teacher_id", teacherId)
                                        }
                                    }.decodeList<SupabaseAttendanceDto>()

                                    val recheck = recheckList.firstOrNull()
                                    if (recheck != null) {
                                        val sameGroup = (recheck.groupId == req.groupId)
                                        val sameStatus = recheck.status.equals(req.status, ignoreCase = true)
                                        val sameNote = (recheck.note?.trim() ?: "") == (req.note?.trim() ?: "")

                                        if (sameGroup && sameStatus && sameNote) {
                                            Log.i("OutboxSyncWorker", "Concurrent insert resolved: record already matches on server.")
                                        } else {
                                            val conflictDetails = buildString {
                                                append("Concurrent attendance conflict for student ${req.studentId} on ${req.date}: ")
                                                if (!sameGroup) append("group mismatch (server=${recheck.groupId}, op=${req.groupId}); ")
                                                if (!sameStatus) append("status mismatch (server=${recheck.status}, op=${req.status}); ")
                                                if (!sameNote) append("note mismatch; ")
                                            }
                                            throw IllegalStateException("23505 conflict: $conflictDetails")
                                        }
                                    } else {
                                        // Re-query did not reveal a record, preserve original unique constraint error
                                        throw insEx
                                    }
                                } else {
                                    // Foreign key, not-null, permission, or network error: preserve original exception
                                    throw insEx
                                }
                            }
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
            "HOMEWORK" -> {
                when (opType) {
                    "INSERT", "UPDATE" -> {
                        val dto = Json.decodeFromString<com.example.core.model.SupabaseHomeworkDto>(op.payload)
                        client.postgrest["homework"].upsert(dto)
                    }
                    "DELETE" -> {
                        client.postgrest["homework"].delete {
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

    private fun getEntityKey(op: OutboxEntity): String {
        val rawId = op.entityId.trim()
        if (rawId.isBlank()) return "OP:${op.id}"
        val type = op.entityType.uppercase()
        return if (type == "GROUP" || type == "GROUP_DAYS") {
            "GROUP:$rawId"
        } else {
            "$type:$rawId"
        }
    }

    private fun isUniqueViolation(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("violates unique constraint") ||
                msg.contains("duplicate key value") ||
                msg.contains("23505")
            ) {
                return true
            }

            if (current is RestException) {
                val restError = current.error.lowercase()
                val restDesc = current.description?.lowercase() ?: ""
                val full = "$restError $restDesc"
                if (full.contains("violates unique constraint") ||
                    full.contains("duplicate key value") ||
                    full.contains("23505")
                ) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    private fun isPermanentError(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("violates foreign key") ||
                msg.contains("violates unique constraint") ||
                msg.contains("duplicate key value") ||
                msg.contains("23503") || // foreign_key_violation
                msg.contains("23505") || // unique_violation
                msg.contains("23502") || // not_null_violation
                msg.contains("42p01") || // undefined_table
                msg.contains("42703") || // undefined_column
                msg.contains("400") ||
                msg.contains("404") ||
                msg.contains("422") ||
                msg.contains("bad request")
            ) {
                return true
            }

            if (current is RestException) {
                val restError = current.error.lowercase()
                val restDesc = current.description?.lowercase() ?: ""
                val full = "$restError $restDesc"
                if (full.contains("violates foreign key") ||
                    full.contains("violates unique constraint") ||
                    full.contains("duplicate key value") ||
                    full.contains("23503") ||
                    full.contains("23505") ||
                    full.contains("23502") ||
                    full.contains("400") ||
                    full.contains("404") ||
                    full.contains("422")
                ) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }
}
