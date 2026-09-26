package com.example

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Grade
import com.example.core.model.Group
import com.example.core.model.Student
import com.example.core.model.Teacher
import com.example.data.SupabaseClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.GroupDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.mapper.toEntity
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.data.repository.SupabaseGroupRepository
import com.example.ui.add_edit_student.AddEditStudentEvent
import com.example.ui.add_edit_student.AddEditStudentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddEditStudentGroupSelectionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val teacherT1 = Teacher(id = "teacher_1", fullName = "أحمد المدرس", email = "t1@example.com")
    private val teacherT2 = Teacher(id = "teacher_2", fullName = "محمود المدرس", email = "t2@example.com")

    private val gradeG1 = Grade(id = "grade_1", name = "الصف الأول الإعدادي", teacherId = "teacher_1", displayOrder = 1)
    private val gradeG2 = Grade(id = "grade_2", name = "الصف الثاني الإعدادي", teacherId = "teacher_1", displayOrder = 2)

    private lateinit var teacherRepository: MockTeacherRepository
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var groupRepository: MockGroupRepository

    private fun createTestGroup(
        id: String,
        teacherId: String = "teacher_1",
        gradeId: String = "grade_1",
        name: String = "مجموعة تجريبية",
        active: Boolean = true,
        startTime: String = "14:00",
        endTime: String = "16:00"
    ): Group {
        return Group(
            id = id,
            teacherId = teacherId,
            gradeId = gradeId,
            name = name,
            active = active,
            startTime = startTime,
            endTime = endTime
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        teacherRepository = MockTeacherRepository(initialTeacher = teacherT1)
        gradeRepository = MockGradeRepository()
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        groupRepository = MockGroupRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. loadGroupsForSelectedGrade
    @Test
    fun `1 loadGroupsForSelectedGrade loads active groups for the selected grade`() = runTest {
        val group1 = createTestGroup(id = "grp_1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة أ", active = true)
        val group2 = createTestGroup(id = "grp_2", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة ب", active = true)
        val groupOtherGrade = createTestGroup(id = "grp_3", teacherId = "teacher_1", gradeId = "grade_2", name = "مجموعة ج", active = true)
        groupRepository.setGroups(listOf(group1, group2, groupOtherGrade))

        val savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1"))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("grade_1", state.selectedGradeId)
        assertEquals(2, state.groups.size)
        assertTrue(state.groups.any { it.id == "grp_1" })
        assertTrue(state.groups.any { it.id == "grp_2" })
        assertFalse(state.groups.any { it.id == "grp_3" })
    }

    // 2. groups filtered by teacher
    @Test
    fun `2 groups filtered by teacher isolates groups from other teachers`() = runTest {
        val groupT1 = createTestGroup(id = "grp_t1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة مدرسي", active = true)
        val groupT2 = createTestGroup(id = "grp_t2", teacherId = "teacher_2", gradeId = "grade_1", name = "مجموعة مدرس آخر", active = true)
        groupRepository.setGroups(listOf(groupT1, groupT2))

        val savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1"))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val groups = viewModel.uiState.value.groups
        assertEquals(1, groups.size)
        assertEquals("grp_t1", groups.first().id)
    }

    // 3. groups filtered by grade
    @Test
    fun `3 groups filtered by grade isolates groups from other grades`() = runTest {
        val groupG1 = createTestGroup(id = "grp_g1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة أولى", active = true)
        val groupG2 = createTestGroup(id = "grp_g2", teacherId = "teacher_1", gradeId = "grade_2", name = "مجموعة ثانية", active = true)
        groupRepository.setGroups(listOf(groupG1, groupG2))

        val savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1"))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val groups = viewModel.uiState.value.groups
        assertEquals(1, groups.size)
        assertEquals("grp_g1", groups.first().id)
    }

    // 4. changing grade clears incompatible group
    @Test
    fun `4 changing grade clears incompatible group`() = runTest {
        val groupG1 = createTestGroup(id = "grp_g1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة أولى", active = true)
        val groupG2 = createTestGroup(id = "grp_g2", teacherId = "teacher_1", gradeId = "grade_2", name = "مجموعة ثانية", active = true)
        groupRepository.setGroups(listOf(groupG1, groupG2))

        val savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1"))
        val viewModel = AddEditStudentViewModel(
            savedStateHandle = savedStateHandle,
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Select Group G1
        viewModel.onGroupSelected("grp_g1")
        assertEquals("grp_g1", viewModel.uiState.value.selectedGroupId)

        // Switch Grade to grade_2
        viewModel.onGradeSelected("grade_2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("grade_2", state.selectedGradeId)
        assertNull("Selected group should be cleared when grade changes", state.selectedGroupId)
        assertEquals(1, state.groups.size)
        assertEquals("grp_g2", state.groups.first().id)
    }

    // 5. valid group assignment succeeds
    @Test
    fun `5 valid group assignment succeeds`() = runTest {
        val group = createTestGroup(id = "grp_valid", teacherId = "teacher_1", gradeId = "grade_1", name = "نجوم المستقبل", active = true)
        groupRepository.setGroups(listOf(group))

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1")),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("محمد علي حسن")
        viewModel.onParentPhoneChange("01012345678")
        viewModel.onGroupSelected("grp_valid")

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showSuccessDialog)
        assertNotNull(state.createdStudent)
        assertEquals("grp_valid", state.createdStudent?.groupId)
    }

    // 6. invalid teacher group rejected
    @Test
    fun `6 invalid teacher group rejected`() = runTest {
        val groupT2 = createTestGroup(id = "grp_t2", teacherId = "teacher_2", gradeId = "grade_1", name = "مجموعة مدرس آخر", active = true)
        groupRepository.setGroups(listOf(groupT2))

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1")),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("طالب غير صالح")
        viewModel.onParentPhoneChange("01012345678")
        viewModel.onGroupSelected("grp_t2")

        val events = mutableListOf<AddEditStudentEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showSuccessDialog)
        assertTrue(events.any { it is AddEditStudentEvent.ShowError && it.message == "المجموعة غير متوافقة مع الصف الدراسي." })
        job.cancel()
    }

    // 7. invalid grade-group combination rejected
    @Test
    fun `7 invalid grade-group combination rejected`() = runTest {
        val groupG2 = createTestGroup(id = "grp_g2", teacherId = "teacher_1", gradeId = "grade_2", name = "مجموعة الصف الثاني", active = true)
        groupRepository.setGroups(listOf(groupG2))

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1")),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("طالب صف أول")
        viewModel.onParentPhoneChange("01012345678")
        // Force an invalid group belonging to grade_2 while selectedGradeId is grade_1
        viewModel.onGroupSelected("grp_g2")

        val events = mutableListOf<AddEditStudentEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showSuccessDialog)
        assertTrue(events.any { it is AddEditStudentEvent.ShowError && it.message == "المجموعة غير متوافقة مع الصف الدراسي." })
        job.cancel()
    }

    // 8. removing group works
    @Test
    fun `8 removing group works`() = runTest {
        val group = createTestGroup(id = "grp_1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة 1", active = true)
        groupRepository.setGroups(listOf(group))

        val addResult = studentRepository.addStudent(
            fullName = "ياسين أحمد",
            gradeId = "grade_1",
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow().copy(groupId = "grp_1", teacherId = "teacher_1")
        studentRepository.updateStudent(student)

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("grp_1", viewModel.uiState.value.selectedGroupId)

        // Select no group
        viewModel.onGroupSelected(null)
        assertNull(viewModel.uiState.value.selectedGroupId)

        val events = mutableListOf<AddEditStudentEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(events.any { it is AddEditStudentEvent.StudentUpdated })
        val updated = studentRepository.getStudentById(student.studentId)
        assertNull(updated?.groupId)
        job.cancel()
    }

    // 9. inactive current group remains visible on edit
    @Test
    fun `9 inactive current group remains visible on edit`() = runTest {
        val inactiveCurrentGroup = createTestGroup(id = "grp_inactive_1", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة قديمة مغلقة", active = false)
        val inactiveOtherGroup = createTestGroup(id = "grp_inactive_2", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة قديمة أخرى", active = false)
        val activeGroup = createTestGroup(id = "grp_active", teacherId = "teacher_1", gradeId = "grade_1", name = "مجموعة نشطة", active = true)
        groupRepository.setGroups(listOf(inactiveCurrentGroup, inactiveOtherGroup, activeGroup))

        val addResult = studentRepository.addStudent(
            fullName = "خالد سامي",
            gradeId = "grade_1",
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrThrow().copy(groupId = "grp_inactive_1", teacherId = "teacher_1")
        studentRepository.updateStudent(student)

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to student.studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("grp_inactive_1", state.selectedGroupId)
        // Groups list should include the student's inactive group AND active groups, but NOT other inactive groups
        assertTrue(state.groups.any { it.id == "grp_inactive_1" })
        assertTrue(state.groups.any { it.id == "grp_active" })
        assertFalse(state.groups.any { it.id == "grp_inactive_2" })
    }

    // 10. no groups -> student can still be saved
    @Test
    fun `10 no groups - student can still be saved`() = runTest {
        groupRepository.setGroups(emptyList())

        val viewModel = AddEditStudentViewModel(
            savedStateHandle = SavedStateHandle(mapOf("gradeId" to "grade_1")),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            groupRepository = groupRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.groups.isEmpty())
        assertNull(state.selectedGroupId)

        viewModel.onFullNameChange("طالب بدون مجموعة")
        viewModel.onParentPhoneChange("01012345678")

        viewModel.saveStudent()
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertTrue(finalState.showSuccessDialog)
        assertNotNull(finalState.createdStudent)
        assertNull(finalState.createdStudent?.groupId)
    }

    // 11. offline student update keeps groupId locally in Room
    @Test
    fun `11 offline student update keeps groupId locally in Room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val studentDao = db.studentDao()
        val groupDao = db.groupDao()
        val outboxDao = db.outboxDao()

        DatabaseProvider.setDatabase(db, context)
        SupabaseClientProvider.mockTeacherId = "teacher_1"

        try {
            val group = createTestGroup(id = "grp_1", teacherId = "teacher_1", gradeId = "grade_1", name = "المتفوقين", active = true)
            groupDao.insertGroup(group.toEntity())

            val student = Student(
                studentId = "s_offline_1",
                studentCode = "ST-10001",
                fullName = "طارق عمر",
                gradeId = "grade_1",
                parentPhone = "01012345678",
                teacherId = "teacher_1"
            )
            studentDao.upsertStudent(student.toEntity())

            val supabaseGroupRepo = SupabaseGroupRepository(
                groupDao = groupDao,
                studentDao = studentDao,
                outboxDao = outboxDao
            )

            // Assign student to group offline
            val assignResult = supabaseGroupRepo.assignStudentToGroup("teacher_1", "s_offline_1", "grp_1")
            assertTrue(assignResult.isSuccess)

            val localStudent = studentDao.getStudentByIdSync("teacher_1", "s_offline_1")
            assertNotNull(localStudent)
            assertEquals("grp_1", localStudent?.groupId)
        } finally {
            db.close()
            DatabaseProvider.resetForTesting()
        }
    }

    // 12. Outbox contains correct student update
    @Test
    fun `12 Outbox contains correct student update on group assignment`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val studentDao = db.studentDao()
        val groupDao = db.groupDao()
        val outboxDao = db.outboxDao()

        DatabaseProvider.setDatabase(db, context)
        SupabaseClientProvider.mockTeacherId = "teacher_1"

        try {
            val group = createTestGroup(id = "grp_1", teacherId = "teacher_1", gradeId = "grade_1", name = "المتفوقين", active = true)
            groupDao.insertGroup(group.toEntity())

            val student = Student(
                studentId = "s_outbox_1",
                studentCode = "ST-10002",
                fullName = "إسلام صبحي",
                gradeId = "grade_1",
                parentPhone = "01012345678",
                teacherId = "teacher_1"
            )
            studentDao.upsertStudent(student.toEntity())

            val supabaseGroupRepo = SupabaseGroupRepository(
                groupDao = groupDao,
                studentDao = studentDao,
                outboxDao = outboxDao
            )

            supabaseGroupRepo.assignStudentToGroup("teacher_1", "s_outbox_1", "grp_1")

            val pendingOps = outboxDao.getPendingOperationsForTeacher("teacher_1")
            val studentOp = pendingOps.find { it.entityId == "s_outbox_1" }
            assertNotNull(studentOp)
            assertEquals("UPDATE", studentOp?.operationType)
            assertEquals("STUDENT", studentOp?.entityType)
            assertEquals("teacher_1", studentOp?.teacherId)
            assertTrue(studentOp?.payload?.contains("grp_1") == true || studentOp?.payload?.contains("s_outbox_1") == true)
        } finally {
            db.close()
            DatabaseProvider.resetForTesting()
        }
    }
}
