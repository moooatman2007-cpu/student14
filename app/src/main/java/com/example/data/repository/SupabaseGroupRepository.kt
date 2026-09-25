package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.model.Group
import com.example.core.model.GroupDay
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.GroupDao
import com.example.data.local.dao.GroupDayDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.GroupDayEntity
import com.example.data.local.entity.GroupEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SupabaseGroupRepository(
    private val groupDao: GroupDao = DatabaseProvider.getDatabase().groupDao(),
    private val groupDayDao: GroupDayDao = DatabaseProvider.getDatabase().groupDayDao(),
    private val outboxDao: OutboxDao = DatabaseProvider.getDatabase().outboxDao(),
    private val studentDao: com.example.data.local.dao.StudentDao = DatabaseProvider.getDatabase().studentDao()
) : GroupRepository {
    private val client = SupabaseClientProvider.client

    private fun getAuthenticatedTeacherId(): String =
        client.auth.currentUserOrNull()?.id ?: throw Exception("Not authenticated")

    override fun observeGroups(teacherId: String): Flow<List<Group>> = 
        groupDao.observeGroupsByTeacher(teacherId).map { it.map { e -> e.toDomain() } }

    override fun observeActiveGroups(teacherId: String): Flow<List<Group>> =
        groupDao.observeActiveGroupsByTeacher(teacherId).map { it.map { e -> e.toDomain() } }

    override fun observeGroupsByGrade(teacherId: String, gradeId: String): Flow<List<Group>> =
        groupDao.observeGroupsByGrade(teacherId, gradeId).map { it.map { e -> e.toDomain() } }

    override suspend fun getGroupById(teacherId: String, groupId: String): Group? =
        withContext(Dispatchers.IO) {
            groupDao.getGroupById(groupId)?.toDomain()
        }

    override suspend fun createGroup(group: Group): Result<Group> = withContext(Dispatchers.IO) {
        try {
            val entity = group.toEntity()
            groupDao.insertGroup(entity)
            enqueueOutbox(entity, "INSERT", "GROUP")
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroup(group: Group): Result<Group> = withContext(Dispatchers.IO) {
        try {
            val entity = group.toEntity()
            groupDao.updateGroup(entity)
            enqueueOutbox(entity, "UPDATE", "GROUP")
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateGroup(teacherId: String, groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = groupDao.getGroupById(groupId) ?: return@withContext Result.failure(Exception("Group not found"))
            val updated = entity.toDomain().copy(active = false).toEntity()
            groupDao.updateGroup(updated)
            enqueueOutbox(updated, "UPDATE", "GROUP")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignStudentToGroup(teacherId: String, studentId: String, groupId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authId = getAuthenticatedTeacherId()
            if (authId != teacherId) return@withContext Result.failure(Exception("Cross-tenant assignment rejected"))
            
            val student = studentDao.getStudentByIdSync(teacherId, studentId) ?: return@withContext Result.failure(Exception("Student not found"))
            
            if (groupId != null) {
                val group = groupDao.getGroupById(groupId) ?: return@withContext Result.failure(Exception("Group not found"))
                if (group.teacherId != teacherId) return@withContext Result.failure(Exception("Cross-tenant group assignment rejected"))
                if (group.gradeId != student.gradeId) return@withContext Result.failure(Exception("Incompatible grade"))
            }

            val updatedStudent = student.copy(groupId = groupId)
            studentDao.upsertStudent(updatedStudent)
            enqueueOutbox(updatedStudent, "UPDATE", "STUDENT")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeGroupDays(teacherId: String, groupId: String): Flow<List<GroupDay>> =
        groupDayDao.observeGroupDays(groupId).map { it.map { e -> e.toDomain() } }

    override fun observeAllGroupDays(teacherId: String): Flow<List<GroupDay>> =
        groupDayDao.observeGroupDaysByTeacher(teacherId).map { it.map { e -> e.toDomain() } }

    override suspend fun replaceGroupDays(teacherId: String, groupId: String, days: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authId = getAuthenticatedTeacherId()
            if (authId != teacherId) return@withContext Result.failure(Exception("Cross-tenant rejected"))
            
            val entities = days.map { GroupDayEntity(teacherId, groupId, it) }
            
            DatabaseProvider.getDatabase().withTransaction {
                groupDayDao.deleteGroupDays(groupId)
                groupDayDao.insertGroupDays(entities)
            }
            
            enqueueOutbox(entities, "REPLACE", "GROUP_DAYS")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun enqueueOutbox(entity: Any, operation: String, type: String) {
        val authId = getAuthenticatedTeacherId()
        val json = Json.encodeToString(entity)
        val outbox = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = operation,
            entityType = type,
            entityId = when(entity) {
                is GroupEntity -> entity.id
                is GroupDayEntity -> entity.groupId
                is com.example.data.local.entity.StudentEntity -> entity.studentId
                is List<*> -> "BATCH"
                else -> ""
            },
            payload = json,
            createdAt = System.currentTimeMillis(),
            teacherId = authId
        )
        outboxDao.insertOperation(outbox)
    }
}
