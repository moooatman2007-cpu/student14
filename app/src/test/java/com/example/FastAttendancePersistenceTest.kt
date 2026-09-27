package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.EducationalStages
import com.example.core.model.Student
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockStudentRepository
import com.example.ui.attendance.FastAttendanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class FastAttendancePersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var paymentRepository: MockPaymentRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.SECONDARY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        paymentRepository = MockPaymentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestStudents(count: Int, gradeId: String, groupId: String? = null): List<Student> {
        return (1..count).map { i ->
            Student(
                studentId = "std_${String.format("%05d", i)}",
                teacherId = "teacher_test",
                gradeId = gradeId,
                groupId = groupId,
                fullName = "طالب رقم $i",
                studentCode = String.format("ST-%05d", i),
                parentPhone = "011000000$i"
            )
        }
    }

    @Test
    fun test1_newSessionAttendance() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val students = createTestStudents(14, targetGrade.id)

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(students, students)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Scan ST-00011 (studentId = std_00011)
        viewModel.processScannedBarcode("ST-00011")
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val savedRecords = attendanceRepository.getAttendanceForDateAndGroup("2026-09-27", null)
        val presentRecord = savedRecords.find { it.studentId == "std_00011" }
        assertNotNull(presentRecord)
        assertEquals(AttendanceStatus.PRESENT, presentRecord?.status)

        val absentRecords = savedRecords.filter { it.studentId != "std_00011" }
        assertEquals(13, absentRecords.size)
        assertTrue(absentRecords.all { it.status == AttendanceStatus.ABSENT })
    }

    @Test
    fun test2_reopenSameDateSessionAndScanLateStudent() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val students = createTestStudents(14, targetGrade.id)

        val viewModel1 = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel1.setStudentsForScope(students, students)
        viewModel1.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Session 1: Scan ST-00011 and finish
        viewModel1.processScannedBarcode("ST-00011")
        advanceUntilIdle()
        viewModel1.finishAttendance()
        advanceUntilIdle()

        // Session 2 (Reopen on same date): initialize new ViewModel instance
        val viewModel2 = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel2.setStudentsForScope(students, students)
        viewModel2.onGradeSelected(targetGrade.id)
        advanceUntilIdle() // This triggers loadExistingAttendance()

        // ST-00011 should already be loaded as present
        assertTrue(viewModel2.uiState.value.presentStudentIds.contains("std_00011"))

        // Now scan late student ST-00004
        viewModel2.processScannedBarcode("ST-00004")
        advanceUntilIdle()

        viewModel2.finishAttendance()
        advanceUntilIdle()

        val savedRecords = attendanceRepository.getAttendanceForDateAndGroup("2026-09-27", null)
        val st11 = savedRecords.find { it.studentId == "std_00011" }
        val st04 = savedRecords.find { it.studentId == "std_00004" }

        assertEquals(AttendanceStatus.PRESENT, st11?.status)
        assertEquals(AttendanceStatus.PRESENT, st04?.status)

        val remainingAbsent = savedRecords.filter { it.studentId != "std_00011" && it.studentId != "std_00004" }
        assertEquals(12, remainingAbsent.size)
        assertTrue(remainingAbsent.all { it.status == AttendanceStatus.ABSENT })
    }

    @Test
    fun test3_existingAbsentConvertedToPresent() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val students = createTestStudents(14, targetGrade.id)

        // Pre-save ST-00004 as ABSENT
        attendanceRepository.recordOrUpdateAttendance("std_00004", "2026-09-27", AttendanceStatus.ABSENT, null, null)
        val initialAtt = attendanceRepository.getAttendanceByDate("std_00004", "2026-09-27")
        assertNotNull(initialAtt)
        val initialAttendanceId = initialAtt?.attendanceId

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(students, students)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Scan ST-00004
        viewModel.processScannedBarcode("ST-00004")
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val updatedAtt = attendanceRepository.getAttendanceByDate("std_00004", "2026-09-27")
        assertEquals(AttendanceStatus.PRESENT, updatedAtt?.status)
        assertEquals(initialAttendanceId, updatedAtt?.attendanceId) // Same attendance_id, updated not inserted
    }

    @Test
    fun test4_repeatedScanNoDuplicates() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val students = createTestStudents(5, targetGrade.id)

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(students, students)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Scan ST-00002 multiple times
        viewModel.processScannedBarcode("ST-00002")
        viewModel.processScannedBarcode("ST-00002")
        viewModel.processScannedBarcode("ST-00002")
        advanceUntilIdle()

        viewModel.finishAttendance()
        advanceUntilIdle()

        val savedRecords = attendanceRepository.getAttendanceForDateAndGroup("2026-09-27", null)
        val studentRecords = savedRecords.filter { it.studentId == "std_00002" }
        assertEquals(1, studentRecords.size)
        assertEquals(AttendanceStatus.PRESENT, studentRecords[0].status)
    }

    @Test
    fun test5_reopenAndSaveWithoutScanningPreservesPresent() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val students = createTestStudents(5, targetGrade.id)

        // Pre-save ST-00001 as PRESENT
        attendanceRepository.recordOrUpdateAttendance("std_00001", "2026-09-27", AttendanceStatus.PRESENT, null, null)

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setStudentsForScope(students, students)
        viewModel.onGradeSelected(targetGrade.id)
        advanceUntilIdle() // loads existing PRESENT for std_00001

        assertTrue(viewModel.uiState.value.presentStudentIds.contains("std_00001"))

        // Save without scanning anyone new
        viewModel.finishAttendance()
        advanceUntilIdle()

        val updatedAtt = attendanceRepository.getAttendanceByDate("std_00001", "2026-09-27")
        assertEquals(AttendanceStatus.PRESENT, updatedAtt?.status)
    }

    @Test
    fun test6_groupAttendancePreserved() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val groupId = "group_abc"
        val students = createTestStudents(5, targetGrade.id, groupId = groupId)

        // Pre-save group attendance
        attendanceRepository.recordOrUpdateAttendance("std_00001", "2026-09-27", AttendanceStatus.PRESENT, "Group session", groupId)

        val viewModel = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        viewModel.setInitialGroupId(groupId, "مجموعة A")
        viewModel.setStudentsForScope(students, students)
        advanceUntilIdle()

        // Should load existing group attendance
        assertTrue(viewModel.uiState.value.presentStudentIds.contains("std_00001"))

        viewModel.finishAttendance()
        advanceUntilIdle()

        val groupRecords = attendanceRepository.getAttendanceForDateAndGroup("2026-09-27", groupId)
        val st1 = groupRecords.find { it.studentId == "std_00001" }
        assertEquals(AttendanceStatus.PRESENT, st1?.status)
        assertEquals(groupId, st1?.groupId)
    }
}
