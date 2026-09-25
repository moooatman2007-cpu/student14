package com.example.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockSettingsRepository : SettingsRepository {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val _teacherName = MutableStateFlow("أستاذ محمد أحمد")

    override fun getThemeMode(): Flow<ThemeMode> = _themeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override fun getTeacherName(): Flow<String> = _teacherName.asStateFlow()

    override suspend fun setTeacherName(name: String) {
        _teacherName.value = name
    }
}
