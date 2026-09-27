package com.example.data.repository

import com.example.core.model.Exam
import com.example.core.model.ExamSummary
import com.example.core.model.SupabaseExamDto
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.ExamDao
import com.example.data.local.dao.OutboxDao
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
import java.util.UUID

class SupabaseExamRepository(
    private val examDao: ExamDao? = try { DatabaseProvider.getDatabase().examDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
) : ExamRepository {
    private val client = SupabaseClientProvider.client

    private suspend fun fetchAndCacheExamsForStudent(studentId: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["exams"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseExamDto>()
                .map { it.toExam(teacherId = teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            examDao?.upsertExams(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndCacheExamsByMonth(studentId: String, startDate: String, endDate: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["exams"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseExamDto>()
                .map { it.toExam(teacherId = teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return
            examDao?.upsertExams(list.map { it.toEntity() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getExamsForStudent(studentId: String): Flow<List<Exam>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && examDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheExamsForStudent(studentId)
                }
                examDao.getExamsByStudent(teacherId, studentId).map { entities ->
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
                val list = client.postgrest["exams"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseExamDto>()
                    .map { it.toExam(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getExamsForStudentByMonth(
        studentId: String,
        year: Int,
        month: Int
    ): Flow<List<Exam>> {
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && examDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheExamsByMonth(studentId, startDate, endDate)
                }
                examDao.getExamsByStudentAndRange(teacherId, studentId, startDate, endDate).map { entities ->
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
                val list = client.postgrest["exams"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                            gte("date", startDate)
                            lte("date", endDate)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseExamDto>()
                    .map { it.toExam(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getExamSummaryForStudent(
        studentId: String,
        year: Int,
        month: Int
    ): ExamSummary = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext ExamSummary()
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate

        try {
            val list = client.postgrest["exams"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                }
                .decodeList<SupabaseExamDto>()
                .map { it.toExam(teacherId = teacherId) }

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                examDao?.upsertExams(list.map { it.toEntity() })
            }

            if (list.isEmpty()) return@withContext ExamSummary()

            val totalCount = list.size
            val avgScore = list.map { it.score }.average()
            val percentages = list.map { (it.score / it.maxScore) * 100.0 }
            val avgPct = percentages.average().toFloat()
            val highestPct = percentages.maxOrNull()?.toFloat() ?: 0f
            val lowestPct = percentages.minOrNull()?.toFloat() ?: 0f

            ExamSummary(
                totalCount = totalCount,
                averageScore = avgScore,
                averagePercentage = avgPct,
                highestPercentage = highestPct,
                lowestPercentage = lowestPct
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedEntities = examDao?.getExamsByStudentSync(teacherId, studentId)?.filter {
                it.date >= startDate && it.date <= endDate
            } ?: emptyList()

            val list = cachedEntities.map { it.toDomain() }
            if (list.isEmpty()) return@withContext ExamSummary()

            val totalCount = list.size
            val avgScore = list.map { it.score }.average()
            val percentages = list.map { (it.score / it.maxScore) * 100.0 }
            val avgPct = percentages.average().toFloat()
            val highestPct = percentages.maxOrNull()?.toFloat() ?: 0f
            val lowestPct = percentages.minOrNull()?.toFloat() ?: 0f

            ExamSummary(
                totalCount = totalCount,
                averageScore = avgScore,
                averagePercentage = avgPct,
                highestPercentage = highestPct,
                lowestPercentage = lowestPct
            )
        }
    }

    override suspend fun getExamsCountThisMonth(year: Int, month: Int): Int = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext 0
        try {
            val dateRange = DateUtils.getMonthDateRange(year, month)
            val startDate = dateRange.startDate
            val endDate = dateRange.endDate

            val result = client.postgrest["exams"]
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

    override fun getAllExamsForTeacher(): Flow<List<Exam>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && examDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        val list = client.postgrest["exams"]
                            .select {
                                filter {
                                    eq("teacher_id", teacherId)
                                }
                                order("date", order = Order.DESCENDING)
                            }
                            .decodeList<SupabaseExamDto>()
                            .map { it.toExam(teacherId = teacherId) }

                        if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                            examDao.upsertExams(list.map { it.toEntity() })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                examDao.getAllExamsByTeacher(teacherId).map { entities ->
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
                val list = client.postgrest["exams"]
                    .select {
                        filter {
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseExamDto>()
                    .map { it.toExam(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun addExam(
        studentId: String,
        date: String,
        examName: String,
        subject: String?,
        score: Double,
        maxScore: Double,
        note: String?
    ): Result<Exam> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))

        val generatedId = UUID.randomUUID().toString()
        val dto = SupabaseExamDto(
            id = generatedId,
            teacherId = teacherId,
            studentId = studentId,
            date = date,
            examName = examName,
            subject = subject,
            score = score,
            maxScore = maxScore,
            note = note
        )

        try {
            val inserted = client.postgrest["exams"]
                .insert(dto) {
                    select()
                }
                .decodeSingle<SupabaseExamDto>()

            val exam = inserted.toExam(teacherId = teacherId)
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                examDao?.upsertSingleExam(exam.toEntity())
            }

            Result.success(exam)
        } catch (e: Exception) {
            e.printStackTrace()
            val isLocalFailure = e is android.database.sqlite.SQLiteException || e.stackTrace.any { it.className.contains("sqlite") || it.className.contains("room") }
            if (isLocalFailure) {
                return@withContext Result.failure(Exception("تم إضافة الامتحان على السيرفر ولكن فشل التحديث المحلي: ${e.message}"))
            }

            // Offline fallback
            try {
                val exam = dto.toExam(teacherId = teacherId)
                examDao?.upsertSingleExam(exam.toEntity())

                val payload = kotlinx.serialization.json.Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "EXAM",
                        entityId = generatedId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                Result.success(exam)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل إضافة الامتحان: ${ex.message}"))
            }
        }
    }

    override suspend fun updateExam(exam: Exam): Result<Exam> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))

        try {
            val dto = SupabaseExamDto(
                id = exam.examId,
                teacherId = teacherId,
                studentId = exam.studentId,
                date = exam.date,
                examName = exam.examName,
                subject = exam.subject,
                score = exam.score,
                maxScore = exam.maxScore,
                note = exam.note
            )
            val updated = client.postgrest["exams"].update(dto) {
                filter {
                    eq("id", exam.examId)
                    eq("teacher_id", teacherId)
                }
                select()
            }.decodeSingle<SupabaseExamDto>()

            val result = updated.toExam(teacherId = teacherId)
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                examDao?.upsertSingleExam(result.toEntity())
            }

            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                val examWithTeacher = exam.copy(teacherId = teacherId)
                examDao?.upsertSingleExam(examWithTeacher.toEntity())

                val dto = SupabaseExamDto(
                    id = exam.examId,
                    teacherId = teacherId,
                    studentId = exam.studentId,
                    date = exam.date,
                    examName = exam.examName,
                    subject = exam.subject,
                    score = exam.score,
                    maxScore = exam.maxScore,
                    note = exam.note
                )
                val payload = kotlinx.serialization.json.Json.encodeToString(dto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPDATE",
                        entityType = "EXAM",
                        entityId = exam.examId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                Result.success(examWithTeacher)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل تعديل الامتحان: ${ex.message}"))
            }
        }
    }

    override suspend fun deleteExam(examId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))

        try {
            client.postgrest["exams"].delete {
                filter {
                    eq("id", examId)
                    eq("teacher_id", teacherId)
                }
            }
            examDao?.deleteById(teacherId, examId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                examDao?.deleteById(teacherId, examId)

                val payload = "{\"id\":\"$examId\"}"
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "EXAM",
                        entityId = examId,
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
                Result.failure(Exception("فشل حذف الامتحان: ${ex.message}"))
            }
        }
    }

    override suspend fun getExamById(examId: String): Exam? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null

        val cached = examDao?.getExamByIdSync(teacherId, examId)
        if (cached != null) {
            return@withContext cached.toDomain()
        }

        try {
            val dto = client.postgrest["exams"]
                .select {
                    filter {
                        eq("id", examId)
                        eq("teacher_id", teacherId)
                    }
                }
                .decodeSingleOrNull<SupabaseExamDto>()

            val exam = dto?.toExam(teacherId = teacherId)
            if (exam != null && com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                examDao?.upsertSingleExam(exam.toEntity())
            }
            exam
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
