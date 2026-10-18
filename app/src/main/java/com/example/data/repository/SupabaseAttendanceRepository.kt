package com.example.data.repository

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.BatchAttendanceResult
import com.example.core.model.BatchAttendanceResultDto
import com.example.core.model.BatchSyncStatus
import com.example.core.model.SyncResult
import com.example.core.model.SupabaseAttendanceDto
import com.example.core.model.UpsertAttendanceRequest
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.AttendanceEntity
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupabaseAttendanceRepository(
    private val attendanceDao: AttendanceDao? = try { DatabaseProvider.getDatabase().attendanceDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null },
    private val studentDao: com.example.data.local.dao.StudentDao? = try { DatabaseProvider.getDatabase().studentDao() } catch (_: Exception) { null }
) : AttendanceRepository {
    private val client = SupabaseClientProvider.client

    private suspend fun fetchAndCacheAttendanceForStudent(studentId: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["attendance"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseAttendanceDto>()
                .map { it.toAttendance(teacherId = teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return

            for (serverAttendance in list) {
                attendanceDao?.reconcileAttendance(teacherId, serverAttendance.toEntity())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndCacheAttendanceByMonth(studentId: String, startDate: String, endDate: String) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return
        try {
            val list = client.postgrest["attendance"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                    order("date", order = Order.DESCENDING)
                }
                .decodeList<SupabaseAttendanceDto>()
                .map { it.toAttendance(teacherId = teacherId) }

            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) return

            for (serverAttendance in list) {
                attendanceDao?.reconcileAttendance(teacherId, serverAttendance.toEntity())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
    }

    override fun getAttendanceForStudent(studentId: String): Flow<List<Attendance>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && attendanceDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheAttendanceForStudent(studentId)
                }
                attendanceDao.getAttendanceByStudent(teacherId, studentId).map { entities ->
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
                val list = client.postgrest["attendance"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceDto>()
                    .map { it.toAttendance(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getAttendanceForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Attendance>> {
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && attendanceDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    fetchAndCacheAttendanceByMonth(studentId, startDate, endDate)
                }
                attendanceDao.getAttendanceByStudentAndRange(teacherId, studentId, startDate, endDate).map { entities ->
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
                val list = client.postgrest["attendance"]
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                            gte("date", startDate)
                            lte("date", endDate)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceDto>()
                    .map { it.toAttendance(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAttendanceSummaryForStudent(
        studentId: String,
        year: Int,
        month: Int
    ): AttendanceSummary = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext AttendanceSummary()
        val dateRange = DateUtils.getMonthDateRange(year, month)
        val startDate = dateRange.startDate
        val endDate = dateRange.endDate

        try {
            val list = client.postgrest["attendance"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        gte("date", startDate)
                        lte("date", endDate)
                    }
                }
                .decodeList<SupabaseAttendanceDto>()
                .map { it.toAttendance(teacherId = teacherId) }

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                attendanceDao?.upsertAttendance(list.map { it.toEntity() })
            }

            val total = list.size
            val present = list.count { it.status == AttendanceStatus.PRESENT }
            val absent = list.count { it.status == AttendanceStatus.ABSENT }
            val late = list.count { it.status == AttendanceStatus.LATE }
            val excused = list.count { it.status == AttendanceStatus.EXCUSED }
            val rate = if (total > 0) (present.toFloat() / total) * 100f else 0f

            AttendanceSummary(
                totalDays = total,
                presentCount = present,
                absentCount = absent,
                lateCount = late,
                excusedCount = excused,
                attendanceRate = rate
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedEntities = attendanceDao?.getAttendanceByStudentSync(teacherId, studentId)?.filter {
                it.date >= startDate && it.date <= endDate
            } ?: emptyList()

            val list = cachedEntities.map { it.toDomain() }
            val total = list.size
            val present = list.count { it.status == AttendanceStatus.PRESENT }
            val absent = list.count { it.status == AttendanceStatus.ABSENT }
            val late = list.count { it.status == AttendanceStatus.LATE }
            val excused = list.count { it.status == AttendanceStatus.EXCUSED }
            val rate = if (total > 0) (present.toFloat() / total) * 100f else 0f

            AttendanceSummary(
                totalDays = total,
                presentCount = present,
                absentCount = absent,
                lateCount = late,
                excusedCount = excused,
                attendanceRate = rate
            )
        }
    }

    override suspend fun getTodayAttendanceCount(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext Pair(0, 0)
        try {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

            coroutineScope {
                val presentDeferred = async {
                    val result = client.postgrest["attendance"].select {
                        head = true
                        count(Count.EXACT)
                        filter {
                            eq("teacher_id", teacherId)
                            eq("date", todayStr)
                            eq("status", AttendanceStatus.PRESENT.name)
                        }
                    }
                    result.countOrNull()?.toInt() ?: 0
                }

                val absentDeferred = async {
                    val result = client.postgrest["attendance"].select {
                        head = true
                        count(Count.EXACT)
                        filter {
                            eq("teacher_id", teacherId)
                            eq("date", todayStr)
                            eq("status", AttendanceStatus.ABSENT.name)
                        }
                    }
                    result.countOrNull()?.toInt() ?: 0
                }

                Pair(presentDeferred.await(), absentDeferred.await())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(0, 0)
        }
    }

    override fun getAllAttendanceForTeacher(): Flow<List<Attendance>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && attendanceDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        val list = client.postgrest["attendance"]
                            .select {
                                filter {
                                    eq("teacher_id", teacherId)
                                }
                                order("date", order = Order.DESCENDING)
                            }
                            .decodeList<SupabaseAttendanceDto>()
                            .map { it.toAttendance(teacherId = teacherId) }

                        if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                            attendanceDao.upsertAttendance(list.map { it.toEntity() })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                attendanceDao.getAllAttendanceByTeacher(teacherId).map { entities ->
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
                val list = client.postgrest["attendance"]
                    .select {
                        filter {
                            eq("teacher_id", teacherId)
                        }
                        order("date", order = Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceDto>()
                    .map { it.toAttendance(teacherId = teacherId) }
                emit(list)
            } catch (e: Exception) {
                e.printStackTrace()
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun isPermanentDatabaseError(e: Throwable): Boolean {
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
                msg.contains("pgrst") && (msg.contains("400") || msg.contains("404") || msg.contains("422"))
            ) {
                return true
            }
            if (current is io.github.jan.supabase.exceptions.RestException) {
                val restError = current.error.lowercase()
                val restDesc = current.description?.lowercase() ?: ""
                val full = "$restError $restDesc"
                if (full.contains("violates foreign key") ||
                    full.contains("violates unique constraint") ||
                    full.contains("duplicate key value") ||
                    full.contains("23503") ||
                    full.contains("23505") ||
                    full.contains("23502")
                ) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    override suspend fun recordOrUpdateAttendance(
        studentId: String,
        date: String,
        status: AttendanceStatus,
        note: String?,
        groupId: String?
    ): Result<SyncResult<Attendance>> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        val student = studentDao?.getStudentByIdSync(teacherId, studentId)
        
        if (groupId != null) {
            if (student?.groupId != groupId) {
                return@withContext Result.failure(Exception("الطالب ليس في هذه المجموعة"))
            }
        }
        
        val effectiveGroupId = groupId ?: student?.groupId
        val attendanceId = "${studentId}_${date}"

        try {
            val upsertDto = UpsertAttendanceRequest(
                teacherId = teacherId,
                studentId = studentId,
                groupId = effectiveGroupId,
                date = date,
                status = status.name,
                note = note?.ifBlank { null }
            )

            val conflictColumns = "teacher_id,student_id,date"
            val dto = client.postgrest["attendance"].upsert(upsertDto) {
                onConflict = conflictColumns
                select()
            }.decodeSingle<SupabaseAttendanceDto>()

            val attendance = dto.toAttendance(teacherId = teacherId)
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                attendanceDao?.upsertSingleAttendance(attendance.toEntity())
            }

            Result.success(SyncResult(attendance, BatchSyncStatus.SAVED_TO_CLOUD))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SupabaseAttendanceRepo", "Failed to upsert attendance to Supabase: ${e.message}")

            if (isPermanentDatabaseError(e)) {
                val errorMsg = when {
                    e.message?.contains("violates foreign key", ignoreCase = true) == true ||
                    e.message?.contains("23503") == true -> "فشل الحفظ: تعارض في بيانات الطالب أو المجموعة المحددة (Foreign Key Violation)."
                    e.message?.contains("violates unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("23505") == true -> "فشل الحفظ: يوجد سجل حضور مطابق مسجل مسبقاً لهذا الطالب والتاريخ."
                    else -> "فشل دائم في حفظ السجل في قاعدة البيانات: ${e.message}"
                }
                return@withContext Result.failure(Exception(errorMsg, e))
            }

            // Offline fallback for transient network / connectivity issues
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val attendance = Attendance(
                        attendanceId = attendanceId,
                        studentId = studentId,
                        teacherId = teacherId,
                        groupId = effectiveGroupId,
                        date = date,
                        status = status,
                        note = note
                    )
                    attendanceDao?.upsertSingleAttendance(attendance.toEntity())

                    val upsertDto = UpsertAttendanceRequest(
                        teacherId = teacherId,
                        studentId = studentId,
                        groupId = effectiveGroupId,
                        date = date,
                        status = status.name,
                        note = note?.ifBlank { null }
                    )
                    val payload = Json.encodeToString(upsertDto)
                    outboxDao?.insertOperation(
                        OutboxEntity(
                            id = UUID.randomUUID().toString(),
                            operationType = "UPSERT",
                            entityType = "ATTENDANCE",
                            entityId = attendanceId,
                            payload = payload,
                            createdAt = System.currentTimeMillis(),
                            status = "PENDING",
                            teacherId = teacherId
                        )
                    )
                    OutboxSyncScheduler.scheduleSync()

                    Result.success(SyncResult(attendance, BatchSyncStatus.QUEUED_OFFLINE))
                }
            } catch (ex: Exception) {
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                ex.printStackTrace()
                Result.failure(Exception("فشل في حفظ سجل الحضور محلياً: ${ex.message}", ex))
            }
        }
    }

    override suspend fun recordBatchAttendance(
        date: String,
        records: List<BatchAttendanceItemDto>,
        groupId: String?
    ): Result<BatchAttendanceResult> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))

        if (records.isEmpty()) {
            return@withContext Result.success(BatchAttendanceResult(total = 0, date = date))
        }

        val studentEntitiesMap = studentDao?.getAllStudentsSync(teacherId)?.associateBy { it.studentId } ?: emptyMap()
        var batchResult: BatchAttendanceResult? = null
        var cloudSaved = false
        var lastCloudError: Exception? = null
        var insertedList: List<SupabaseAttendanceDto> = emptyList()

        // Direct Postgrest Bulk Upsert on 'attendance' table with Typed DTOs
        try {
            val upsertRequests = records.map { item ->
                val student = studentEntitiesMap[item.studentId]
                if (groupId != null && student?.groupId != groupId) {
                    return@withContext Result.failure(Exception("الطالب ليس في هذه المجموعة"))
                }
                
                val effectiveGroupId = groupId ?: student?.groupId
                UpsertAttendanceRequest(
                    teacherId = teacherId,
                    studentId = item.studentId,
                    groupId = effectiveGroupId,
                    date = date,
                    status = item.status,
                    note = item.note?.ifBlank { null }
                )
            }

            val distinctRequests = upsertRequests.distinctBy { it.studentId }

            if (distinctRequests.isNotEmpty()) {
                val conflictColumns = "teacher_id,student_id,date"
                insertedList = client.postgrest["attendance"].upsert(distinctRequests) {
                    onConflict = conflictColumns
                    select()
                }.decodeList<SupabaseAttendanceDto>()
            }

            var presentCount = 0
            var absentCount = 0
            var lateCount = 0
            var excusedCount = 0
            for (record in records) {
                val statusEnum = try { AttendanceStatus.valueOf(record.status) } catch(_: Exception) { AttendanceStatus.PRESENT }
                when (statusEnum) {
                    AttendanceStatus.PRESENT -> presentCount++
                    AttendanceStatus.ABSENT -> absentCount++
                    AttendanceStatus.LATE -> lateCount++
                    AttendanceStatus.EXCUSED -> excusedCount++
                }
            }

            batchResult = BatchAttendanceResult(
                total = records.size,
                presentCount = presentCount,
                absentCount = absentCount,
                lateCount = lateCount,
                excusedCount = excusedCount,
                date = date,
                syncStatus = BatchSyncStatus.SAVED_TO_CLOUD
            )
            cloudSaved = true
        } catch (bulkEx: Exception) {
            if (bulkEx is kotlinx.coroutines.CancellationException) throw bulkEx
            lastCloudError = bulkEx
            android.util.Log.e("SupabaseAttendanceRepo", "Direct Postgrest bulk upsert failed (${bulkEx.javaClass.simpleName}: ${bulkEx.message})")

            if (isPermanentDatabaseError(bulkEx)) {
                val errorMsg = when {
                    bulkEx.message?.contains("violates foreign key", ignoreCase = true) == true ||
                    bulkEx.message?.contains("23503") == true -> "فشل الحفظ: تعارض في بيانات الطالب أو المجموعة المحددة (Foreign Key Violation)."
                    bulkEx.message?.contains("violates unique constraint", ignoreCase = true) == true ||
                    bulkEx.message?.contains("23505") == true -> "فشل الحفظ: يوجد سجل حضور مطابق مسجل مسبقاً لهذا الطالب والتاريخ."
                    else -> "فشل دائم في حفظ السجلات في قاعدة البيانات: ${bulkEx.message}"
                }
                return@withContext Result.failure(Exception(errorMsg, bulkEx))
            }
        }

        if (cloudSaved && batchResult != null) {
            try {
                val serverMap = insertedList.associateBy { it.studentId }
                val entities = records.map { item ->
                    val statusEnum = try { AttendanceStatus.valueOf(item.status) } catch(_: Exception) { AttendanceStatus.PRESENT }
                    val effectiveGroupId = groupId ?: studentEntitiesMap[item.studentId]?.groupId
                    val serverDto = serverMap[item.studentId]
                    val attendanceId = serverDto?.id ?: "${item.studentId}_${date}"
                    
                    val attendance = Attendance(
                        attendanceId = attendanceId,
                        studentId = item.studentId,
                        teacherId = teacherId,
                        groupId = effectiveGroupId,
                        date = date,
                        status = statusEnum,
                        note = item.note
                    )
                    
                    if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                        attendanceDao?.reconcileAttendance(teacherId, attendance.toEntity())
                    }
                }
            } catch (cacheEx: Exception) {
                cacheEx.printStackTrace()
            }
            Result.success(batchResult)
        } else {
            // Offline fallback: store directly in Room and Outbox with ZERO network calls in loop
            val cloudException = lastCloudError ?: Exception("Unknown cloud error")
            try {
                var presentCount = 0
                var absentCount = 0
                var lateCount = 0
                var excusedCount = 0

                val entities = ArrayList<AttendanceEntity>(records.size)
                val outboxOps = ArrayList<OutboxEntity>(records.size)
                val now = System.currentTimeMillis()

                for (record in records) {
                    val statusEnum = try { AttendanceStatus.valueOf(record.status) } catch(_: Exception) { AttendanceStatus.PRESENT }
                    when (statusEnum) {
                        AttendanceStatus.PRESENT -> presentCount++
                        AttendanceStatus.ABSENT -> absentCount++
                        AttendanceStatus.LATE -> lateCount++
                        AttendanceStatus.EXCUSED -> excusedCount++
                    }

                    val effectiveGroupId = groupId ?: studentEntitiesMap[record.studentId]?.groupId
                    val attendanceId = "${record.studentId}_${date}"
                    val attendance = Attendance(
                        attendanceId = attendanceId,
                        studentId = record.studentId,
                        teacherId = teacherId,
                        groupId = effectiveGroupId,
                        date = date,
                        status = statusEnum,
                        note = record.note
                    )
                    entities.add(attendance.toEntity())

                    val upsertDto = UpsertAttendanceRequest(
                        teacherId = teacherId,
                        studentId = record.studentId,
                        groupId = effectiveGroupId,
                        date = date,
                        status = statusEnum.name,
                        note = record.note?.ifBlank { null }
                    )
                    val payload = Json.encodeToString(upsertDto)
                    outboxOps.add(
                        OutboxEntity(
                            id = UUID.randomUUID().toString(),
                            operationType = "UPSERT",
                            entityType = "ATTENDANCE",
                            entityId = attendanceId,
                            payload = payload,
                            createdAt = now,
                            status = "PENDING",
                            teacherId = teacherId
                        )
                    )
                }

                attendanceDao?.upsertAttendance(entities)
                outboxDao?.insertOperations(outboxOps)
                OutboxSyncScheduler.scheduleSync()

                Result.failure(Exception("فشل الحفظ المباشر على السحابة: ${cloudException.message ?: "تعذر الاتصال بالسيرفر"}. تم حفظ السجلات محلياً في قائمة الانتظار للمزامنة."))
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل حفظ الحضور: ${ex.message}"))
            }
        }
    }

    override suspend fun deleteAttendance(attendanceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        try {
            val parts = attendanceId.split("_")
            if (parts.size >= 2) {
                val studentId = parts[0]
                val date = parts[1]
                client.postgrest["attendance"].delete {
                    filter {
                        eq("student_id", studentId)
                        eq("date", date)
                        eq("teacher_id", teacherId)
                    }
                }
            } else {
                client.postgrest["attendance"].delete {
                    filter {
                        eq("id", attendanceId)
                        eq("teacher_id", teacherId)
                    }
                }
            }
            attendanceDao?.deleteAttendanceById(teacherId, attendanceId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                attendanceDao?.deleteAttendanceById(teacherId, attendanceId)

                val payload = "{\"id\":\"$attendanceId\"}"
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "ATTENDANCE",
                        entityId = attendanceId,
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
                Result.failure(Exception("فشل في حذف سجل الحضور: ${ex.message}"))
            }
        }
    }

    override suspend fun getAttendanceByDate(
        studentId: String,
        date: String
    ): Attendance? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null

        val cached = attendanceDao?.getAttendanceByDateSync(teacherId, studentId, date)
        if (cached != null) {
            return@withContext cached.toDomain()
        }

        try {
            val dto = client.postgrest["attendance"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                        eq("date", date)
                    }
                }
                .decodeSingleOrNull<SupabaseAttendanceDto>()

            val attendance = dto?.toAttendance(teacherId = teacherId)
            if (attendance != null && com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                attendanceDao?.upsertSingleAttendance(attendance.toEntity())
            }
            attendance
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getAttendanceForDateAndGroup(
        date: String,
        groupId: String?
    ): List<Attendance> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext emptyList()

        try {
            val list = client.postgrest["attendance"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("date", date)
                    }
                }
                .decodeList<SupabaseAttendanceDto>()
                .map { it.toAttendance(teacherId = teacherId) }
                .filter { it.groupId == groupId }

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId) && attendanceDao != null) {
                attendanceDao.upsertAttendance(list.map { it.toEntity() })
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            val cached = attendanceDao?.getAttendanceByDateAndGroupSync(teacherId, date, groupId)
            cached?.map { it.toDomain() } ?: emptyList()
        }
    }
}
