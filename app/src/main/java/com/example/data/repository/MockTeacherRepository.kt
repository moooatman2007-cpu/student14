package com.example.data.repository

import com.example.core.model.Teacher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockTeacherRepository(
    initialTeacher: Teacher? = null
) : TeacherRepository {
    private val _currentTeacher = MutableStateFlow<Teacher?>(initialTeacher)

    override fun getCurrentTeacher(): Flow<Teacher?> = _currentTeacher.asStateFlow()

    override suspend fun fetchCurrentTeacher(): Teacher? = _currentTeacher.value

    override suspend fun updateTeacherProfile(
        fullName: String,
        phoneNumber: String?,
        subject: String?,
        centerName: String?,
        educationalStage: String?,
        avatarUrl: String?
    ): Result<Teacher> {
        val current = _currentTeacher.value
        val updated = (current ?: Teacher(id = "teacher_default", email = "test@example.com")).copy(
            fullName = fullName,
            phoneNumber = phoneNumber,
            subject = subject,
            centerName = centerName,
            educationalStage = educationalStage,
            avatarUrl = avatarUrl
        )
        _currentTeacher.value = updated
        return Result.success(updated)
    }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String): Result<String> {
        val url = "https://mock-storage.com/avatars/$fileName"
        _currentTeacher.value = _currentTeacher.value?.copy(avatarUrl = url)
        return Result.success(url)
    }

    override suspend fun deleteAvatar(): Result<Unit> {
        _currentTeacher.value = _currentTeacher.value?.copy(avatarUrl = null)
        return Result.success(Unit)
    }
}
