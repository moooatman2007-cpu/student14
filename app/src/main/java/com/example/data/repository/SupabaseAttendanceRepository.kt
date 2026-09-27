package com.example.data.repository

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.BatchAttendanceResult
import com.example.core.model.BatchAttendanceResultDto
import com.example.core.model.BatchSyncStatus
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

            attendanceDao?.upsertAttendance(list.map { it.toEntity() })
        } catch (e: Exception) {
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

            attendanceDao?.upsertAttendance(list.map { it.toEntity() })
        } catch (e: Exception) {
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

            attendanceDao?.upsertAttendance(list.map { it.toEntity() })

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

                        attendanceDao.upsertAttendance(list.map { it.toEntity() })
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

    override suspend fun recordOrUpdateAttendance(
        studentId: String,
        date: String,
        status: AttendanceStatus,
        note: String?
    ): Result<Attendance> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        val studentGroupId = studentDao?.getStudentByIdSync(teacherId, studentId)?.groupId

        try {
            val upsertDto = UpsertAttendanceRequest(
                teacherId = teacherId,
                studentId = studentId,
                groupId = studentGroupId,
                date = date,
                status = status.name,
                note = note?.ifBlank { null }
            )

            val dto = client.postgrest["attendance"].upsert(upsertDto) {
                onConflict = "student_id,date"
                select()
            }.decodeSingle<SupabaseAttendanceDto>()

            val attendance = dto.toAttendance(teacherId = teacherId)
            attendanceDao?.upsertSingleAttendance(attendance.toEntity())

            Result.success(attendance)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAttendanceRepo", "Failed to upsert attendance to Supabase: ${e.message}")
            // Offline fallback
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val attendance = Attendance(
                        attendanceId = "${studentId}_${date}",
                        studentId = studentId,
                        teacherId = teacherId,
                        groupId = studentGroupId,
                        date = date,
                        status = status,
                        note = note
                    )
                    attendanceDao?.upsertSingleAttendance(attendance.toEntity())

                    val upsertDto = UpsertAttendanceRequest(
                        teacherId = teacherId,
                        studentId = studentId,
                        groupId = studentGroupId,
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
                            entityId = "${studentId}_${date}",
                            payload = payload,
                            createdAt = System.currentTimeMillis(),
                            status = "PENDING",
                            teacherId = teacherId
                        )
                    )
                    OutboxSyncScheduler.scheduleSync()

                    Result.success(attendance)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception("فشل في حفظ سجل الحضور: ${ex.message}"))
            }
        }
    }

    override suspend fun recordBatchAttendance(
        date: String,
        records: List<BatchAttendanceItemDto>
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

        // 1. Primary Strategy: Try RPC record_batch_attendance
        try {
            val recordsArray = buildJsonArray {
                for (item in records) {
                    add(buildJsonObject {
                        put("student_id", item.studentId)
                        put("status", item.status)
                        if (item.note != null) {
                            put("note", item.note)
                        } else {
                            put("note", null as String?)
                        }
                    })
                }
            }

            val params = buildJsonObject {
                put("p_date", date)
                put("p_records", recordsArray)
            }

            val resultDto = client.postgrest.rpc(
                function = "record_batch_attendance",
                parameters = params
            ).decodeSingle<BatchAttendanceResultDto>()

            batchResult = resultDto.toBatchAttendanceResult().copy(syncStatus = BatchSyncStatus.SAVED_TO_CLOUD)
            cloudSaved = true
        } catch (rpcEx: Exception) {
            lastCloudError = rpcEx
            android.util.Log.w("SupabaseAttendanceRepo", "record_batch_attendance RPC call failed (${rpcEx.javaClass.simpleName}: ${rpcEx.message}). Falling back to direct Postgrest bulk upsert...")

            // 2. Secondary Strategy: Direct Postgrest Bulk Upsert on 'attendance' table
            try {
                val upsertRequests = records.map { item ->
                    val studentGroupId = studentEntitiesMap[item.studentId]?.groupId
                    UpsertAttendanceRequest(
                        teacherId = teacherId,
                        studentId = item.studentId,
                        groupId = studentGroupId,
                        date = date,
                        status = item.status,
                        note = item.note?.ifBlank { null }
                    )
                }

                client.postgrest["attendance"].upsert(upsertRequests) {
                    onConflict = "student_id,date"
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
                lastCloudError = bulkEx
                android.util.Log.e("SupabaseAttendanceRepo", "Direct Postgrest bulk upsert failed (${bulkEx.javaClass.simpleName}: ${bulkEx.message})")
            }
        }

        if (cloudSaved && batchResult != null) {
            try {
                val entities = records.map { item ->
                    val statusEnum = try { AttendanceStatus.valueOf(item.status) } catch(_: Exception) { AttendanceStatus.PRESENT }
                    val studentGroupId = studentEntitiesMap[item.studentId]?.groupId
                    Attendance(
                        attendanceId = "${item.studentId}_${date}",
                        studentId = item.studentId,
                        teacherId = teacherId,
                        groupId = studentGroupId,
                        date = date,
                        status = statusEnum,
                        note = item.note
                    ).toEntity()
                }
                attendanceDao?.upsertAttendance(entities)
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

                    val studentGroupId = studentEntitiesMap[record.studentId]?.groupId
                    val attendanceId = "${record.studentId}_${date}"
                    val attendance = Attendance(
                        attendanceId = attendanceId,
                        studentId = record.studentId,
                        teacherId = teacherId,
                        groupId = studentGroupId,
                        date = date,
                        status = statusEnum,
                        note = record.note
                    )
                    entities.add(attendance.toEntity())

                    val upsertDto = UpsertAttendanceRequest(
                        teacherId = teacherId,
                        studentId = record.studentId,
                        groupId = studentGroupId,
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
            if (attendance != null) {
                attendanceDao?.upsertSingleAttendance(attendance.toEntity())
            }
            attendance
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
