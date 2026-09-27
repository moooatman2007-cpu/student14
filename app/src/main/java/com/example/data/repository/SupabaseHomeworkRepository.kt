package com.example.data.repository

import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.SupabaseHomeworkDto
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.HomeworkDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.sync.OutboxSyncScheduler
import com.example.util.DateUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SupabaseHomeworkRepository(
    private val homeworkDao: HomeworkDao? = try { DatabaseProvider.getDatabase().homeworkDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
) : HomeworkRepository {
    private val client = SupabaseClientProvider.client

    private suspend fun fetchAndCacheHomeworkForStudent(studentId: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["homework"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseHomeworkDto>()
                .map { it.toHomework(teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            homeworkDao?.upsertHomeworkList(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndCacheHomeworkForTeacher() {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["homework"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseHomeworkDto>()
                .map { it.toHomework(teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            homeworkDao?.upsertHomeworkList(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getHomeworkForStudent(studentId: String): Flow<List<Homework>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && homeworkDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheHomeworkForStudent(studentId)
                }
                homeworkDao.getHomeworkByStudent(teacherId, studentId).map { entities ->
                    entities.map { it.toDomain() }
                }.collect { list ->
                    send(list)
                }
            }
        }

        return flow {
            if (teacherId == null) {
                emit(emptyList())
                return@flow
            }
            try {
                val list = client.postgrest["homework"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseHomeworkDto>()
                    .map { it.toHomework() }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getHomeworkForStudentByMonth(
        studentId: String,
        year: Int,
        month: Int
    ): Flow<List<Homework>> = flow {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
        if (teacherId == null) {
            emit(emptyList())
            return@flow
        }
        try {
            val dateRange = DateUtils.getMonthDateRange(year, month)
            val startDate = dateRange.startDate
            val endDate = dateRange.endDate

            val list = client.postgrest["homework"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseHomeworkDto>()
                .map { it.toHomework(teacherId) }
            emit(list)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getHomeworkForTeacher(): Flow<List<Homework>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && homeworkDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheHomeworkForTeacher()
                }
                homeworkDao.getAllHomeworkByTeacher(teacherId).map { entities ->
                    entities.map { it.toDomain() }
                }.collect { list ->
                    send(list)
                }
            }
        }

        return flow {
            if (teacherId == null) {
                emit(emptyList())
                return@flow
            }
            try {
                val list = client.postgrest["homework"]
                    .select {
                        filter {
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseHomeworkDto>()
                    .map { it.toHomework(teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun addHomework(
        studentId: String,
        date: String,
        title: String,
        status: HomeworkStatus,
        note: String?
    ): Result<Homework> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))
        
        val generatedId = UUID.randomUUID().toString()
        val dto = SupabaseHomeworkDto(
            id = generatedId,
            teacherId = teacherId,
            studentId = studentId,
            date = date,
            title = title,
            status = status.name,
            note = note
        )

        try {
            val inserted = client.postgrest["homework"]
                .insert(dto) {
                    select()
                }
                .decodeSingle<SupabaseHomeworkDto>()
            
            val homework = inserted.toHomework(teacherId)
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                homeworkDao?.upsertSingleHomework(homework.toEntity())
            }
            Result.success(homework)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                val homework = dto.toHomework(teacherId)
                homeworkDao?.upsertSingleHomework(homework.toEntity())

                val payload = Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "HOMEWORK",
                        entityId = generatedId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                Result.success(homework)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل إضافة الواجب: ${ex.message}"))
            }
        }
    }

    override suspend fun updateHomework(homework: Homework): Result<Homework> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))
        try {
            val dto = SupabaseHomeworkDto(
                id = homework.homeworkId,
                teacherId = teacherId,
                studentId = homework.studentId,
                date = homework.date,
                title = homework.title,
                status = homework.status.name,
                note = homework.note
            )
            val updated = client.postgrest["homework"].update(dto) {
                filter {
                    eq("id", homework.homeworkId)
                    eq("teacher_id", teacherId)
                }
                select()
            }.decodeSingle<SupabaseHomeworkDto>()

            val result = updated.toHomework(teacherId)
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                homeworkDao?.upsertSingleHomework(result.toEntity())
            }
            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                val homeworkWithTeacher = if (homework.teacherId.isBlank()) homework.copy(teacherId = teacherId) else homework
                homeworkDao?.upsertSingleHomework(homeworkWithTeacher.toEntity())

                val dto = SupabaseHomeworkDto(
                    id = homework.homeworkId,
                    teacherId = teacherId,
                    studentId = homework.studentId,
                    date = homework.date,
                    title = homework.title,
                    status = homework.status.name,
                    note = homework.note
                )
                val payload = Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPDATE",
                        entityType = "HOMEWORK",
                        entityId = homework.homeworkId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                Result.success(homeworkWithTeacher)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل تعديل الواجب: ${ex.message}"))
            }
        }
    }

    override suspend fun deleteHomework(homeworkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))
        try {
            client.postgrest["homework"].delete {
                filter {
                    eq("id", homeworkId)
                    eq("teacher_id", teacherId)
                }
            }
            homeworkDao?.deleteById(teacherId, homeworkId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                homeworkDao?.deleteById(teacherId, homeworkId)

                val payload = "{\"id\":\"$homeworkId\"}"
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "HOMEWORK",
                        entityId = homeworkId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                Result.success(Unit)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل حذف الواجب: ${ex.message}"))
            }
        }
    }

    override suspend fun getHomeworkById(homeworkId: String): Homework? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null

        val cached = homeworkDao?.getHomeworkByIdSync(teacherId, homeworkId)
        if (cached != null) {
            return@withContext cached.toDomain()
        }

        try {
            val dto = client.postgrest["homework"]
                .select {
                    filter {
                        eq("id", homeworkId)
                        eq("teacher_id", teacherId)
                    }
                }
                .decodeSingleOrNull<SupabaseHomeworkDto>()
            
            val homework = dto?.toHomework(teacherId)
            if (homework != null && com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                homeworkDao?.upsertSingleHomework(homework.toEntity())
            }
            homework
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
