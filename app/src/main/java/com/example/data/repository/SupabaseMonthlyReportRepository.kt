package com.example.data.repository

import com.example.core.model.GradeMonthlyReportOverview
import com.example.core.model.MonthlyReport
import com.example.core.model.StudentMonthlyPerformance
import com.example.core.model.StudentMonthlyPerformanceViewDto
import com.example.core.model.SupabaseMonthlyReportDto
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseMonthlyReportRepository : MonthlyReportRepository {
    private val client = SupabaseClientProvider.client

    override suspend fun getMonthlyReport(
        studentId: String,
        year: Int,
        month: Int
    ): MonthlyReport? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull() ?: return@withContext null
        try {
            val dto = client.postgrest["monthly_reports"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", user.id)
                        eq("year", year)
                        eq("month", month)
                    }
                }
                .decodeSingleOrNull<SupabaseMonthlyReportDto>()
            dto?.toMonthlyReport()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveOrUpdateMonthlyReport(
        studentId: String,
        year: Int,
        month: Int,
        teacherNote: String
    ): Result<MonthlyReport> = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
            ?: return@withContext Result.failure(Exception("انتهت الجلسة، يرجى إعادة تسجيل الدخول."))
        try {
            val existing = client.postgrest["monthly_reports"]
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", user.id)
                        eq("year", year)
                        eq("month", month)
                    }
                }
                .decodeSingleOrNull<SupabaseMonthlyReportDto>()

            val dto = if (existing != null) {
                val updatedDto = SupabaseMonthlyReportDto(
                    id = existing.id,
                    teacherId = user.id,
                    studentId = studentId,
                    year = year,
                    month = month,
                    teacherNote = teacherNote
                )
                client.postgrest["monthly_reports"].update(updatedDto) {
                    filter {
                        eq("id", existing.id ?: "")
                        eq("teacher_id", user.id)
                    }
                }.decodeSingle<SupabaseMonthlyReportDto>()
            } else {
                val newDto = SupabaseMonthlyReportDto(
                    teacherId = user.id,
                    studentId = studentId,
                    year = year,
                    month = month,
                    teacherNote = teacherNote
                )
                client.postgrest["monthly_reports"].insert(newDto).decodeSingle<SupabaseMonthlyReportDto>()
            }

            Result.success(dto.toMonthlyReport())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("فشل حفظ التقرير الشهري: ${e.message}"))
        }
    }

    override suspend fun getStudentMonthlyPerformance(
        studentId: String,
        year: Int,
        month: Int
    ): StudentMonthlyPerformance? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull() ?: return@withContext null
        try {
            val params = buildJsonObject {
                put("p_year", year)
                put("p_month", month)
                put("p_student_id", studentId)
            }
            val list = client.postgrest.rpc(
                function = "get_student_monthly_performance",
                parameters = params
            ).decodeList<StudentMonthlyPerformanceViewDto>()

            list.firstOrNull()?.toStudentMonthlyPerformance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getGradeMonthlyReportOverview(
        gradeId: String?,
        year: Int,
        month: Int
    ): GradeMonthlyReportOverview = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull() ?: return@withContext GradeMonthlyReportOverview(
            month = month,
            year = year,
            gradeId = gradeId,
            gradeName = if (gradeId == null) "جميع الصفوف" else "",
            totalStudents = 0,
            averageAttendanceRate = 0f,
            averageRecitationRate = 0f,
            averageExamRate = 0f,
            studentPerformances = emptyList()
        )
        try {
            val params = buildJsonObject {
                put("p_year", year)
                put("p_month", month)
                if (gradeId != null) {
                    put("p_grade_id", gradeId)
                }
            }
            val list = client.postgrest.rpc(
                function = "get_student_monthly_performance",
                parameters = params
            ).decodeList<StudentMonthlyPerformanceViewDto>()
                .map { it.toStudentMonthlyPerformance() }

            val total = list.size
            val avgAttendance = if (total > 0) list.map { it.attendanceSummary.attendanceRate }.average().toFloat() else 0f
            val avgRecitation = if (total > 0) list.map { it.recitationSummary.averagePercentage }.average().toFloat() else 0f
            val avgExam = if (total > 0) list.map { it.examSummary.averagePercentage }.average().toFloat() else 0f

            val gradeName = if (gradeId != null && list.isNotEmpty()) {
                list.first().grade?.name ?: "الصف المحدد"
            } else if (gradeId != null) {
                "الصف المحدد"
            } else {
                "جميع الصفوف"
            }

            GradeMonthlyReportOverview(
                month = month,
                year = year,
                gradeId = gradeId,
                gradeName = gradeName,
                totalStudents = total,
                averageAttendanceRate = avgAttendance,
                averageRecitationRate = avgRecitation,
                averageExamRate = avgExam,
                studentPerformances = list
            )
        } catch (e: Exception) {
            e.printStackTrace()
            GradeMonthlyReportOverview(
                month = month,
                year = year,
                gradeId = gradeId,
                gradeName = if (gradeId == null) "جميع الصفوف" else "",
                totalStudents = 0,
                averageAttendanceRate = 0f,
                averageRecitationRate = 0f,
                averageExamRate = 0f,
                studentPerformances = emptyList()
            )
        }
    }
}
