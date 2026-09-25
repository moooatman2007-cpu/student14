package com.example.data.repository

import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Teacher
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.GradeDao
import com.example.data.local.dao.OutboxDao
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SupabaseGradeRepository(
    private val gradeDao: GradeDao? = try { DatabaseProvider.getDatabase().gradeDao() } catch (_: Exception) { null },
    private val outboxDao: OutboxDao? = try { DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null },
    private val studentRepository: StudentRepository? = null
) : GradeRepository {
    private val client = SupabaseClientProvider.client
    private val _grades = MutableStateFlow<List<Grade>>(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            fetchGrades()
        }
    }

    suspend fun fetchGrades(): List<Grade> = withContext(Dispatchers.IO) {
        return@withContext refreshGrades()
    }

    override suspend fun refreshGrades(stage: String?): List<Grade> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext emptyList()

        try {
            // Resolve teacher's educational stage
            val teacherFromDb = if (stage.isNullOrBlank()) {
                try {
                    client.postgrest["teachers"]
                        .select {
                            filter { eq("id", teacherId) }
                        }
                        .decodeSingleOrNull<Teacher>()
                } catch (e: Exception) {
                    null
                }
            } else null

            val metaStage = try {
                user?.userMetadata?.get("educational_stage")?.toString()?.replace("\"", "")
            } catch (_: Exception) {
                null
            }
            val resolvedStage = stage?.ifBlank { null }
                ?: teacherFromDb?.educationalStage?.ifBlank { null }
                ?: metaStage?.ifBlank { null }
                ?: EducationalStages.PREPARATORY

            val fetched = client.postgrest["grades"]
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                    }
                    order("display_order", order = Order.ASCENDING)
                }
                .decodeList<Grade>()

            val ensured = ensureStageGrades(teacherId, resolvedStage, fetched)

            // Normalize grade names if needed
            val normalized = ensured.map { g ->
                val norm = EducationalStages.normalizeGradeName(g.name)
                if (norm != g.name) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            client.postgrest["grades"].update(mapOf("name" to norm)) {
                                filter { eq("id", g.id) }
                            }
                        } catch (ignored: Exception) {}
                    }
                    g.copy(name = norm, teacherId = teacherId)
                } else {
                    if (g.teacherId == null) g.copy(teacherId = teacherId) else g
                }
            }

            // Strictly isolate to the teacher's stage, falling back to all normalized grades if filter would empty a non-empty list
            val matchedGrades = normalized
                .filter { EducationalStages.isGradeMatchingStage(it.name, resolvedStage) }
                .sortedBy { it.displayOrder }

            val stageGrades = if (matchedGrades.isNotEmpty() || normalized.isEmpty()) {
                matchedGrades
            } else {
                normalized.sortedBy { it.displayOrder }
            }

            // Save fetched grades into Room Cache
            gradeDao?.upsertGrades(stageGrades.map { it.toEntity() })

            _grades.value = stageGrades
            stageGrades
        } catch (e: Exception) {
            e.printStackTrace()
            // In case of network failure / offline mode, load from Room cache!
            val cachedEntities = gradeDao?.getAllGradesSync(teacherId) ?: emptyList()
            val cached = if (cachedEntities.isNotEmpty()) {
                cachedEntities.map { it.toDomain() }
            } else {
                _grades.value
            }
            _grades.value = cached
            cached
        }
    }

    private suspend fun ensureStageGrades(
        userId: String,
        stage: String,
        existingGrades: List<Grade>
    ): List<Grade> {
        val expectedNames = EducationalStages.getGradeNamesForStage(stage)
        val existingNormalized = existingGrades.map { EducationalStages.normalizeGradeName(it.name) }.toSet()

        val missingNames = expectedNames.filter { !existingNormalized.contains(it) }

        if (missingNames.isNotEmpty()) {
            for (missingName in missingNames) {
                val order = expectedNames.indexOf(missingName) + 1
                try {
                    val dto = mapOf(
                        "teacher_id" to userId,
                        "name" to missingName,
                        "display_order" to order
                    )
                    client.postgrest["grades"].insert(dto)
                } catch (e: Exception) {
                    // Ignore on conflict
                }
            }

            return try {
                client.postgrest["grades"]
                    .select {
                        filter { eq("teacher_id", userId) }
                        order("display_order", order = Order.ASCENDING)
                    }
                    .decodeList<Grade>()
            } catch (e: Exception) {
                existingGrades
            }
        }

        return existingGrades
    }

    override suspend fun ensureGradesForStage(stage: String?): List<Grade> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext emptyList()

        try {
            val targetStage = stage?.ifBlank { null } ?: EducationalStages.PREPARATORY

            val fetched = client.postgrest["grades"]
                .select {
                    filter { eq("teacher_id", teacherId) }
                    order("display_order", order = Order.ASCENDING)
                }
                .decodeList<Grade>()

            val ensured = ensureStageGrades(teacherId, targetStage, fetched)
            val filtered = ensured
                .filter { EducationalStages.isGradeMatchingStage(it.name, targetStage) }
                .sortedBy { it.displayOrder }
                .map { if (it.teacherId == null) it.copy(teacherId = teacherId) else it }

            gradeDao?.upsertGrades(filtered.map { it.toEntity() })

            _grades.value = filtered
            filtered
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedEntities = gradeDao?.getAllGradesSync(teacherId) ?: emptyList()
            if (cachedEntities.isNotEmpty()) {
                cachedEntities.map { it.toDomain() }
            } else {
                _grades.value
            }
        }
    }

    override fun getGrades(): Flow<List<Grade>> {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id

        if (teacherId != null && gradeDao != null) {
            return channelFlow {
                launch(Dispatchers.IO) {
                    try {
                        fetchGrades()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                gradeDao.getAllGrades(teacherId).map { entities ->
                    entities.map { it.toDomain() }
                }.collect { list ->
                    _grades.value = list
                    send(list)
                }
            }
        }
        return _grades.asStateFlow()
    }

    override suspend fun getGradeById(id: String): Grade? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext null
        try {
            val cachedFromRoom = gradeDao?.getGradeById(teacherId, id)?.toDomain()
            if (cachedFromRoom != null) return@withContext cachedFromRoom

            val cached = _grades.value.firstOrNull { it.id == id && it.teacherId == teacherId }
            if (cached != null) return@withContext cached

            val fetched = client.postgrest["grades"]
                .select {
                    filter {
                        eq("id", id)
                        eq("teacher_id", teacherId)
                    }
                }
                .decodeSingleOrNull<Grade>()

            if (fetched != null) {
                gradeDao?.upsertGrade(fetched.copy(teacherId = teacherId).toEntity())
            }
            fetched
        } catch (e: Exception) {
            e.printStackTrace()
            gradeDao?.getGradeById(teacherId, id)?.toDomain() ?: _grades.value.firstOrNull { it.id == id && it.teacherId == teacherId }
        }
    }

    override suspend fun addGrade(grade: Grade): Boolean = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext false
        val gradeWithTeacher = grade.copy(teacherId = teacherId)
        try {
            gradeDao?.upsertGrade(gradeWithTeacher.toEntity())

            val dto = mapOf(
                "id" to gradeWithTeacher.id,
                "teacher_id" to teacherId,
                "name" to gradeWithTeacher.name,
                "display_order" to gradeWithTeacher.displayOrder
            )
            client.postgrest["grades"].insert(dto)
            fetchGrades()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                gradeDao?.upsertGrade(gradeWithTeacher.toEntity())

                val payload = kotlinx.serialization.json.Json.encodeToString(gradeWithTeacher)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "GRADE",
                        entityId = gradeWithTeacher.id,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    override suspend fun updateGrade(grade: Grade): Boolean = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext false
        val gradeWithTeacher = grade.copy(teacherId = teacherId)
        try {
            gradeDao?.upsertGrade(gradeWithTeacher.toEntity())
            val dto = mapOf(
                "name" to gradeWithTeacher.name,
                "display_order" to gradeWithTeacher.displayOrder
            )
            client.postgrest["grades"].update(dto) {
                filter {
                    eq("id", gradeWithTeacher.id)
                    eq("teacher_id", teacherId)
                }
            }
            fetchGrades()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                gradeDao?.upsertGrade(gradeWithTeacher.toEntity())

                val payload = kotlinx.serialization.json.Json.encodeToString(gradeWithTeacher)
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "UPDATE",
                        entityType = "GRADE",
                        entityId = gradeWithTeacher.id,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    override suspend fun deleteGrade(id: String): Boolean = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id ?: return@withContext false
        try {
            client.postgrest["grades"].delete {
                filter {
                    eq("id", id)
                    eq("teacher_id", teacherId)
                }
            }
            gradeDao?.deleteGrade(teacherId, id)
            fetchGrades()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback
            try {
                gradeDao?.deleteGrade(teacherId, id)

                val payload = "{\"id\":\"$id\"}"
                outboxDao?.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "DELETE",
                        entityType = "GRADE",
                        entityId = id,
                        payload = payload,
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
                OutboxSyncScheduler.scheduleSync()
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }
}
