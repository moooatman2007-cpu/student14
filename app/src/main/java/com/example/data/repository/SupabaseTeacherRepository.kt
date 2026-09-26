package com.example.data.repository

import com.example.core.model.Teacher
import com.example.core.model.UpdateTeacherRequest
import com.example.core.model.UpsertTeacherRequest
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SupabaseTeacherRepository : TeacherRepository {
    private val client = SupabaseClientProvider.client
    private val _currentTeacher = MutableStateFlow<Teacher?>(null)

    fun clearCache() {
        _currentTeacher.value = null
    }

    override fun getCurrentTeacher(): Flow<Teacher?> = _currentTeacher.asStateFlow()

    override suspend fun fetchCurrentTeacher(): Teacher? = withContext(Dispatchers.IO) {
        val mockId = SupabaseClientProvider.mockTeacherId
        if (mockId != null) {
            val cached = _currentTeacher.value
            if (cached != null && cached.id == mockId) return@withContext cached
            val mockTeacher = Teacher(id = mockId, email = "mock@teacher.com", fullName = "المدرس الحالي")
            _currentTeacher.value = mockTeacher
            return@withContext mockTeacher
        }

        try {
            val user = client.auth.currentUserOrNull() ?: return@withContext null
            val teacherId = user.id
            val result = try {
                client.postgrest["teachers"]
                    .select {
                        filter {
                            eq("id", teacherId)
                        }
                    }
                    .decodeSingleOrNull<Teacher>()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val metaStage = user.userMetadata?.get("educational_stage")?.toString()?.replace("\"", "")
            val finalTeacher = (result ?: Teacher(
                id = teacherId,
                email = user.email ?: "",
                fullName = user.userMetadata?.get("full_name")?.toString()?.replace("\"", "") ?: "",
                educationalStage = metaStage
            )).let { t ->
                if (t.educationalStage == null && !metaStage.isNullOrBlank()) {
                    t.copy(educationalStage = metaStage)
                } else t
            }
            _currentTeacher.value = finalTeacher
            finalTeacher
        } catch (e: Exception) {
            e.printStackTrace()
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                val metaStage = user.userMetadata?.get("educational_stage")?.toString()?.replace("\"", "")
                val fallback = Teacher(id = user.id, email = user.email ?: "", educationalStage = metaStage)
                _currentTeacher.value = fallback
                fallback
            } else null
        }
    }

    override suspend fun updateTeacherProfile(
        fullName: String,
        phoneNumber: String?,
        subject: String?,
        centerName: String?,
        educationalStage: String?,
        avatarUrl: String?
    ): Result<Teacher> = withContext(Dispatchers.IO) {
        try {
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("جلسة تسجيل الدخول غير صحيحة أو انتهت."))

            val teacherId = user.id
            val updateDto = UpdateTeacherRequest(
                fullName = fullName,
                phoneNumber = phoneNumber?.ifBlank { null },
                subject = subject?.ifBlank { null },
                centerName = centerName?.ifBlank { null },
                educationalStage = educationalStage?.ifBlank { null },
                avatarUrl = avatarUrl
            )

            try {
                client.postgrest["teachers"]
                    .update(updateDto) {
                        filter {
                            eq("id", teacherId)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                val upsertDto = UpsertTeacherRequest(
                    id = teacherId,
                    email = user.email ?: "",
                    fullName = fullName,
                    phoneNumber = phoneNumber?.ifBlank { null },
                    subject = subject?.ifBlank { null },
                    centerName = centerName?.ifBlank { null },
                    educationalStage = educationalStage?.ifBlank { null },
                    avatarUrl = avatarUrl
                )
                client.postgrest["teachers"].upsert(upsertDto)
            }

            val updated = Teacher(
                id = teacherId,
                email = user.email ?: "",
                fullName = fullName,
                phoneNumber = phoneNumber?.ifBlank { null },
                subject = subject?.ifBlank { null },
                centerName = centerName?.ifBlank { null },
                educationalStage = educationalStage?.ifBlank { null },
                avatarUrl = avatarUrl
            )

            _currentTeacher.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            e.printStackTrace()
            val rawMsg = e.localizedMessage ?: e.message ?: e.toString()
            Result.failure(Exception("فشل حفظ البيانات: $rawMsg"))
        }
    }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("جلسة تسجيل الدخول غير صحيحة أو انتهت."))

            val teacherId = user.id
            val bucket = client.storage.from("teacher-avatars")
            val filePath = "$teacherId/$fileName"

            bucket.upload(filePath, bytes) {
                upsert = true
            }
            val publicUrl = bucket.publicUrl(filePath)
            Result.success(publicUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            val rawMsg = e.localizedMessage ?: e.message ?: e.toString()
            Result.failure(Exception("فشل رفع الصورة: $rawMsg"))
        }
    }

    override suspend fun deleteAvatar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(Exception("غير مسجل الدخول."))

            val current = _currentTeacher.value
            val avatarUrl = current?.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                val bucket = client.storage.from("teacher-avatars")
                val fileName = avatarUrl.substringAfterLast("/")
                if (fileName.isNotBlank()) {
                    try {
                        bucket.delete("${user.id}/$fileName")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            updateTeacherProfile(
                fullName = current?.fullName ?: "",
                phoneNumber = current?.phoneNumber,
                subject = current?.subject,
                centerName = current?.centerName,
                avatarUrl = null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            val rawMsg = e.localizedMessage ?: e.message ?: e.toString()
            Result.failure(Exception("فشل حذف الصورة: $rawMsg"))
        }
    }
}
