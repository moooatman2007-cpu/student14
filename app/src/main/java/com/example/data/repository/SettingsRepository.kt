package com.example.data.repository

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

interface SettingsRepository {
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun getTeacherName(): Flow<String>
    suspend fun setTeacherName(name: String)
}
