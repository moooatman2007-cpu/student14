package com.example.data.repository

import com.example.core.model.GradeMonthlyReportOverview
import com.example.core.model.MonthlyReport
import com.example.core.model.StudentMonthlyPerformance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.UUID

class MockMonthlyReportRepository(
    private val studentRepository: StudentRepository,
    private val gradeRepository: GradeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val recitationRepository: RecitationRepository,
    private val examRepository: ExamRepository
) : MonthlyReportRepository {

    private val _reports = MutableStateFlow<List<MonthlyReport>>(emptyList())

    init {
        seedInitialNotes()
    }

    private fun seedInitialNotes() {
        val initialList = mutableListOf<MonthlyReport>()
        val defaultNotes = listOf(
            "طالب متميز وملتزم بالحضور وأداء التسميع في الموعد المحدد. يرجى الاستمرار على هذا المستوى.",
            "مستوى جيد جداً وتطور ملحوظ في التجويد ومخارج الحروف. يحتاج مزيداً من التركيز أثناء الامتحانات.",
            "طالب حريص ومشارك فعال في الحلقة. نوصي بمراجعة دورية في المنزل لضمان التثبيت."
        )

        for (i in 1..86) {
            val studentId = "s_" + String.format("%02d", i)
            val note = defaultNotes[i % defaultNotes.size]
            initialList.add(
                MonthlyReport(
                    reportId = "rep_${studentId}_2026_9",
                    studentId = studentId,
                    month = 9,
                    year = 2026,
                    teacherNote = note
                )
            )
        }
        _reports.value = initialList
    }

    override suspend fun getMonthlyReport(studentId: String, year: Int, month: Int): MonthlyReport? {
        return _reports.value.find { it.studentId == studentId && it.year == year && it.month == month }
    }

    override suspend fun saveOrUpdateMonthlyReport(
        studentId: String,
        year: Int,
        month: Int,
        teacherNote: String
    ): Result<MonthlyReport> {
        var updatedReport: MonthlyReport? = null
        _reports.update { list ->
            val index = list.indexOfFirst { it.studentId == studentId && it.year == year && it.month == month }
            if (index >= 0) {
                val existing = list[index]
                val modified = existing.copy(
                    teacherNote = teacherNote.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                updatedReport = modified
                list.toMutableList().apply { set(index, modified) }
            } else {
                val newReport = MonthlyReport(
                    reportId = "rep_" + UUID.randomUUID().toString().take(8),
                    studentId = studentId,
                    month = month,
                    year = year,
                    teacherNote = teacherNote.trim(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                updatedReport = newReport
                listOf(newReport) + list
            }
        }
        return Result.success(updatedReport!!)
    }

    override suspend fun getStudentMonthlyPerformance(
        studentId: String,
        year: Int,
        month: Int
    ): StudentMonthlyPerformance? {
        val student = studentRepository.getStudentById(studentId) ?: return null
        val grade = gradeRepository.getGradeById(student.gradeId)
        val attendanceSummary = attendanceRepository.getAttendanceSummaryForStudent(studentId, year, month)
        val recitationSummary = recitationRepository.getRecitationSummaryForStudent(studentId, year, month)
        val examSummary = examRepository.getExamSummaryForStudent(studentId, year, month)
        val report = getMonthlyReport(studentId, year, month)

        return StudentMonthlyPerformance(
            student = student,
            grade = grade,
            month = month,
            year = year,
            attendanceSummary = attendanceSummary,
            recitationSummary = recitationSummary,
            examSummary = examSummary,
            teacherNote = report?.teacherNote ?: ""
        )
    }

    override suspend fun getGradeMonthlyReportOverview(
        gradeId: String?,
        year: Int,
        month: Int
    ): GradeMonthlyReportOverview {
        val allStudents = if (gradeId.isNullOrBlank() || gradeId == "all") {
            studentRepository.getStudents().first()
        } else {
            studentRepository.getStudentsByGrade(gradeId).first()
        }

        val gradeName = if (gradeId.isNullOrBlank() || gradeId == "all") {
            "جميع الصفوف"
        } else {
            gradeRepository.getGradeById(gradeId)?.name ?: "صف غير محدد"
        }

        val performanceList = mutableListOf<StudentMonthlyPerformance>()
        for (student in allStudents) {
            val grade = gradeRepository.getGradeById(student.gradeId)
            val attSum = attendanceRepository.getAttendanceSummaryForStudent(student.studentId, year, month)
            val recSum = recitationRepository.getRecitationSummaryForStudent(student.studentId, year, month)
            val examSum = examRepository.getExamSummaryForStudent(student.studentId, year, month)
            val rep = getMonthlyReport(student.studentId, year, month)

            performanceList.add(
                StudentMonthlyPerformance(
                    student = student,
                    grade = grade,
                    month = month,
                    year = year,
                    attendanceSummary = attSum,
                    recitationSummary = recSum,
                    examSummary = examSum,
                    teacherNote = rep?.teacherNote ?: ""
                )
            )
        }

        val avgAttendance = if (performanceList.isNotEmpty()) {
            performanceList.map { it.attendanceSummary.attendanceRate }.average().toFloat()
        } else 0f

        val avgRecitation = if (performanceList.isNotEmpty()) {
            performanceList.map { it.recitationSummary.averagePercentage }.average().toFloat()
        } else 0f

        val avgExam = if (performanceList.isNotEmpty()) {
            performanceList.map { it.examSummary.averagePercentage }.average().toFloat()
        } else 0f

        return GradeMonthlyReportOverview(
            month = month,
            year = year,
            gradeId = gradeId,
            gradeName = gradeName,
            totalStudents = allStudents.size,
            averageAttendanceRate = (Math.round(avgAttendance * 10.0) / 10.0).toFloat(),
            averageRecitationRate = (Math.round(avgRecitation * 10.0) / 10.0).toFloat(),
            averageExamRate = (Math.round(avgExam * 10.0) / 10.0).toFloat(),
            studentPerformances = performanceList
        )
    }
}
