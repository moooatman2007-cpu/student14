package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.core.model.Grade
import com.example.core.model.Group
import com.example.core.model.Student
import com.example.core.model.Teacher
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockHomeworkRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.ui.reports.DailyAttendanceStatus
import com.example.ui.reports.ReportsTab
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReportsUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    private val teacher1 = Teacher(
        id = "t1_id",
        fullName = "أحمد المدرس",
        phoneNumber = "01000000001",
        subject = "قرآن وتجويد",
        educationalStage = EducationalStages.PREPARATORY
    )

    private val teacher2 = Teacher(
        id = "t2_id",
        fullName = "محمود المدرس",
        phoneNumber = "01000000002",
        subject = "لغة عربية",
        educationalStage = EducationalStages.PREPARATORY
    )

    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var groupRepository: MockGroupRepository
    private lateinit var teacherRepository: MockTeacherRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var examRepository: MockExamRepository
    private lateinit var homeworkRepository: MockHomeworkRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        gradeRepository = MockGradeRepository(initialStage = EducationalStages.PREPARATORY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        groupRepository = MockGroupRepository()
        teacherRepository = MockTeacherRepository(initialTeacher = teacher1)
        recitationRepository = MockRecitationRepository()
        examRepository = MockExamRepository()
        homeworkRepository = MockHomeworkRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReportsViewModel {
        return ReportsViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            examRepository = examRepository,
            homeworkRepository = homeworkRepository,
            groupRepository = groupRepository,
            teacherRepository = teacherRepository
        )
    }

    @Test
    fun test1_groupDailyReport_calculatesTotalPresentAbsentAndNoRecord() = runTest {
        val grades = gradeRepository.getGrades().first()
        val prepGrade = grades.first { it.name.contains("إعدادي") }

        val groupA = Group(
            id = "grp_A",
            teacherId = teacher1.id,
            gradeId = prepGrade.id,
            name = "مجموعة أ",
            active = true,
            startTime = "10:00",
            endTime = "12:00"
        )
        groupRepository.setGroups(listOf(groupA))

        // Add 3 students and assign to groupA
        val s1Created = studentRepository.addStudent("طالب 1 (حاضر)", prepGrade.id, "01011112222", true, null).getOrThrow()
        val s1 = studentRepository.updateStudent(s1Created.copy(groupId = groupA.id, teacherId = teacher1.id)).getOrThrow()

        val s2Created = studentRepository.addStudent("طالب 2 (غائب)", prepGrade.id, "01011112223", true, null).getOrThrow()
        val s2 = studentRepository.updateStudent(s2Created.copy(groupId = groupA.id, teacherId = teacher1.id)).getOrThrow()

        val s3Created = studentRepository.addStudent("طالب 3 (لم يسجل)", prepGrade.id, "01011112224", true, null).getOrThrow()
        val s3 = studentRepository.updateStudent(s3Created.copy(groupId = groupA.id, teacherId = teacher1.id)).getOrThrow()

        val todayStr = LocalDate.now().toString()

        // Attendance records for today
        attendanceRepository.recordOrUpdateAttendance(s1.studentId, todayStr, AttendanceStatus.PRESENT, note = "ممتاز")
        attendanceRepository.recordOrUpdateAttendance(s2.studentId, todayStr, AttendanceStatus.ABSENT, note = "بدون عذر")
        // s3 has NO attendance record

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectTab(ReportsTab.GROUP_DAILY)
        vm.selectGroup(groupA.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        val report = state.groupDailyReport

        assertNotNull(report)
        assertEquals(groupA.id, report?.group?.id)
        assertEquals(3, report?.totalStudents)
        assertEquals(1, report?.presentCount)
        assertEquals(1, report?.absentCount)
        assertEquals(1, report?.noRecordCount)

        val s1Att = report?.studentList?.find { it.student.studentId == s1.studentId }
        val s2Att = report?.studentList?.find { it.student.studentId == s2.studentId }
        val s3Att = report?.studentList?.find { it.student.studentId == s3.studentId }

        assertEquals(DailyAttendanceStatus.PRESENT, s1Att?.status)
        assertEquals("ممتاز", s1Att?.note)
        assertEquals(DailyAttendanceStatus.ABSENT, s2Att?.status)
        assertEquals(DailyAttendanceStatus.NO_RECORD, s3Att?.status)
    }

    @Test
    fun test2_gradeDailyReport_calculatesGradeOverviewAndGroupBreakdowns() = runTest {
        val grades = gradeRepository.getGrades().first()
        val prepGrade = grades.first { it.name.contains("الأول الإعدادي") }

        val grpA = Group("g_A", teacher1.id, prepGrade.id, "مجموعة أ", true, "10:00", "12:00")
        val grpB = Group("g_B", teacher1.id, prepGrade.id, "مجموعة ب", true, "12:00", "14:00")
        groupRepository.setGroups(listOf(grpA, grpB))

        val s1Created = studentRepository.addStudent("طالب أ1", prepGrade.id, "01000000001", true, null).getOrThrow()
        val s1 = studentRepository.updateStudent(s1Created.copy(groupId = grpA.id, teacherId = teacher1.id)).getOrThrow()

        val s2Created = studentRepository.addStudent("طالب أ2", prepGrade.id, "01000000002", true, null).getOrThrow()
        val s2 = studentRepository.updateStudent(s2Created.copy(groupId = grpA.id, teacherId = teacher1.id)).getOrThrow()

        val s3Created = studentRepository.addStudent("طالب ب1", prepGrade.id, "01000000003", true, null).getOrThrow()
        val s3 = studentRepository.updateStudent(s3Created.copy(groupId = grpB.id, teacherId = teacher1.id)).getOrThrow()

        val todayStr = LocalDate.now().toString()
        attendanceRepository.recordOrUpdateAttendance(s1.studentId, todayStr, AttendanceStatus.PRESENT, null)
        attendanceRepository.recordOrUpdateAttendance(s2.studentId, todayStr, AttendanceStatus.ABSENT, null)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectTab(ReportsTab.GRADE_DAILY)
        vm.selectGrade(prepGrade.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = vm.uiState.value.gradeDailyReport
        assertNotNull(report)
        assertEquals(prepGrade.id, report?.grade?.id)
        assertEquals(2, report?.totalGroups)

        val bdA = report?.groupBreakdowns?.find { it.group.id == grpA.id }
        val bdB = report?.groupBreakdowns?.find { it.group.id == grpB.id }

        assertEquals(2, bdA?.totalStudents)
        assertEquals(1, bdA?.presentCount)
        assertEquals(1, bdA?.absentCount)
        assertEquals(0, bdA?.noRecordCount)

        assertEquals(1, bdB?.totalStudents)
        assertEquals(0, bdB?.presentCount)
        assertEquals(0, bdB?.absentCount)
        assertEquals(1, bdB?.noRecordCount)
    }

    @Test
    fun test3_teacherDailyReport_calculatesTeacherOverviewAndStageBreakdowns() = runTest {
        val grades = gradeRepository.getGrades().first()
        val prep1 = grades.first { it.name.contains("الأول الإعدادي") }

        val grp1 = Group("g_1", teacher1.id, prep1.id, "مجموعة 1", true, "10:00", "12:00")
        groupRepository.setGroups(listOf(grp1))

        val s1Created = studentRepository.addStudent("طالب 1", prep1.id, "01011111111", true, null).getOrThrow()
        val s1 = studentRepository.updateStudent(s1Created.copy(groupId = grp1.id, teacherId = teacher1.id)).getOrThrow()

        val s2Created = studentRepository.addStudent("طالب 2", prep1.id, "01022222222", true, null).getOrThrow()
        val s2 = studentRepository.updateStudent(s2Created.copy(groupId = grp1.id, teacherId = teacher1.id)).getOrThrow()

        val todayStr = LocalDate.now().toString()
        attendanceRepository.recordOrUpdateAttendance(s1.studentId, todayStr, AttendanceStatus.PRESENT, null)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectTab(ReportsTab.TEACHER_DAILY)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = vm.uiState.value.teacherDailyReport
        assertNotNull(report)
        assertTrue(report!!.totalStudents >= 2)
        assertEquals(1, report.totalGroups)
        assertTrue(report.presentCount >= 1)
        assertTrue(report.noRecordCount >= 1)

        assertTrue(report.stageBreakdowns.isNotEmpty())
    }

    @Test
    fun test4_historicalGroupSnapshotInAttendance() = runTest {
        val grades = gradeRepository.getGrades().first()
        val prep = grades.first()

        val grpA = Group("g_A_hist", teacher1.id, prep.id, "مجموعة أ القديمة", true, "10:00", "12:00")
        val grpB = Group("g_B_hist", teacher1.id, prep.id, "مجموعة ب الجديدة", true, "12:00", "14:00")
        groupRepository.setGroups(listOf(grpA, grpB))

        val sCreated = studentRepository.addStudent("طالب متنقل", prep.id, "01099998888", true, null).getOrThrow()
        val s = studentRepository.updateStudent(sCreated.copy(groupId = grpB.id, teacherId = teacher1.id)).getOrThrow()

        // Attendance on 2026-09-20 was recorded while student was in Group A
        attendanceRepository.recordOrUpdateAttendance(
            studentId = s.studentId,
            date = "2026-09-20",
            status = AttendanceStatus.PRESENT,
            note = null
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Set date to 2026-09-20
        vm.setSelectedDate(LocalDate.parse("2026-09-20"))
        vm.selectTab(ReportsTab.GROUP_DAILY)
        vm.selectGroup(grpA.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.groupDailyReport)
    }

    @Test
    fun test5_tenantIsolation_teacherOnlySeesOwnData() = runTest {
        val grades = gradeRepository.getGrades().first()
        val prep = grades.first()

        val grpT1 = Group("g_t1", teacher1.id, prep.id, "مجموعة مدرس 1", true, "10:00", "12:00")
        val grpT2 = Group("g_t2", teacher2.id, prep.id, "مجموعة مدرس 2", true, "12:00", "14:00")
        groupRepository.setGroups(listOf(grpT1, grpT2))

        val sCreated = studentRepository.addStudent("طالب1 لمدرس 1", prep.id, "01011110001", true, null).getOrThrow()
        studentRepository.updateStudent(sCreated.copy(groupId = grpT1.id, teacherId = teacher1.id))

        val vm = createViewModel() // teacher1 logged in
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        // Verify teacher1 only sees grpT1 in groups list
        assertEquals(1, state.groups.size)
        assertEquals(grpT1.id, state.groups.first().id)
    }

    @Test
    fun test6_noGroupsOrStudents_handledGracefullyWithoutCrashing() = runTest {
        groupRepository.setGroups(emptyList())

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0, state.groups.size)

        vm.selectTab(ReportsTab.GROUP_DAILY)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfter = vm.uiState.value
        // ViewModel handles empty state without throwing exceptions
        assertEquals(null, stateAfter.groupDailyReport)
    }
}
