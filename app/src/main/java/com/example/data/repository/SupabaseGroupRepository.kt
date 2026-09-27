package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.model.*
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.GroupDao
import com.example.data.local.dao.GroupDayDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.GroupDayEntity
import com.example.data.local.entity.GroupEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.*
import com.example.data.sync.OutboxSyncScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SupabaseGroupRepository(
    private val groupDao: GroupDao? = try { DatabaseProvider.getDatabase().groupDao() } catch (_: Exception) { null },
    private val groupDayDao: GroupDayDao? = try { DatabaseProvider.getDatabase().groupDayDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null },
    private val studentDao: com.example.data.local.dao.StudentDao? = try { DatabaseProvider.getDatabase().studentDao() } catch (_: Exception) { null }
) : GroupRepository {
    private val client = SupabaseClientProvider.client

    private fun getAuthenticatedTeacherId(): String =
        SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id ?: throw Exception("Not authenticated")

    override fun observeGroups(teacherId: String): Flow<List<Group>> = 
        groupDao?.observeGroupsByTeacher(teacherId)?.map { it.map { e -> e.toDomain() } } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeActiveGroups(teacherId: String): Flow<List<Group>> =
        groupDao?.observeActiveGroupsByTeacher(teacherId)?.map { it.map { e -> e.toDomain() } } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeGroupsByGrade(teacherId: String, gradeId: String): Flow<List<Group>> =
        groupDao?.observeGroupsByGrade(teacherId, gradeId)?.map { it.map { e -> e.toDomain() } } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun getGroupById(teacherId: String, groupId: String): Group? =
        withContext(Dispatchers.IO) {
            val entity = groupDao?.getGroupById(groupId)
            if (entity != null && entity.teacherId == teacherId) {
                entity.toDomain()
            } else {
                null
            }
        }

    override suspend fun refreshGroups(teacherId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteGroups = client.postgrest["groups"].select {
                filter {
                    eq("teacher_id", teacherId)
                }
            }.decodeList<SupabaseGroupDto>()

            val remoteGroupDays = client.postgrest["group_days"].select {
                filter {
                    eq("teacher_id", teacherId)
                }
            }.decodeList<SupabaseGroupDayDto>()

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                val groupEntities = remoteGroups.map { it.toDomain().toEntity() }
                val groupDayEntities = remoteGroupDays.map { it.toDomain().toEntity() }

                for (g in groupEntities) {
                    groupDao?.insertGroup(g)
                }
                groupDayDao?.deleteGroupDaysByTeacher(teacherId)
                if (groupDayEntities.isNotEmpty()) {
                    groupDayDao?.insertGroupDays(groupDayEntities)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseGroupRepo", "Failed to refresh remote groups: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createGroup(group: Group): Result<Group> = withContext(Dispatchers.IO) {
        try {
            val entity = group.toEntity()
            groupDao?.insertGroup(entity)

            val dto = group.toSupabaseDto()
            try {
                client.postgrest["groups"].upsert(dto)
            } catch (cloudEx: Exception) {
                android.util.Log.w("SupabaseGroupRepo", "Direct group cloud insert failed, queuing to outbox: ${cloudEx.message}")
                enqueueOutbox(dto, "INSERT", "GROUP")
                OutboxSyncScheduler.scheduleSync()
            }

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroup(group: Group): Result<Group> = withContext(Dispatchers.IO) {
        try {
            val entity = group.toEntity()
            groupDao?.updateGroup(entity)

            val dto = group.toSupabaseDto()
            try {
                client.postgrest["groups"].upsert(dto)
            } catch (cloudEx: Exception) {
                android.util.Log.w("SupabaseGroupRepo", "Direct group cloud update failed, queuing to outbox: ${cloudEx.message}")
                enqueueOutbox(dto, "UPDATE", "GROUP")
                OutboxSyncScheduler.scheduleSync()
            }

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteGroup(teacherId: String, groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authId = getAuthenticatedTeacherId()
            if (authId != teacherId) return@withContext Result.failure(Exception("غير مصرح بحذف هذه المجموعة."))

            val group = groupDao?.getGroupById(groupId) ?: return@withContext Result.failure(Exception("المجموعة غير موجودة."))
            if (group.teacherId != teacherId) return@withContext Result.failure(Exception("غير مصرح بحذف هذه المجموعة."))

            // Try cloud delete first to properly check foreign key constraints (students / attendance)
            try {
                client.postgrest["group_days"].delete {
                    filter {
                        eq("group_id", groupId)
                        eq("teacher_id", teacherId)
                    }
                }
                client.postgrest["groups"].delete {
                    filter {
                        eq("id", groupId)
                        eq("teacher_id", teacherId)
                    }
                }

                // If cloud delete succeeded, remove from local Room
                groupDayDao?.deleteGroupDays(groupId)
                groupDao?.deleteGroup(group)
                Result.success(Unit)
            } catch (cloudEx: Exception) {
                val msg = cloudEx.message?.lowercase() ?: ""
                if (msg.contains("foreign key") || msg.contains("23503") || msg.contains("violates foreign key") || msg.contains("is still referenced")) {
                    Result.failure(Exception("لا يمكن حذف المجموعة لأنها مرتبطة بطلاب مسجلين أو سجلات حضور سابقة. يمكنك إلغاء تفعيل المجموعة بدلاً من حذفها."))
                } else {
                    // Offline fallback: delete locally and queue delete op
                    groupDayDao?.deleteGroupDays(groupId)
                    groupDao?.deleteGroup(group)
                    enqueueOutbox(group.toDomain().toSupabaseDto(), "DELETE", "GROUP")
                    OutboxSyncScheduler.scheduleSync()
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateGroup(teacherId: String, groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = groupDao?.getGroupById(groupId) ?: return@withContext Result.failure(Exception("Group not found"))
            if (entity.teacherId != teacherId) return@withContext Result.failure(Exception("Cross-tenant group update rejected"))
            val updated = entity.toDomain().copy(active = false)
            updateGroup(updated).map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignStudentToGroup(teacherId: String, studentId: String, groupId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authId = getAuthenticatedTeacherId()
            if (authId != teacherId) return@withContext Result.failure(Exception("Cross-tenant assignment rejected"))
            
            val student = studentDao?.getStudentByIdSync(teacherId, studentId) ?: return@withContext Result.failure(Exception("Student not found"))
            
            if (groupId != null) {
                val group = groupDao?.getGroupById(groupId) ?: return@withContext Result.failure(Exception("Group not found"))
                if (group.teacherId != teacherId) return@withContext Result.failure(Exception("Cross-tenant group assignment rejected"))
                if (group.gradeId != student.gradeId) return@withContext Result.failure(Exception("Incompatible grade"))
            }

            val updatedStudent = student.copy(groupId = groupId)
            studentDao?.upsertStudent(updatedStudent)
            
            val updateReq = UpdateStudentRequest(
                gradeId = updatedStudent.gradeId,
                fullName = updatedStudent.fullName,
                parentPhone = updatedStudent.parentPhone,
                hasWhatsApp = updatedStudent.hasWhatsApp,
                alternativePhone = updatedStudent.alternativePhone
            )
            val json = Json.encodeToString(updateReq)
            val outbox = OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "UPDATE",
                entityType = "STUDENT",
                entityId = studentId,
                payload = json,
                createdAt = System.currentTimeMillis(),
                teacherId = authId
            )
            outboxDao?.insertOperation(outbox)
            OutboxSyncScheduler.scheduleSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeGroupDays(teacherId: String, groupId: String): Flow<List<GroupDay>> =
        groupDayDao?.observeGroupDays(groupId)?.map { it.map { e -> e.toDomain() } } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeAllGroupDays(teacherId: String): Flow<List<GroupDay>> =
        groupDayDao?.observeGroupDaysByTeacher(teacherId)?.map { it.map { e -> e.toDomain() } } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun replaceGroupDays(teacherId: String, groupId: String, days: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authId = getAuthenticatedTeacherId()
            if (authId != teacherId) return@withContext Result.failure(Exception("Cross-tenant rejected"))
            
            val entities = days.map { GroupDayEntity(teacherId, groupId, it) }
            val dtos = days.map { SupabaseGroupDayDto(teacherId, groupId, it) }
            
            try {
                DatabaseProvider.getDatabase().withTransaction {
                    groupDayDao?.deleteGroupDays(groupId)
                    groupDayDao?.insertGroupDays(entities)
                }
            } catch (_: Exception) {
                groupDayDao?.deleteGroupDays(groupId)
                groupDayDao?.insertGroupDays(entities)
            }
            
            try {
                client.postgrest["group_days"].delete {
                    filter {
                        eq("group_id", groupId)
                        eq("teacher_id", teacherId)
                    }
                }
                if (dtos.isNotEmpty()) {
                    client.postgrest["group_days"].upsert(dtos)
                }
            } catch (cloudEx: Exception) {
                android.util.Log.w("SupabaseGroupRepo", "Direct group_days cloud replace failed, queuing outbox: ${cloudEx.message}")
                enqueueOutbox(dtos, "REPLACE", "GROUP_DAYS")
                OutboxSyncScheduler.scheduleSync()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun enqueueOutbox(payloadObject: Any, operation: String, type: String) {
        val authId = getAuthenticatedTeacherId()
        val json = when (payloadObject) {
            is SupabaseGroupDto -> Json.encodeToString(payloadObject)
            is SupabaseGroupDayDto -> Json.encodeToString(payloadObject)
            is List<*> -> {
                val groupDays = payloadObject.filterIsInstance<SupabaseGroupDayDto>()
                if (groupDays.isNotEmpty()) {
                    Json.encodeToString(groupDays)
                } else {
                    "[]"
                }
            }
            is Group -> Json.encodeToString(payloadObject.toSupabaseDto())
            is GroupEntity -> Json.encodeToString(payloadObject.toDomain().toSupabaseDto())
            else -> "{}"
        }
        val outbox = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = operation,
            entityType = type,
            entityId = when(payloadObject) {
                is SupabaseGroupDto -> payloadObject.id
                is SupabaseGroupDayDto -> payloadObject.groupId
                is Group -> payloadObject.id
                is GroupEntity -> payloadObject.id
                is List<*> -> (payloadObject.firstOrNull() as? SupabaseGroupDayDto)?.groupId ?: "BATCH"
                else -> ""
            },
            payload = json,
            createdAt = System.currentTimeMillis(),
            teacherId = authId
        )
        outboxDao?.insertOperation(outbox)
    }
}
