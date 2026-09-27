package com.example.data.repository

import com.example.core.model.LessonPayment
import com.example.core.model.SupabaseLessonPaymentDto
import com.example.core.model.UpsertLessonPaymentRequest
import com.example.BuildConfig
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.sync.OutboxSyncScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.UUID

class SupabasePaymentRepository(
    private val paymentDao: PaymentDao? = try { DatabaseProvider.getDatabase().paymentDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
) : PaymentRepository {
    private val client = SupabaseClientProvider.client

    override fun getMonthlyPayments(year: Int, month: Int): Flow<List<LessonPayment>> = flow {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
        if (teacherId == null) {
            if (BuildConfig.DEBUG) android.util.Log.w("SupabasePaymentRepo", "getMonthlyPayments: No authenticated user found.")
            emit(emptyList())
            return@flow
        }
        try {
            // Network First
            val list = client.postgrest["lesson_payments"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("year", year)
                        eq("month", month)
                    }
                }
                .decodeList<SupabaseLessonPaymentDto>()
                .map { it.toLessonPayment() }

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                paymentDao?.upsertPayments(list.map { it.toEntity() })
            }
            emit(list)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("SupabasePaymentRepo", "Network call failed, falling back to local cache for year=$year, month=$month: ${e.message}")
            val cached = paymentDao?.getPaymentsByMonthSync(teacherId, year, month) ?: emptyList()
            emit(cached.map { it.toDomain() })
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getMonthlyPaymentsList(year: Int, month: Int): List<LessonPayment> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext emptyList()
        try {
            val list = client.postgrest["lesson_payments"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("year", year)
                        eq("month", month)
                    }
                }
                .decodeList<SupabaseLessonPaymentDto>()
                .map { it.toLessonPayment() }

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                paymentDao?.upsertPayments(list.map { it.toEntity() })
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("SupabasePaymentRepo", "Network call failed, falling back to local cache: ${e.message}")
            val cached = paymentDao?.getPaymentsByMonthSync(teacherId, year, month) ?: emptyList()
            cached.map { it.toDomain() }
        }
    }

    override suspend fun getPaymentForStudent(studentId: String, year: Int, month: Int): LessonPayment? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null
        try {
            val dto = client.postgrest["lesson_payments"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("student_id", studentId)
                        eq("year", year)
                        eq("month", month)
                    }
                }
                .decodeSingleOrNull<SupabaseLessonPaymentDto>()

            val payment = dto?.toLessonPayment()
            if (payment != null) {
                if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                    paymentDao?.upsertSinglePayment(payment.toEntity())
                }
                return@withContext payment
            }
            val cached = paymentDao?.getPaymentForStudentSync(teacherId, studentId, year, month)
            cached?.toDomain()
        } catch (e: Exception) {
            e.printStackTrace()
            val cached = paymentDao?.getPaymentForStudentSync(teacherId, studentId, year, month)
            cached?.toDomain()
        }
    }

    override suspend fun setPaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        isPaid: Boolean,
        amount: Double
    ): Result<LessonPayment> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى تسجيل الدخول مجددًا."))

        try {
            val paidAtIso = if (isPaid) java.time.OffsetDateTime.now().toString() else null

            val upsertDto = UpsertLessonPaymentRequest(
                teacherId = teacherId,
                studentId = studentId,
                year = year,
                month = month,
                amount = amount,
                isPaid = isPaid,
                paidAt = paidAtIso
            )

            val dto = client.postgrest["lesson_payments"].upsert(upsertDto) {
                onConflict = "student_id,year,month"
                select()
            }.decodeSingle<SupabaseLessonPaymentDto>()

            val payment = dto.toLessonPayment()
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                paymentDao?.upsertSinglePayment(payment.toEntity())
            }

            Result.success(payment)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                val paidAtIso = if (isPaid) java.time.OffsetDateTime.now().toString() else null
                val paymentId = "${studentId}_${year}_${month}"
                val payment = LessonPayment(
                    paymentId = paymentId,
                    studentId = studentId,
                    teacherId = teacherId,
                    year = year,
                    month = month,
                    amount = amount,
                    isPaid = isPaid,
                    paidAt = paidAtIso,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                paymentDao?.upsertSinglePayment(payment.toEntity())

                val upsertDto = UpsertLessonPaymentRequest(
                    teacherId = teacherId,
                    studentId = studentId,
                    year = year,
                    month = month,
                    amount = amount,
                    isPaid = isPaid,
                    paidAt = paidAtIso
                )
                val payload = kotlinx.serialization.json.Json.encodeToString(upsertDto)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPSERT",
                        entityType = "PAYMENT",
                        entityId = paymentId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                Result.success(payment)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل حفظ حالة الدفع محلياً: ${ex.message}"))
            }
        }
    }

    override suspend fun togglePaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        amount: Double
    ): Result<LessonPayment> = withContext(Dispatchers.IO) {
        val currentPayment = getPaymentForStudent(studentId, year, month)
        val newStatus = !(currentPayment?.isPaid ?: false)
        val currentAmount = if (amount > 0) amount else (currentPayment?.amount ?: 0.0)
        setPaymentStatus(studentId, year, month, newStatus, currentAmount)
    }
}
