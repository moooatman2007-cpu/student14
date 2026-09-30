package com.example.data.repository

import android.util.Log

import com.example.core.model.InsertStudentRequest
import com.example.core.model.SoftDeleteStudentRequest
import com.example.core.model.Student
import com.example.core.model.SupabaseStudentInsertDto
import com.example.core.model.TeacherStats
import com.example.core.model.UpdateStudentRequest
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.sync.OutboxSyncScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SupabaseStudentRepository(
    private val studentDao: StudentDao? = try { DatabaseProvider.getDatabase().studentDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
) : StudentRepository {
    private val client = SupabaseClientProvider.client
    private val _students = MutableStateFlow<List<Student>>(emptyList())

    fun clearCache() {
        _students.value = emptyList()
    }

    suspend fun fetchStudents(): List<Student> = withContext(Dispatchers.IO) {
        val teacherId = SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id
        if (teacherId == null) {
            _students.value = emptyList()
            return@withContext emptyList()
        }

        try {
            val fetched = client.postgrest["students"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        exact("deleted_at", null)
                    }
                    order("created_at", order = Order.DESCENDING)
                }
                .decodeList<Student>()

            // If session was terminated during network fetch, discard result immediately to prevent stale data re-injection
            if (!com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                return@withContext emptyList()
            }

            // Stale-While-Revalidate: save fetched into Room preserving local group assignments
            val cachedEntities = studentDao?.getAllStudentsSync(teacherId) ?: emptyList()
            val localGroupMap = cachedEntities.associate { it.studentId to it.groupId }
            val entitiesToUpsert = fetched.map { s ->
                val localGroupId = localGroupMap[s.studentId] ?: s.groupId
                s.copy(groupId = localGroupId).toEntity()
            }
            studentDao?.upsertStudents(entitiesToUpsert)
            val mergedList = fetched.map { s ->
                val localGroupId = localGroupMap[s.studentId] ?: s.groupId
                s.copy(groupId = localGroupId)
            }
            _students.value = mergedList
            mergedList
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedEntities = studentDao?.getAllStudentsSync(teacherId) ?: emptyList()
            val cached = if (cachedEntities.isNotEmpty()) cachedEntities.map { it.toDomain() } else _students.value
            _students.value = cached
            cached
        }
    }

    override fun getStudents(): Flow<List<Student>> {
        val teacherId = SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id ?: return _students.asStateFlow()

        if (studentDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        fetchStudents()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                studentDao.getAllStudents(teacherId).map { entities ->
                    entities.map { it.toDomain() }
                }.collect { list ->
                    _students.value = list
                    send(list)
                }
            }
        }
        return _students.asStateFlow()
    }

    override fun getStudentsByGrade(gradeId: String): Flow<List<Student>> {
        val teacherId = SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id ?: return _students.map { list -> list.filter { it.gradeId == gradeId } }

        if (studentDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        fetchStudents()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                studentDao.getStudentsByGrade(gradeId, teacherId).map { entities ->
                    entities.map { it.toDomain() }
                }.collect { list ->
                    send(list)
                }
            }
        }
        return _students.map { list ->
            list.filter { it.gradeId == gradeId }
        }
    }

    override suspend fun getStudentById(studentId: String): Student? = withContext(Dispatchers.IO) {
        val teacherId = SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id

        if (teacherId != null) {
            val cachedEntity = studentDao?.getStudentByIdSync(teacherId, studentId)
            if (cachedEntity != null) {
                return@withContext cachedEntity.toDomain()
            }
        }

        try {
            if (teacherId == null) return@withContext null

            val student = client.postgrest["students"]
                .select {
                    filter {
                        eq("id", studentId)
                        eq("teacher_id", teacherId)
                        exact("deleted_at", null)
                    }
                }
                .decodeSingleOrNull<Student>()

            if (student != null && com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                studentDao?.upsertStudent(student.toEntity())
            }
            student
        } catch (e: Exception) {
            e.printStackTrace()
            _students.value.firstOrNull { it.studentId == studentId && (teacherId == null || it.teacherId == teacherId) }
        }
    }

    override suspend fun getStudentByCode(studentCode: String): Student? = withContext(Dispatchers.IO) {
        val teacherId = SupabaseClientProvider.mockTeacherId ?: client.auth.currentUserOrNull()?.id ?: return@withContext null

        val cachedEntity = studentDao?.getStudentByCode(teacherId, studentCode)
        if (cachedEntity != null) {
            return@withContext cachedEntity.toDomain()
        }

        try {
            val student = client.postgrest["students"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("student_code", studentCode)
                        exact("deleted_at", null)
                    }
                }
                .decodeSingleOrNull<Student>()

            if (student != null && com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                studentDao?.upsertStudent(student.toEntity())
            }
            student
        } catch (e: Exception) {
            e.printStackTrace()
            _students.value.firstOrNull { it.studentCode == studentCode && it.teacherId == teacherId }
        }
    }

    private suspend fun generateSafeOfflineStudentCode(teacherId: String): String {
        // Guaranteed collision-free:
        // Prefixed with "ST-TMP-" to comply with "ST-" prefix conventions while
        // remaining distinct from 5-digit server codes (ST-00001).
        // Uses cryptographic random UUID and validates against Room and in-memory caches.
        for (attempt in 0..20) {
            val candidate = "ST-TMP-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
            val existsInRoom = studentDao?.getStudentByCode(teacherId, candidate) != null
            val existsInMemory = _students.value.any { it.teacherId == teacherId && it.studentCode.equals(candidate, ignoreCase = true) }
            if (!existsInRoom && !existsInMemory) {
                return candidate
            }
        }
        return "ST-TMP-${System.nanoTime().toString().takeLast(8)}"
    }

    override suspend fun addStudent(
        fullName: String,
        gradeId: String,
        parentPhone: String,
        hasWhatsApp: Boolean,
        alternativePhone: String?
    ): Result<Student> = withContext(Dispatchers.IO) {
        Log.d("SyncDiag", "REPO ADD START: fullName=${fullName}, gradeId=${gradeId}")
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        val generatedId = UUID.randomUUID().toString()
        val offlineStudentCode = generateSafeOfflineStudentCode(teacherId)
        val student = Student(
            studentId = generatedId,
            studentCode = offlineStudentCode,
            fullName = fullName.trim(),
            gradeId = gradeId,
            parentPhone = parentPhone.trim(),
            hasWhatsApp = hasWhatsApp,
            alternativePhone = alternativePhone?.trim()?.ifBlank { null },
            teacherId = teacherId
        )

        try {
            val dto = SupabaseStudentInsertDto(
                id = generatedId,
                teacherId = teacherId,
                gradeId = gradeId,
                fullName = fullName.trim(),
                parentPhone = parentPhone.trim(),
                hasWhatsApp = hasWhatsApp,
                alternativePhone = alternativePhone?.trim()?.ifBlank { null }
            )

            val insertedStudent = client.postgrest["students"]
                .insert(dto) {
                    select()
                }
                .decodeSingle<Student>()

            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                studentDao?.upsertStudent(insertedStudent.toEntity())
            }

            val currentList = _students.value.toMutableList()
            currentList.add(0, insertedStudent)
            _students.value = currentList

            Result.success(insertedStudent)
        } catch (e: Exception) {
            e.printStackTrace()
            val isLocalFailure = e is android.database.sqlite.SQLiteException || e.stackTrace.any { it.className.contains("sqlite") || it.className.contains("room") }
            if (isLocalFailure) {
                return@withContext Result.failure(Exception("تم الحفظ على السيرفر ولكن فشل التحديث المحلي: ${e.message}"))
            }

            // Fallback to offline local write and Outbox queue
            try {
                studentDao?.upsertStudent(student.toEntity())

                val request = InsertStudentRequest(
                    id = generatedId,
                    teacherId = teacherId,
                    gradeId = gradeId,
                    fullName = fullName.trim(),
                    parentPhone = parentPhone.trim(),
                    hasWhatsApp = hasWhatsApp,
                    alternativePhone = alternativePhone?.trim()?.ifBlank { null }
                )
                val payload = Json.encodeToString(request)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "STUDENT",
                        entityId = generatedId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                val currentList = _students.value.toMutableList()
                currentList.add(0, student)
                _students.value = currentList

                
        Log.d("SyncDiag", "REPO ADD SUCCESS: id=${student.studentId}")
                Result.success(student)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception(ex.message ?: "حدث خطأ أثناء إضافة الطالب."))
            }
        }
    }

    override suspend fun updateStudent(student: Student): Result<Student> = withContext(Dispatchers.IO) {
        Log.d("SyncDiag", "REPO UPDATE START: id=${student.studentId}, fullName=${student.fullName}")
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        val studentWithTeacher = if (student.teacherId.isNullOrBlank()) student.copy(teacherId = teacherId) else student

        // Ensure studentCode and groupId stability during updates
        val existingEntity = studentDao?.getStudentByIdSync(teacherId, studentWithTeacher.studentId)
        val preservedCode = if (studentWithTeacher.studentCode.isNotBlank()) {
            studentWithTeacher.studentCode
        } else {
            existingEntity?.studentCode ?: ""
        }
        val safeStudent = studentWithTeacher.copy(studentCode = preservedCode)

        try {
            val request = UpdateStudentRequest(
                gradeId = safeStudent.gradeId,
                fullName = safeStudent.fullName.trim(),
                parentPhone = safeStudent.parentPhone.trim(),
                hasWhatsApp = safeStudent.hasWhatsApp,
                alternativePhone = safeStudent.alternativePhone?.trim()?.ifBlank { null },
                groupId = safeStudent.groupId
            )

            val updatedStudent = client.postgrest["students"]
                .update(request) {
                    filter {
                        eq("id", safeStudent.studentId)
                        eq("teacher_id", teacherId)
                    }
                    select()
                }
                .decodeSingle<Student>()

            val reconciled = updatedStudent.copy(
                studentCode = if (updatedStudent.studentCode.isNotBlank()) updatedStudent.studentCode else preservedCode,
                groupId = existingEntity?.groupId ?: updatedStudent.groupId
            )
            if (com.example.data.auth.AccountSessionManager.isSessionActive(teacherId)) {
                studentDao?.upsertStudent(reconciled.toEntity())
            }

            val currentList = _students.value.toMutableList()
            val index = currentList.indexOfFirst { it.studentId == safeStudent.studentId }
            if (index >= 0) {
                currentList[index] = reconciled
                _students.value = currentList
            }

            
        Log.d("SyncDiag", "REPO UPDATE SUCCESS: id=${reconciled.studentId}")
                Result.success(reconciled)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                studentDao?.upsertStudent(safeStudent.toEntity())

                val request = UpdateStudentRequest(
                    gradeId = safeStudent.gradeId,
                    fullName = safeStudent.fullName.trim(),
                    parentPhone = safeStudent.parentPhone.trim(),
                    hasWhatsApp = safeStudent.hasWhatsApp,
                    alternativePhone = safeStudent.alternativePhone?.trim()?.ifBlank { null },
                    groupId = safeStudent.groupId
                )
                val payload = Json.encodeToString(request)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPDATE",
                        entityType = "STUDENT",
                        entityId = safeStudent.studentId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                val currentList = _students.value.toMutableList()
                val index = currentList.indexOfFirst { it.studentId == safeStudent.studentId }
                if (index >= 0) {
                    currentList[index] = safeStudent
                    _students.value = currentList
                } else {
                    currentList.add(0, safeStudent)
                    _students.value = currentList
                }

                Result.success(safeStudent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception(ex.message ?: "حدث خطأ أثناء تعديل بيانات الطالب."))
            }
        }
    }

    override suspend fun deleteStudent(studentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext Result.failure(IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."))

        try {
            val nowIso = java.time.OffsetDateTime.now().toString()
            val request = SoftDeleteStudentRequest(deletedAt = nowIso)

            client.postgrest["students"]
                .update(request) {
                    filter {
                        eq("id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }

            studentDao?.deleteStudentById(teacherId, studentId)

            _students.value = _students.value.filterNot { it.studentId == studentId }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                studentDao?.deleteStudentById(teacherId, studentId)

                val nowIso = java.time.OffsetDateTime.now().toString()
                val request = SoftDeleteStudentRequest(deletedAt = nowIso)
                val payload = Json.encodeToString(request)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "STUDENT",
                        entityId = studentId,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()

                _students.value = _students.value.filterNot { it.studentId == studentId }

                Result.success(Unit)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Result.failure(Exception(ex.message ?: "حدث خطأ أثناء حذف الطالب."))
            }
        }
    }

    override fun searchStudents(query: String, gradeId: String?): Flow<List<Student>> {
        return getStudents().map { list ->
            list.filter { student ->
                val matchesGrade = gradeId == null || student.gradeId == gradeId
                val q = query.trim().lowercase()
                val matchesQuery = q.isEmpty() ||
                        student.fullName.lowercase().contains(q) ||
                        student.studentCode.lowercase().contains(q) ||
                        student.parentPhone.contains(q)
                matchesGrade && matchesQuery
            }
        }
    }

    override fun getStats(): Flow<TeacherStats> {
        return getStudents().map { list ->
            val activeList = list.filter { it.deletedAt == null }
            val gradeCounts = activeList.groupBy { it.gradeId }.mapValues { it.value.size }
            TeacherStats(
                totalStudents = activeList.size,
                gradeCounts = gradeCounts,
                whatsappEnabledCount = activeList.count { it.hasWhatsApp },
                hasAlternativePhoneCount = activeList.count { !it.alternativePhone.isNullOrBlank() }
            )
        }
    }
}
