package com.example.data.repository

import com.example.core.model.Recitation
import com.example.core.model.RecitationSummary
import com.example.core.model.SupabaseRecitationDto
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.RecitationDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.sync.OutboxSyncScheduler
import com.example.util.DateUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
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

class SupabaseRecitationRepository(
    private val recitationDao: RecitationDao? = try { DatabaseProvider.getDatabase().recitationDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
) : RecitationRepository {
    private val client = SupabaseClientProvider.client

    private suspend fun fetchAndCacheRecitationsForStudent(studentId: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["recitations"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseRecitationDto>()
                .map { it.toRecitation(teacherId = teacherId) }

            recitationDao?.upsertRecitations(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndCacheRecitationsByMonth(studentId: String, startDate: String, endDate: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["recitations"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseRecitationDto>()
                .map { it.toRecitation(teacherId = teacherId) }

            recitationDao?.upsertRecitations(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getRecitationsForStudent(studentId: String): Flow<List<Recitation>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && recitationDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheRecitationsForStudent(studentId)
                }
                recitationDao.getRecitationsByStudent(teacherId, studentId).map { entities ->
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
                val list = client.postgrest["recitations"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseRecitationDto>()
                    .map { it.toRecitation(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecitationsForStudentByMonth(
        studentId: String,
        year: Int,
        month: Int
    ): Flow<List<Recitation>> {
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && recitationDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheRecitationsByMonth(studentId, startDate, endDate)
                }
                recitationDao.getRecitationsByStudentAndRange(teacherId, studentId, startDate, endDate).map { entities ->
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
                val list = client.postgrest["recitations"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                            gte("date", startDate)
                            lte("date", endDate)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseRecitationDto>()
                    .map { it.toRecitation(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getRecitationSummaryForStudent(
        studentId: String,
        year: Int,
        month: Int
    ): RecitationSummary = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext RecitationSummary()
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate

        try {
            val list = client.postgrest["recitations"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                }
                .decodeList<SupabaseRecitationDto>()
                .map { it.toRecitation(teacherId = teacherId) }

            recitationDao?.upsertRecitations(list.map { it.toEntity() })

            if (list.isEmpty()) return@withContext RecitationSummary()

            val totalCount = list.size
            val avgScore = list.map { it.score }.average()
            val avgMaxScore = list.map { it.maxScore }.average()
            val avgPct = list.map { (it.score / it.maxScore) * 100.0 }.average().toFloat()

            RecitationSummary(
                totalCount = totalCount,
                averageScore = avgScore,
                averageMaxScore = avgMaxScore,
                averagePercentage = avgPct
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedEntities = recitationDao?.getRecitationsByStudentSync(teacherId, studentId)?.filter {
                it.date >= startDate && it.date <= endDate
            } ?: emptyList()

            val list = cachedEntities.map { it.toDomain() }
            if (list.isEmpty()) return@withContext RecitationSummary()

            val totalCount = list.size
            val avgScore = list.map { it.score }.average()
            val avgMaxScore = list.map { it.maxScore }.average()
            val avgPct = list.map { (it.score / it.maxScore) * 100.0 }.average().toFloat()

            RecitationSummary(
                totalCount = totalCount,
                averageScore = avgScore,
                averageMaxScore = avgMaxScore,
                averagePercentage = avgPct
            )
        }
    }

    override suspend fun getRecitationsCountThisMonth(year: Int, month: Int): Int = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext 0
        try {
            val dateRange = DateUtils.getMonthDateRange(year, month)
            val startDate = dateRange.startDate
            val endDate = dateRange.endDate

            val result = client.postgrest["recitations"]
                .select {
                    head = true
                    count(Count.EXACT)
                    filter {
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                }

            result.countOrNull()?.toInt() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun getAllRecitationsForTeacher(): Flow<List<Recitation>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && recitationDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        val list = client.postgrest["recitations"]
                            .select {
                                filter {
                                    eq("teacher_id", teacherId)
                                }
                                order("date", order = Order.DESCENDING)
                            }
                            .decodeList<SupabaseRecitationDto>()
                            .map { it.toRecitation(teacherId = teacherId) }

                        recitationDao.upsertRecitations(list.map { it.toEntity() })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                recitationDao.getAllRecitationsByTeacher(teacherId).map { entities ->
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
                val list = client.postgrest["recitations"]
                    .select {
                        filter {
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseRecitationDto>()
                    .map { it.toRecitation(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun addRecitation(
        studentId: String,
        date: String,
        title: String,
        content: String,
        score: Double,
        maxScore: Double,
        note: String?
    ): Result<Recitation> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        val generatedId = UUID.randomUUID().toString()
        val dto = SupabaseRecitationDto(
            id = generatedId,
            teacherId = teacherId,
            studentId = studentId,
            date = date,
            title = title,
            content = content,
            score = score,
            maxScore = maxScore,
            note = note
        )

        try {
            val inserted = client.postgrest["recitations"]
                .insert(dto) {
                    select()
                }
                .decodeSingle<SupabaseRecitationDto>()

            val recitation = inserted.toRecitation(teacherId = teacherId)
            recitationDao?.upsertSingleRecitation(recitation.toEntity())

            Result.success(recitation)
        } catch (e: Exception) {
            e.printStackTrace()
            val isLocalFailure = e is android.database.sqlite.SQLiteException || e.stackTrace.any { it.className.contains("sqlite") || it.className.contains("room") }
            if (isLocalFailure) {
                return@withContext Result.failure(Exception("تم حفظ التسميع على السيرفر ولكن فشل التحديث المحلي: ${e.message}"))
            }

            // Offline fallback
            try {
                val recitation = dto.toRecitation(teacherId = teacherId)
                recitationDao?.upsertSingleRecitation(recitation.toEntity())

                val payload = Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "RECITATION",
                        entityId = generatedId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                Result.success(recitation)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل إضافة التسميع: ${ex.message}"))
            }
        }
    }

    override suspend fun updateRecitation(recitation: Recitation): Result<Recitation> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        try {
            val dto = SupabaseRecitationDto(
                id = recitation.recitationId,
                teacherId = teacherId,
                studentId = recitation.studentId,
                date = recitation.date,
                title = recitation.title,
                content = recitation.content,
                score = recitation.score,
                maxScore = recitation.maxScore,
                note = recitation.note
            )
            val updated = client.postgrest["recitations"].update(dto) {
                filter {
                    eq("id", recitation.recitationId)
                    eq("teacher_id", teacherId)
                }
                select()
            }.decodeSingle<SupabaseRecitationDto>()

            val result = updated.toRecitation(teacherId = teacherId)
            recitationDao?.upsertSingleRecitation(result.toEntity())

            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                val recitationWithTeacher = recitation.copy(teacherId = teacherId)
                recitationDao?.upsertSingleRecitation(recitationWithTeacher.toEntity())

                val dto = SupabaseRecitationDto(
                    id = recitation.recitationId,
                    teacherId = teacherId,
                    studentId = recitation.studentId,
                    date = recitation.date,
                    title = recitation.title,
                    content = recitation.content,
                    score = recitation.score,
                    maxScore = recitation.maxScore,
                    note = recitation.note
                )
                val payload = Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPDATE",
                        entityType = "RECITATION",
                        entityId = recitation.recitationId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                Result.success(recitationWithTeacher)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل تعديل التسميع: ${ex.message}"))
            }
        }
    }

    override suspend fun deleteRecitation(recitationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        try {
            client.postgrest["recitations"].delete {
                filter {
                    eq("id", recitationId)
                    eq("teacher_id", teacherId)
                }
            }
            recitationDao?.deleteById(teacherId, recitationId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                recitationDao?.deleteById(teacherId, recitationId)

                val payload = "{\"id\":\"$recitationId\"}"
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "RECITATION",
                        entityId = recitationId,
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
                Result.failure(Exception("فشل حذف التسميع: ${ex.message}"))
            }
        }
    }

    override suspend fun getRecitationById(recitationId: String): Recitation? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null

        val cached = recitationDao?.getRecitationByIdSync(teacherId, recitationId)
        if (cached != null) {
            return@withContext cached.toDomain()
        }

        try {
            val dto = client.postgrest["recitations"]
                .select {
                    filter {
                        eq("id", recitationId)
                        eq("teacher_id", teacherId)
                    }
                }
                .decodeSingleOrNull<SupabaseRecitationDto>()

            val recitation = dto?.toRecitation(teacherId = teacherId)
            if (recitation != null) {
                recitationDao?.upsertSingleRecitation(recitation.toEntity())
            }
            recitation
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
