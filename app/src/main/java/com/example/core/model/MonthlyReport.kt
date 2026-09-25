package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseMonthlyReportDto(
    @SerialName("id") val id: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("month") val month: Int,
    @SerialName("year") val year: Int,
    @SerialName("teacher_note") val teacherNote: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toMonthlyReport(): MonthlyReport {
        return MonthlyReport(
            reportId = id ?: "",
            studentId = studentId,
            month = month,
            year = year,
            teacherNote = teacherNote,
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt)
        )
    }
}

@Serializable
data class StudentMonthlyPerformanceViewDto(
    @SerialName("student_id") val studentId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("student_code") val studentCode: String,
    @SerialName("grade_id") val gradeId: String,
    @SerialName("grade_name") val gradeName: String,
    @SerialName("year") val year: Int,
    @SerialName("month") val month: Int,
    @SerialName("attendance_present") val attendancePresent: Int = 0,
    @SerialName("attendance_absent") val attendanceAbsent: Int = 0,
    @SerialName("attendance_late") val attendanceLate: Int = 0,
    @SerialName("attendance_excused") val attendanceExcused: Int = 0,
    @SerialName("attendance_total") val attendanceTotal: Int = 0,
    @SerialName("attendance_percentage") val attendancePercentage: Double = 0.0,
    @SerialName("recitation_count") val recitationCount: Int = 0,
    @SerialName("recitation_average_percentage") val recitationAveragePercentage: Double = 0.0,
    @SerialName("exam_count") val examCount: Int = 0,
    @SerialName("exam_average_percentage") val examAveragePercentage: Double = 0.0,
    @SerialName("teacher_note") val teacherNote: String = ""
) {
    fun toStudentMonthlyPerformance(): StudentMonthlyPerformance {
        val studentObj = Student(
            studentId = studentId,
            studentCode = studentCode,
            fullName = studentName,
            gradeId = gradeId,
            teacherId = teacherId
        )
        val gradeObj = Grade(
            id = gradeId,
            teacherId = teacherId,
            name = gradeName
        )
        val attSummary = AttendanceSummary(
            totalDays = attendanceTotal,
            presentCount = attendancePresent,
            absentCount = attendanceAbsent,
            lateCount = attendanceLate,
            excusedCount = attendanceExcused,
            attendanceRate = attendancePercentage.toFloat()
        )
        val recSummary = RecitationSummary(
            totalCount = recitationCount,
            averagePercentage = recitationAveragePercentage.toFloat()
        )
        val exSummary = ExamSummary(
            totalCount = examCount,
            averagePercentage = examAveragePercentage.toFloat()
        )
        return StudentMonthlyPerformance(
            student = studentObj,
            grade = gradeObj,
            month = month,
            year = year,
            attendanceSummary = attSummary,
            recitationSummary = recSummary,
            examSummary = exSummary,
            teacherNote = teacherNote
        )
    }
}

data class MonthlyReport(
    val reportId: String,
    val studentId: String,
    val month: Int, // 1 - 12
    val year: Int, // e.g. 2026
    val teacherNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StudentMonthlyPerformance(
    val student: Student,
    val grade: Grade?,
    val month: Int,
    val year: Int,
    val attendanceSummary: AttendanceSummary,
    val recitationSummary: RecitationSummary,
    val examSummary: ExamSummary,
    val teacherNote: String = ""
)

data class GradeMonthlyReportOverview(
    val month: Int,
    val year: Int,
    val gradeId: String?,
    val gradeName: String,
    val totalStudents: Int,
    val averageAttendanceRate: Float,
    val averageRecitationRate: Float,
    val averageExamRate: Float,
    val studentPerformances: List<StudentMonthlyPerformance>
)

private fun parseIsoToMillis(isoString: String?): Long {
    if (isoString.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
