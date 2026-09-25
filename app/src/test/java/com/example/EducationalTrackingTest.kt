package com.example

import com.example.core.model.AttendanceStatus
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockMonthlyReportRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EducationalTrackingTest {

    private lateinit var studentRepo: MockStudentRepository
    private lateinit var gradeRepo: MockGradeRepository
    private lateinit var attendanceRepo: MockAttendanceRepository
    private lateinit var recitationRepo: MockRecitationRepository
    private lateinit var examRepo: MockExamRepository
    private lateinit var monthlyReportRepo: MockMonthlyReportRepository

    @Before
    fun setup() {
        studentRepo = MockStudentRepository()
        gradeRepo = MockGradeRepository()
        attendanceRepo = MockAttendanceRepository()
        recitationRepo = MockRecitationRepository()
        examRepo = MockExamRepository()
        monthlyReportRepo = MockMonthlyReportRepository(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            recitationRepository = recitationRepo,
            examRepository = examRepo
        )
    }

    @Test
    fun testAttendanceFlow() = runTest {
        val studentId = "s_01"
        val initialList = attendanceRepo.getAttendanceForStudentByMonth(studentId, 2026, 9).first()
        val initialCount = initialList.size

        // Record Attendance
        val result = attendanceRepo.recordOrUpdateAttendance(
            studentId = studentId,
            date = "2026-09-20",
            status = AttendanceStatus.PRESENT,
            note = "حضر في الموعد"
        )
        assertTrue(result.isSuccess)
        val createdAtt = result.getOrNull()
        assertNotNull(createdAtt)

        val updatedList = attendanceRepo.getAttendanceForStudentByMonth(studentId, 2026, 9).first()
        assertEquals(initialCount + 1, updatedList.size)

        // Summary Calculation
        val summary = attendanceRepo.getAttendanceSummaryForStudent(studentId, 2026, 9)
        assertTrue(summary.totalDays > 0)
        assertTrue(summary.attendanceRate >= 0f)

        // Delete Attendance
        if (createdAtt != null) {
            attendanceRepo.deleteAttendance(createdAtt.attendanceId)
            val finalList = attendanceRepo.getAttendanceForStudentByMonth(studentId, 2026, 9).first()
            assertEquals(initialCount, finalList.size)
        }
    }

    @Test
    fun testRecitationsFlow() = runTest {
        val studentId = "s_01"
        val initialList = recitationRepo.getRecitationsForStudent(studentId).first()
        val initialCount = initialList.size

        // Add Recitation
        val result = recitationRepo.addRecitation(
            studentId = studentId,
            date = "2026-09-20",
            title = "سورة الكهف",
            content = "من آية 1 إلى 20",
            score = 9.5,
            maxScore = 10.0,
            note = "ممتاز ومتقن للأحكام"
        )
        assertTrue(result.isSuccess)
        val createdRec = result.getOrNull()
        assertNotNull(createdRec)

        val updatedList = recitationRepo.getRecitationsForStudent(studentId).first()
        assertEquals(initialCount + 1, updatedList.size)

        // Summary Calculation
        val summary = recitationRepo.getRecitationSummaryForStudent(studentId, 2026, 9)
        assertTrue(summary.totalCount > 0)
        assertTrue(summary.averagePercentage > 0f)

        // Delete Recitation
        if (createdRec != null) {
            recitationRepo.deleteRecitation(createdRec.recitationId)
            val finalList = recitationRepo.getRecitationsForStudent(studentId).first()
            assertEquals(initialCount, finalList.size)
        }
    }

    @Test
    fun testExamsFlow() = runTest {
        val studentId = "s_01"
        val initialList = examRepo.getExamsForStudent(studentId).first()
        val initialCount = initialList.size

        // Add Exam
        val result = examRepo.addExam(
            studentId = studentId,
            date = "2026-09-20",
            examName = "امتحان تجويد شامل",
            subject = "تجويد",
            score = 48.0,
            maxScore = 50.0,
            note = "أداء رائع"
        )
        assertTrue(result.isSuccess)
        val createdExam = result.getOrNull()
        assertNotNull(createdExam)

        val updatedList = examRepo.getExamsForStudent(studentId).first()
        assertEquals(initialCount + 1, updatedList.size)

        // Summary Calculation
        val summary = examRepo.getExamSummaryForStudent(studentId, 2026, 9)
        assertTrue(summary.totalCount > 0)
        assertTrue(summary.averagePercentage > 0f)

        // Delete Exam
        if (createdExam != null) {
            examRepo.deleteExam(createdExam.examId)
            val finalList = examRepo.getExamsForStudent(studentId).first()
            assertEquals(initialCount, finalList.size)
        }
    }

    @Test
    fun testMonthlyReportGeneration() = runTest {
        val studentId = "s_01"
        val performance = monthlyReportRepo.getStudentMonthlyPerformance(studentId, 2026, 9)
        assertNotNull(performance)
        assertNotNull(performance?.attendanceSummary)
        assertNotNull(performance?.recitationSummary)
        assertNotNull(performance?.examSummary)

        // Save Teacher Monthly Note
        val reportResult = monthlyReportRepo.saveOrUpdateMonthlyReport(
            studentId = studentId,
            year = 2026,
            month = 9,
            teacherNote = "طالب متميز وملتزم جداً"
        )
        assertTrue(reportResult.isSuccess)

        val report = monthlyReportRepo.getMonthlyReport(studentId, 2026, 9)
        assertEquals("طالب متميز وملتزم جداً", report?.teacherNote)
    }
}
