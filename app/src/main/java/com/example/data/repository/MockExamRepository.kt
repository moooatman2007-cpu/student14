package com.example.data.repository

import com.example.core.model.Exam
import com.example.core.model.ExamSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

class MockExamRepository : ExamRepository {

    private val _exams = MutableStateFlow<List<Exam>>(emptyList())

    init {
        seedInitialExams()
    }

    private fun seedInitialExams() {
        val list = mutableListOf<Exam>()
        val examTemplates = listOf(
            Triple("امتحان التقييم الشهري - سبتمبر", "القرآن الكريم والتجويد", 100.0),
            Triple("اختبار أحكام التجويد العملي", "التجويد", 50.0),
            Triple("اختبار الحفظ التراكمي", "حفظ وتثبيت", 100.0)
        )

        val studentIds = (1..86).map { "s_" + String.format("%02d", it) }

        for (studentId in studentIds) {
            val studentNum = studentId.substringAfter("s_").toIntOrNull() ?: 1
            // 2 exams per student in September
            for (i in 0 until 2) {
                val tpl = examTemplates[i]
                val date = if (i == 0) "2026-09-10" else "2026-09-17"
                val basePercent = (75 + ((studentNum * 13 + i * 17) % 24)).coerceIn(60, 98)
                val score = (basePercent / 100.0) * tpl.third
                val note = when {
                    basePercent >= 90 -> "أداء استثنائي وإجابة نموذجية"
                    basePercent >= 80 -> "مستوى جيد جداً ومتقن"
                    basePercent >= 70 -> "مستوى جيد مع حاجة لمراجعة بعض الأحكام"
                    else -> "يحتاج تكثيف المراجعة قبل الاختبار القادم"
                }

                list.add(
                    Exam(
                        examId = "exam_${studentId}_$i",
                        studentId = studentId,
                        date = date,
                        examName = tpl.first,
                        subject = tpl.second,
                        score = score,
                        maxScore = tpl.third,
                        note = note,
                        createdAt = 1726650000000L - (i * 604800000L),
                        updatedAt = 1726650000000L - (i * 604800000L)
                    )
                )
            }
        }
        _exams.value = list
    }

    override fun getExamsForStudent(studentId: String): Flow<List<Exam>> {
        return _exams.map { list ->
            list.filter { it.studentId == studentId }.sortedByDescending { it.date }
        }
    }

    override fun getExamsForStudentByMonth(studentId: String, year: Int, month: Int): Flow<List<Exam>> {
        val monthPrefix = String.format("%04d-%02d", year, month)
        return _exams.map { list ->
            list.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
                .sortedByDescending { it.date }
        }
    }

    override suspend fun getExamSummaryForStudent(studentId: String, year: Int, month: Int): ExamSummary {
        val monthPrefix = String.format("%04d-%02d", year, month)
        val list = _exams.value.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }

        if (list.isEmpty()) {
            return ExamSummary()
        }

        val totalCount = list.size
        val percentages = list.map { if (it.maxScore > 0) ((it.score / it.maxScore) * 100.0).toFloat() else 0f }
        val avgPercentage = percentages.average().toFloat()
        val avgScore = list.map { it.score }.average()
        val highest = percentages.maxOrNull() ?: 0f
        val lowest = percentages.minOrNull() ?: 0f

        return ExamSummary(
            totalCount = totalCount,
            averageScore = (Math.round(avgScore * 10.0) / 10.0),
            averagePercentage = (Math.round(avgPercentage * 10.0) / 10.0).toFloat(),
            highestPercentage = highest,
            lowestPercentage = lowest
        )
    }

    override suspend fun getExamsCountThisMonth(year: Int, month: Int): Int {
        val monthPrefix = String.format(Locale.ENGLISH, "%04d-%02d", year, month)
        return _exams.value.count { it.date.startsWith(monthPrefix) }
    }

    override suspend fun addExam(
        studentId: String,
        date: String,
        examName: String,
        subject: String?,
        score: Double,
        maxScore: Double,
        note: String?
    ): Result<Exam> {
        if (examName.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم الامتحان مطلوب"))
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

        val newExam = Exam(
            examId = "exam_" + UUID.randomUUID().toString().take(8),
            studentId = studentId,
            date = date.trim(),
            examName = examName.trim(),
            subject = subject?.trim()?.ifBlank { null },
            score = score,
            maxScore = maxScore,
            note = note?.trim()?.ifBlank { null },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        _exams.update { listOf(newExam) + it }
        return Result.success(newExam)
    }

    override suspend fun updateExam(exam: Exam): Result<Exam> {
        if (exam.examName.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم الامتحان مطلوب"))
        }
        if (exam.maxScore <= 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة الكاملة يجب أن تكون أكبر من صفر"))
        }
        if (exam.score < 0.0) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون سالبة"))
        }
        if (exam.score > exam.maxScore) {
            return Result.failure(IllegalArgumentException("الدرجة لا يمكن أن تكون أكبر من الدرجة الكاملة"))
        }

        var found = false
        _exams.update { list ->
            list.map {
                if (it.examId == exam.examId) {
                    found = true
                    exam.copy(updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        return if (found) Result.success(exam) else Result.failure(NoSuchElementException("الامتحان غير موجود"))
    }

    override suspend fun deleteExam(examId: String): Result<Unit> {
        _exams.update { list ->
            list.filterNot { it.examId == examId }
        }
        return Result.success(Unit)
    }

    override suspend fun getExamById(examId: String): Exam? {
        return _exams.value.find { it.examId == examId }
    }

    override fun getAllExamsForTeacher(): Flow<List<Exam>> {
        return _exams.asStateFlow()
    }
}
