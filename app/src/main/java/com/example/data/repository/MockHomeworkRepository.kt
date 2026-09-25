package com.example.data.repository

import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

class MockHomeworkRepository(
    initialHomeworks: List<Homework> = emptyList()
) : HomeworkRepository {

    private val _homeworks = MutableStateFlow(initialHomeworks)

    override fun getHomeworkForStudent(studentId: String): Flow<List<Homework>> {
        return _homeworks.asStateFlow().map { list ->
            list.filter { it.studentId == studentId }
        }
    }

    override fun getHomeworkForStudentByMonth(
        studentId: String,
        year: Int,
        month: Int
    ): Flow<List<Homework>> {
        val monthPrefix = String.format(Locale.ENGLISH, "%04d-%02d", year, month)
        return _homeworks.asStateFlow().map { list ->
            list.filter { it.studentId == studentId && it.date.startsWith(monthPrefix) }
        }
    }

    override fun getHomeworkForTeacher(): Flow<List<Homework>> {
        return _homeworks.asStateFlow()
    }

    override suspend fun addHomework(
        studentId: String,
        date: String,
        title: String,
        status: HomeworkStatus,
        note: String?
    ): Result<Homework> {
        val hw = Homework(
            homeworkId = "hw_" + UUID.randomUUID().toString().take(8),
            studentId = studentId,
            date = date,
            title = title,
            status = status,
            note = note,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _homeworks.update { it + hw }
        return Result.success(hw)
    }

    override suspend fun updateHomework(homework: Homework): Result<Homework> {
        _homeworks.update { list ->
            list.map { if (it.homeworkId == homework.homeworkId) homework else it }
        }
        return Result.success(homework)
    }

    override suspend fun deleteHomework(homeworkId: String): Result<Unit> {
        _homeworks.update { list -> list.filterNot { it.homeworkId == homeworkId } }
        return Result.success(Unit)
    }

    override suspend fun getHomeworkById(homeworkId: String): Homework? {
        return _homeworks.value.find { it.homeworkId == homeworkId }
    }
}
