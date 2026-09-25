package com.example.data.repository

import com.example.core.model.GradeMonthlyReportOverview
import com.example.core.model.MonthlyReport
import com.example.core.model.StudentMonthlyPerformance
import kotlinx.coroutines.flow.Flow

interface MonthlyReportRepository {
    suspend fun getMonthlyReport(studentId: String, year: Int, month: Int): MonthlyReport?
    suspend fun saveOrUpdateMonthlyReport(
        studentId: String,
        year: Int,
        month: Int,
        teacherNote: String
    ): Result<MonthlyReport>
    suspend fun getStudentMonthlyPerformance(
        studentId: String,
        year: Int,
        month: Int
    ): StudentMonthlyPerformance?
    suspend fun getGradeMonthlyReportOverview(
        gradeId: String?, // null for all
        year: Int,
        month: Int
    ): GradeMonthlyReportOverview
}
