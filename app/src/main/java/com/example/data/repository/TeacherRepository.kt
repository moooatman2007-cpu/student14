package com.example.data.repository

import com.example.core.model.Teacher
import kotlinx.coroutines.flow.Flow

interface TeacherRepository {
    fun getCurrentTeacher(): Flow<Teacher?>
    suspend fun fetchCurrentTeacher(): Teacher?
    suspend fun updateTeacherProfile(
        fullName: String,
        phoneNumber: String?,
        subject: String?,
        centerName: String?,
        educationalStage: String? = null,
        avatarUrl: String?
    ): Result<Teacher>
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String = "image/jpeg"): Result<String>
    suspend fun deleteAvatar(): Result<Unit>
}
