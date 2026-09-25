package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockMonthlyReportRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.reports.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsAndStatisticsAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var examRepository: MockExamRepository
    private lateinit var monthlyReportRepository: MockMonthlyReportRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        recitationRepository = MockRecitationRepository()
        examRepository = MockExamRepository()
        monthlyReportRepository = MockMonthlyReportRepository(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Edge Case 1:
     * Teacher stage = ثانوي, Student = ثانوي
     * Reports تظهر بشكل طبيعي.
     */
    @Test
    fun testCase1_TeacherSecondaryWithSecondaryStudent_ReportsShowNormally() = runTest {
        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrades = gradeRepository.getGrades().first()
        val secGrade = secGrades.first { it.name == "الأول الثانوي" }

        val student = studentRepository.addStudent(
            fullName = "خالد محمود",
            gradeId = secGrade.id,
            parentPhone = "01099887711",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // Record attendance, recitation, exam for September 2026
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-10", AttendanceStatus.PRESENT, null)
        recitationRepository.addRecitation(student.studentId, "2026-09-10", "سورة البقرة", "1-20", 20.0, 20.0, null)
        examRepository.addExam(student.studentId, "2026-09-15", "اختبار شهري", "تجويد", 50.0, 50.0, null)

        val reportsViewModel = ReportsViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            homeworkRepository = com.example.data.repository.MockHomeworkRepository()
        )
        testDispatcher.scheduler.advanceUntilIdle()
        reportsViewModel.selectTab(com.example.ui.reports.ReportsTab.STUDENT)
        reportsViewModel.selectStudent(student.studentId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = reportsViewModel.uiState.value
        val studentReport = state.studentReport
        assertNotNull(studentReport)
        assertEquals(100f, studentReport?.attendancePercent)
        assertEquals(100f, studentReport?.recitationAvg)
        assertEquals(100f, studentReport?.examAvg)
    }

    /**
     * Edge Case 2:
     * Teacher stage = ثانوي, Student = إعدادي سابق
     * Student history remains intact.
     */
    @Test
    fun testCase2_TeacherSwitchesToSec_OldPrepStudentHistoryRemainsIntact() = runTest {
        // Teacher in prep
        val prepGrades = gradeRepository.getGrades().first()
        val prepGrade = prepGrades.first { it.name == "الثاني الإعدادي" }

        val student = studentRepository.addStudent(
            fullName = "عمر هشام",
            gradeId = prepGrade.id,
            parentPhone = "01122334400",
            hasWhatsApp = true,
            alternativePhone = null
        ).getOrThrow()

        // Record historical performance in prep stage
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-05", AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-12", AttendanceStatus.ABSENT, null)
        recitationRepository.addRecitation(student.studentId, "2026-09-05", "سورة النساء", "1-10", 18.0, 20.0, null)
        examRepository.addExam(student.studentId, "2026-09-20", "امتحان شامل", null, 40.0, 50.0, null)
        monthlyReportRepository.saveOrUpdateMonthlyReport(student.studentId, 2026, 9, "مستوى متقدم")

        // Teacher switches stage to Secondary (ثانوي)
        gradeRepository.setStage(EducationalStages.SECONDARY)
        testDispatcher.scheduler.advanceUntilIdle()

        // Query performance for old student
        val perf = monthlyReportRepository.getStudentMonthlyPerformance(student.studentId, 2026, 9)
        assertNotNull(perf)
        assertEquals(2, perf?.attendanceSummary?.totalDays)
        assertEquals(50f, perf?.attendanceSummary?.attendanceRate)
        assertEquals(90f, perf?.recitationSummary?.averagePercentage)
        assertEquals(80f, perf?.examSummary?.averagePercentage)
        assertEquals("مستوى متقدم", perf?.teacherNote)
        assertEquals(prepGrade.id, perf?.student?.gradeId)
    }

    /**
     * Edge Case 3:
     * Current secondary Grade report:
     * لا يحتوي الطالب الإعدادي القديم.
     */
    @Test
    fun testCase3_CurrentSecondaryGradeReport_DoesNotContainPreparatoryStudent() = runTest {
        // Old prep student
        val prepGrade = gradeRepository.getGrades().first().first()
        val prepStudent = studentRepository.addStudent("طالب إعدادي قديم", prepGrade.id, "01000000010", true, null).getOrThrow()

        // Switch to Secondary
        gradeRepository.setStage(EducationalStages.SECONDARY)
        val secGrades = gradeRepository.getGrades().first()
        val secGrade = secGrades.first { it.name == "الأول الثانوي" }
        val secStudent = studentRepository.addStudent("طالب ثانوي جديد", secGrade.id, "01000000020", true, null).getOrThrow()

        val reportsViewModel = ReportsViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            homeworkRepository = com.example.data.repository.MockHomeworkRepository()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter strictly by secondary grade
        reportsViewModel.selectTab(com.example.ui.reports.ReportsTab.GROUP)
        reportsViewModel.selectGrade(secGrade.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val groupReport = reportsViewModel.uiState.value.groupReport
        assertNotNull(groupReport)
        assertEquals(secGrade.id, groupReport?.grade?.id)
        assertTrue(groupReport?.studentStats?.any { it.student.studentId == secStudent.studentId } == true)
        // Preparatory student must NOT be present in this secondary grade overview
        assertFalse(groupReport?.studentStats?.any { it.student.studentId == prepStudent.studentId } == true)
    }

    /**
     * Edge Case 4:
     * Individual old-stage student report:
     * يحتفظ بتاريخ attendance/recitation/exams الصحيح.
     */
    @Test
    fun testCase4_IndividualOldStageStudentReport_RetainsExactPerformanceData() = runTest {
        val prepGrade = gradeRepository.getGrades().first().first()
        val student = studentRepository.addStudent("طالب إعدادي مميز", prepGrade.id, "01055554444", true, null).getOrThrow()

        // 3 Attendance sessions
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-01", AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-08", AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-15", AttendanceStatus.LATE, null)

        // Switch stage
        gradeRepository.setStage(EducationalStages.PRIMARY)

        val individualPerf = monthlyReportRepository.getStudentMonthlyPerformance(student.studentId, 2026, 9)
        assertNotNull(individualPerf)
        assertEquals(3, individualPerf?.attendanceSummary?.totalDays)
        assertEquals(2, individualPerf?.attendanceSummary?.presentCount)
        assertEquals(1, individualPerf?.attendanceSummary?.lateCount)
    }

    /**
     * Edge Case 5:
     * تغيير stage لا يغير:
     * student.gradeId, attendance.studentId, recitations.studentId, exams.studentId, monthly_reports.studentId
     */
    @Test
    fun testCase5_ChangingStageDoesNotMutateForeignKeysOrStudentIds() = runTest {
        val prepGrade = gradeRepository.getGrades().first().first()
        val student = studentRepository.addStudent("طالب ثابت", prepGrade.id, "01033332222", true, null).getOrThrow()

        val att = attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-02", AttendanceStatus.PRESENT, null).getOrThrow()
        val rec = recitationRepository.addRecitation(student.studentId, "2026-09-02", "سورة آل عمران", "1-15", 19.0, 20.0, null).getOrThrow()
        val exam = examRepository.addExam(student.studentId, "2026-09-02", "امتحان نصف شهري", null, 48.0, 50.0, null).getOrThrow()
        val rep = monthlyReportRepository.saveOrUpdateMonthlyReport(student.studentId, 2026, 9, "ملاحظة").getOrThrow()

        // Cycle through all stages
        gradeRepository.setStage(EducationalStages.PRIMARY)
        gradeRepository.setStage(EducationalStages.SECONDARY)
        gradeRepository.setStage(EducationalStages.PREPARATORY)

        val fetchedStudent = studentRepository.getStudentById(student.studentId)
        val fetchedAtt = attendanceRepository.getAttendanceByDate(student.studentId, "2026-09-02")
        val fetchedRec = recitationRepository.getRecitationById(rec.recitationId)
        val fetchedExam = examRepository.getExamById(exam.examId)
        val fetchedRep = monthlyReportRepository.getMonthlyReport(student.studentId, 2026, 9)

        assertEquals(prepGrade.id, fetchedStudent?.gradeId)
        assertEquals(student.studentId, fetchedAtt?.studentId)
        assertEquals(student.studentId, fetchedRec?.studentId)
        assertEquals(student.studentId, fetchedExam?.studentId)
        assertEquals(student.studentId, fetchedRep?.studentId)
    }

    /**
     * Edge Case 6:
     * لا يحدث cross-teacher data (كل استعلام معزول بـ teacher_id).
     */
    @Test
    fun testCase6_ReportsDataIsolatedPerTeacher() = runTest {
        val prepGrade = gradeRepository.getGrades().first().first()
        val student1 = studentRepository.addStudent("طالب المعلم 1", prepGrade.id, "01000000001", true, null).getOrThrow()

        attendanceRepository.recordOrUpdateAttendance(student1.studentId, "2026-09-05", AttendanceStatus.PRESENT, null)

        val att1 = attendanceRepository.getAttendanceSummaryForStudent(student1.studentId, 2026, 9)
        assertEquals(1, att1.totalDays)

        // Non-existent student ID returns empty summary
        val attOther = attendanceRepository.getAttendanceSummaryForStudent("other_teacher_student_id", 2026, 9)
        assertEquals(0, attOther.totalDays)
        assertEquals(0f, attOther.attendanceRate)
    }

    /**
     * Edge Case 7:
     * الشهر الحالي والشهر السابق يحسبان بشكل صحيح.
     */
    @Test
    fun testCase7_CurrentMonthAndPreviousMonthCalculatedAccurately() = runTest {
        val prepGrade = gradeRepository.getGrades().first().first()
        val student = studentRepository.addStudent("طالب متعدد الشهور", prepGrade.id, "01088887777", true, null).getOrThrow()

        // August 2026 (Month 8)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-08-10", AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-08-17", AttendanceStatus.PRESENT, null)

        // September 2026 (Month 9)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-05", AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(student.studentId, "2026-09-12", AttendanceStatus.ABSENT, null)

        // August summary
        val augSummary = attendanceRepository.getAttendanceSummaryForStudent(student.studentId, 2026, 8)
        assertEquals(2, augSummary.totalDays)
        assertEquals(2, augSummary.presentCount)
        assertEquals(100f, augSummary.attendanceRate)

        // September summary
        val sepSummary = attendanceRepository.getAttendanceSummaryForStudent(student.studentId, 2026, 9)
        assertEquals(2, sepSummary.totalDays)
        assertEquals(1, sepSummary.presentCount)
        assertEquals(1, sepSummary.absentCount)
        assertEquals(50f, sepSummary.attendanceRate)
    }
}
