package com.example.core.model

data class TeacherStats(
    val totalStudents: Int = 0,
    val gradeCounts: Map<String, Int> = emptyMap(),
    val whatsappEnabledCount: Int = 0,
    val hasAlternativePhoneCount: Int = 0
)
