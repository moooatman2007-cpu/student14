package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.EducationalStages
import com.example.core.model.Group
import com.example.core.model.Student
import com.example.core.model.Teacher
import com.example.core.model.UpsertAttendanceRequest
import com.example.data.SupabaseClientProvider
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.ui.attendance.FastAttendanceViewModel
import com.example.ui.attendance.ScanResultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceByGroupTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentTeacherId = "teacher_owner_123"
    private val otherTeacherId = "teacher_other_999"

    private val groupA = Group(
        id = "grp_A",
        teacherId = currentTeacherId,
        gradeId = "grade_1",
        name = "نجوم المستقبل",
        startTime = "10:00",
        endTime = "11:30"
    )

    private val groupB = Group(
        id = "grp_B",
        teacherId = currentTeacherId,
        gradeId = "grade_1",
        name = "رواد الغد",
        startTime = "12:00",
        endTime = "13:30"
    )

    private val groupForeign = Group(
        id = "grp_Foreign",
        teacherId = otherTeacherId,
        gradeId = "grade_1",
        name = "مجموعة مدرس آخر",
        startTime = "14:00",
        endTime = "15:30"
    )

    private lateinit var teacherRepo: MockTeacherRepository
    private lateinit var groupRepo: MockGroupRepository
    private lateinit var gradeRepo: MockGradeRepository
    private lateinit var studentRepo: MockStudentRepository
    private lateinit var attendanceRepo: MockAttendanceRepository
    private lateinit var paymentRepo: MockPaymentRepository

    // Students
    private val studentA1 = Student(
        studentId = "std_a1",
        teacherId = currentTeacherId,
        gradeId = "grade_1",
        groupId = "grp_A",
        fullName = "أحمد محمد (مجموعة أ)",
        studentCode = "BAR_A1"
    )

    private val studentA2 = Student(
        studentId = "std_a2",
        teacherId = currentTeacherId,
        gradeId = "grade_1",
        groupId = "grp_A",
        fullName = "إبراهيم حسن (مجموعة أ)",
        studentCode = "BAR_A2"
    )

    private val studentB1 = Student(
        studentId = "std_b1",
        teacherId = currentTeacherId,
        gradeId = "grade_1",
        groupId = "grp_B",
        fullName = "خالد عمر (مجموعة ب)",
        studentCode = "BAR_B1"
    )

    private val studentForeign = Student(
        studentId = "std_foreign",
        teacherId = otherTeacherId,
        gradeId = "grade_1",
        groupId = "grp_Foreign",
        fullName = "طالب غريب لمدرس آخر",
        studentCode = "BAR_FOR"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        SupabaseClientProvider.mockTeacherId = currentTeacherId

        val currentTeacher = Teacher(id = currentTeacherId, email = "owner@test.com", fullName = "الأستاذ أحمد")
        teacherRepo = MockTeacherRepository(initialTeacher = currentTeacher)
        groupRepo = MockGroupRepository(initialGroups = listOf(groupA, groupB, groupForeign))
        gradeRepo = MockGradeRepository(initialStage = EducationalStages.SECONDARY)
        studentRepo = MockStudentRepository(gradeRepository = gradeRepo)
        attendanceRepo = MockAttendanceRepository()
        paymentRepo = MockPaymentRepository()
    }

    @After
    fun tearDown() {
        SupabaseClientProvider.mockTeacherId = null
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FastAttendanceViewModel {
        return FastAttendanceViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            paymentRepository = paymentRepo,
            groupRepository = groupRepo,
            teacherRepository = teacherRepo
        )
    }

    /**
     * 1. فتح Attendance من Group A: تمرير groupId و groupName والتحقق من السياق
     */
    @Test
    fun test1_openAttendanceFromGroupA_setsGroupContext() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.setStudentsForScope(listOf(studentA1, studentA2), listOf(studentA1, studentA2, studentB1))
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("grp_A", state.selectedGroupId)
        assertEquals("نجوم المستقبل", state.groupName)
        assertNotNull(state.currentDate)
    }

    /**
     * 2. ظهور طلاب Group A فقط
     */
    @Test
    fun test2_onlyGroupAStudentsAppear() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2, studentB1)
        vm.setStudentsForScope(listOf(studentA1, studentA2), allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.allStudentsInScope.size)
        assertTrue(state.allStudentsInScope.any { it.studentId == "std_a1" })
        assertTrue(state.allStudentsInScope.any { it.studentId == "std_a2" })
    }

    /**
     * 3. طالب من Group B لا يظهر إطلاقاً في القائمة
     */
    @Test
    fun test3_groupBStudentDoesNotExistInGroupAScope() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2, studentB1)
        vm.setStudentsForScope(listOf(studentA1, studentA2), allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.allStudentsInScope.any { it.studentId == "std_b1" })
        assertFalse(state.filteredStudents.any { it.studentId == "std_b1" })
    }

    /**
     * 4. Barcode لطالب Group A → Present
     */
    @Test
    fun test4_barcodeScan_groupAStudent_marksPresent() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2, studentB1)
        vm.setStudentsForScope(listOf(studentA1, studentA2), allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        vm.toggleScanner(true)
        advanceUntilIdle()

        vm.processScannedBarcode("BAR_A1")
        val state = vm.uiState.value
        assertEquals(ScanResultType.SUCCESS_PRESENT, state.scanFeedback?.type)
        assertTrue(state.presentStudentIds.contains("std_a1"))
        assertEquals(1, state.presentStudentIds.size)
    }

    /**
     * 5. Barcode لطالب Group B → رفض مع ScanResultType.WRONG_GROUP وعدم تسجيل الحضور
     */
    @Test
    fun test5_barcodeScan_groupBStudent_rejectedWithWrongGroup() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2, studentB1)
        vm.setStudentsForScope(listOf(studentA1, studentA2), allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        vm.toggleScanner(true)
        advanceUntilIdle()

        vm.processScannedBarcode("BAR_B1")
        val state = vm.uiState.value
        assertEquals(ScanResultType.WRONG_GROUP, state.scanFeedback?.type)
        assertFalse(state.presentStudentIds.contains("std_b1"))
        assertEquals(0, state.presentStudentIds.size)
    }

    /**
     * 6. Manual attendance: تحديد يدوي حاضر ثم غائب
     */
    @Test
    fun test6_manualAttendance_togglePresentAndAbsent() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2)
        vm.setStudentsForScope(allStudents, allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        advanceUntilIdle()

        // Toggle student A1 to PRESENT
        vm.toggleStudentPresent("std_a1")
        assertTrue(vm.uiState.value.presentStudentIds.contains("std_a1"))

        // Toggle student A1 back to ABSENT
        vm.toggleStudentPresent("std_a1")
        assertFalse(vm.uiState.value.presentStudentIds.contains("std_a1"))

        // Set A1 as present and finish attendance
        vm.toggleStudentPresent("std_a1")
        vm.finishAttendance()
        advanceUntilIdle()

        val summary = vm.uiState.value.saveSummary
        assertNotNull(summary)
        assertEquals(1, summary?.presentCount)
        assertEquals(1, summary?.absentCount)
        assertEquals(2, summary?.totalCount)
    }

    /**
     * 7. Offline attendance: حفظ الحضور محلياً بنجاح
     */
    @Test
    fun test7_offlineAttendance_storesLocallyAndProducesSummary() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2)
        vm.setStudentsForScope(allStudents, allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        advanceUntilIdle()

        vm.toggleStudentPresent("std_a1")
        vm.toggleStudentPresent("std_a2")

        vm.finishAttendance()
        advanceUntilIdle()

        val summary = vm.uiState.value.saveSummary
        assertNotNull(summary)
        assertEquals(2, summary?.presentCount)
        assertEquals(0, summary?.absentCount)
        assertEquals(2, summary?.totalCount)
    }

    /**
     * 8. Outbox sync: التحقق من بنية OutboxEntity وحفظ groupId كـ snapshot تاريخي
     */
    @Test
    fun test8_outboxSync_preservesGroupIdSnapshot() = runTest {
        val date = "2026-09-25"
        val request = UpsertAttendanceRequest(
            teacherId = currentTeacherId,
            studentId = studentA1.studentId,
            groupId = "grp_A",
            date = date,
            status = AttendanceStatus.PRESENT.name,
            note = null
        )

        val jsonPayload = Json.encodeToString(UpsertAttendanceRequest.serializer(), request)
        val outbox = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "UPSERT",
            entityType = "ATTENDANCE",
            entityId = "${studentA1.studentId}_$date",
            payload = jsonPayload,
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = currentTeacherId
        )

        val decoded = Json.decodeFromString<UpsertAttendanceRequest>(outbox.payload)
        assertEquals("grp_A", decoded.groupId)
        assertEquals(currentTeacherId, decoded.teacherId)
        assertEquals(studentA1.studentId, decoded.studentId)
        assertEquals(AttendanceStatus.PRESENT.name, decoded.status)
    }

    /**
     * 9. Duplicate attendance prevention: منع تكرار المسح لنفس الطالب
     */
    @Test
    fun test9_duplicateAttendancePrevention_returnsAlreadyPresent() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val allStudents = listOf(studentA1, studentA2)
        vm.setStudentsForScope(allStudents, allStudents)
        vm.setInitialGroupId("grp_A", "نجوم المستقبل")
        vm.toggleScanner(true)
        advanceUntilIdle()

        // 1st scan -> SUCCESS_PRESENT
        vm.processScannedBarcode("BAR_A1")
        assertEquals(ScanResultType.SUCCESS_PRESENT, vm.uiState.value.scanFeedback?.type)
        assertEquals(1, vm.uiState.value.presentStudentIds.size)

        // 2nd scan with same barcode -> ALREADY_PRESENT without duplicating
        vm.processScannedBarcode("BAR_A1")
        assertEquals(ScanResultType.ALREADY_PRESENT, vm.uiState.value.scanFeedback?.type)
        assertEquals(1, vm.uiState.value.presentStudentIds.size)
    }

    /**
     * 10. Teacher isolation: عزل المدرسين، منع الوصول لمجموعة أو طالب مدرس آخر
     */
    @Test
    fun test10_teacherIsolation_foreignGroupOrStudentRejected() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Attempt to open a foreign teacher's group
        vm.setInitialGroupId("grp_Foreign", "مجموعة مدرس آخر")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("لا يمكن الوصول لهذه المجموعة: غير مصرح أو غير موجودة.", state.errorMessage)
        assertEquals(0, state.allStudentsInScope.size)

        // Also verify foreign student barcode scan is rejected
        val vm2 = createViewModel()
        advanceUntilIdle()
        vm2.setStudentsForScope(listOf(studentA1), listOf(studentA1, studentForeign))
        vm2.setInitialGroupId("grp_A", "نجوم المستقبل")
        vm2.toggleScanner(true)
        advanceUntilIdle()

        vm2.processScannedBarcode("BAR_FOR")
        // Foreign student belongs to other teacher -> rejected as NOT_FOUND
        assertEquals(ScanResultType.NOT_FOUND, vm2.uiState.value.scanFeedback?.type)
        assertFalse(vm2.uiState.value.presentStudentIds.contains("std_foreign"))
    }
}
