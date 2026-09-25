package com.example.data.repository

import com.example.core.model.Recitation
import com.example.core.model.RecitationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

class MockRecitationRepository : RecitationRepository {

    private val _recitations = MutableStateFlow<List<Recitation>>(emptyList())

    init {
        seedInitialRecitations()
    }

    private fun seedInitialRecitations() {
        val list = mutableListOf<Recitation>()
        val titles = listOf(
            Pair("سورة البقرة", "من الآية 1 إلى 20"),
            Pair("سورة البقرة", "من الآية 21 إلى 40"),
            Pair("سورة آل عمران", "من الآية 1 إلى 25"),
            Pair("سورة النساء", "من الآية 1 إلى 15"),
            Pair("سورة المائدة", "من الآية 1 إلى 20"),
            Pair("سورة الكهف", "من الآية 1 إلى 31"),
            Pair("سورة مريم", "كامل السورة"),
            Pair("سورة يس", "من الآية 1 إلى 30")
        )

        val studentIds = (1..86).map { "s_" + String.format("%02d", it) }

        for (studentId in studentIds) {
            val studentNum = studentId.substringAfter("s_").toIntOrNull() ?: 1
            val recitationCount = 3 + (studentNum % 5) // 3 to 7 recitations
            for (i in 0 until recitationCount) {
                val pair = titles[i % titles.size]
                val day = String.format("%02d", 2 + i * 2)
                val date = "2026-09-$day"
                val score = ((70 + ((studentNum * 7 + i * 11) % 31)) / 10.0).coerceIn(6.0, 10.0)
                val note = when {
                    score >= 9.5 -> "ممتاز جداً، إتقان تام للتجويد ومخارج الحروف"
                    score >= 8.5 -> "جيد جداً، يحتاج انتباه يسير للمدود"
                    score >= 7.5 -> "جيد، يرجى مراجعة الآيات الأخيرة"
                    else -> "مقبول، يحتاج إعادة تسميع غداً"
                }

                list.add(
                    Recitation(
                        recitationId = "rec_${studentId}_$i",
                        studentId = studentId,
                        date = date,
                        title = pair.first,
                        content = pair.second,
                        score = score,
                        maxScore = 10.0,
                        note = note,
                        createdAt = 1726650000000L - (i * 86400000L),
                        updatedAt = 1726650000000L - (i * 86400000L)
                    )
                )
            }
        }
        _recitations.value = list
    }

    override fun getRecitationsForStudent(studentId: String): Flow<List<Recitation>> {
        return _recitations.map { list ->
            list.filter { it.studentId == studentId }.sortedByDescending { it.date }
        }
    }

    override fun getRecitationsForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Recitation>> {
        val monthPrefix = String.format("%04d-%02d", year, month)
        return _recitations.map { list ->
            list.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
                .sortedByDescending { it.date }
        }
    }

    override suspend fun getRecitationSummaryForStudent(studentId: String, year: Int, month: Int): RecitationSummary {
        val monthPrefix = String.format("%04d-%02d", year, month)
        val list = _recitations.value.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
        
        if (list.isEmpty()) {
            return RecitationSummary()
        }

        val totalCount = list.size
        val avgScore = list.map { it.score }.average()
        val avgMax = list.map { it.maxScore }.average()
        val avgPercentage = if (avgMax > 0) ((avgScore / avgMax) * 100.0).toFloat() else 0f

        return RecitationSummary(
            totalCount = totalCount,
            averageScore = (Math.round(avgScore * 10.0) / 10.0),
            averageMaxScore = (Math.round(avgMax * 10.0) / 10.0),
            averagePercentage = avgPercentage
        )
    }

    override suspend fun getRecitationsCountThisMonth(year: Int, month: Int): Int {
        val monthPrefix = String.format(Locale.ENGLISH, "%04d-%02d", year, month)
        return _recitations.value.count { it.date.startsWith(monthPrefix) }
    }

    override suspend fun addRecitation(
        studentId: String,
        date: String,
        title: String,
        content: String,
        score: Double,
        maxScore: Double,
        note: String?
    ): Result<Recitation> {
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم التسميع مطلوب"))
        }
        if (maxScore <= 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة الكاملة يجب أن تكون أكبر من صفر"))
        }
        if (score < 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون سالبة"))
        }
        if (score > maxScore) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون أكبر من الدرجة الكاملة"))
        }

        val newRecitation = Recitation(
            recitationId = "rec_" + UUID.randomUUID().toString().take(8),
            studentId = studentId,
            date = date.trim(),
            title = title.trim(),
            content = content.trim(),
            score = score,
            maxScore = maxScore,
            note = note?.trim()?.ifBlank { null },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        _recitations.update { listOf(newRecitation) + it }
        return Result.success(newRecitation)
    }

    override suspend fun updateRecitation(recitation: Recitation): Result<Recitation> {
        if (recitation.title.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم التسميع مطلوب"))
        }
        if (recitation.maxScore <= 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة الكاملة يجب أن تكون أكبر من صفر"))
        }
        if (recitation.score < 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون سالبة"))
        }
        if (recitation.score > recitation.maxScore) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون أكبر من الدرجة الكاملة"))
        }

        var found = false
        _recitations.update { list ->
            list.map {
                if (it.recitationId == recitation.recitationId) {
                    found = true
                    recitation.copy(updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        return if (found) Result.success(recitation) else Result.failure(NoSuchElementException("التسميع غير موجود"))
    }

    override suspend fun deleteRecitation(recitationId: String): Result<Unit> {
        _recitations.update { list ->
            list.filterNot { it.recitationId == recitationId }
        }
        return Result.success(Unit)
    }

    override suspend fun getRecitationById(recitationId: String): Recitation? {
        return _recitations.value.find { it.recitationId == recitationId }
    }

    override fun getAllRecitationsForTeacher(): Flow<List<Recitation>> {
        return _recitations.asStateFlow()
    }
}
