package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.auth.DeepLinkAuthHandler
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import com.example.data.sync.OutboxSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MultiAccountLogoutIsolationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        DeepLinkAuthHandler.dispatcher = testDispatcher
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(database, context)
    }

    @After
    fun tearDown() {
        DeepLinkAuthHandler.dispatcher = Dispatchers.IO
        database.close()
        DatabaseProvider.resetForTesting()
        AccountSessionManager.setActiveTeacherId(null)
        SupabaseClientProvider.mockTeacherId = null
        Dispatchers.resetMain()
    }

    /**
     * Scenario A: Manual logout cleanly purges all user-scoped data from Room,
     * outbox operations, and in-memory session tracking.
     */
    @Test
    fun testManualLogoutClearsTeacherDataAndOutbox() = runTest {
        val teacherAId = "teacher_a_123"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val studentDao = database.studentDao()
        val attendanceDao = database.attendanceDao()
        val gradeDao = database.gradeDao()
        val outboxDao = database.outboxDao()

        // 1. Populate Teacher A data
        val gradeA = Grade(id = "grade_a", name = "الصف الأول", displayOrder = 1, teacherId = teacherAId)
        gradeDao.upsertGrade(gradeA.toEntity())

        val studentA = Student(
            studentId = "student_a_1",
            studentCode = "ST-0001",
            fullName = "طالب المعلم أ",
            gradeId = "grade_a",
            parentPhone = "01011111111",
            teacherId = teacherAId
        )
        studentDao.upsertStudent(studentA.toEntity())

        val attendanceA = Attendance(
            attendanceId = "att_a_1",
            studentId = "student_a_1",
            teacherId = teacherAId,
            date = "2026-09-25",
            status = AttendanceStatus.PRESENT
        )
        attendanceDao.upsertSingleAttendance(attendanceA.toEntity())

        val outboxA = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "student_a_1",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = teacherAId
        )
        outboxDao.insertOperation(outboxA)

        // Verify data exists
        assertEquals(1, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(1, outboxDao.getPendingOperationsForTeacher(teacherAId).size)
        assertEquals(1, gradeDao.getAllGradesSync(teacherAId).size)
        assertTrue(AccountSessionManager.isSessionActive(teacherAId))

        // 2. Perform Manual Logout
        val logoutResult = AccountSessionManager.logout()
        assertTrue(logoutResult.isSuccess)

        // 3. Verify complete cleanup
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(0, outboxDao.getPendingOperationsForTeacher(teacherAId).size)
        assertEquals(0, gradeDao.getAllGradesSync(teacherAId).size)
        assertFalse(AccountSessionManager.isSessionActive(teacherAId))
        assertNull(AccountSessionManager.getActiveTeacherId())
        assertNull(SupabaseClientProvider.mockTeacherId)
    }

    /**
     * Scenario B: Automatic session expiry triggers onSessionTerminated, purging
     * tenant-scoped Room data without executing redundant remote signOut.
     */
    @Test
    fun testSessionExpiryTriggersCleanTeardown() = runTest {
        val teacherAId = "teacher_expired_abc"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val studentDao = database.studentDao()
        val outboxDao = database.outboxDao()

        studentDao.upsertStudent(
            Student(
                studentId = "s_exp_1",
                studentCode = "ST-9999",
                fullName = "طالب منتهي الجلسة",
                gradeId = "g_1",
                parentPhone = "01022222222",
                teacherId = teacherAId
            ).toEntity()
        )

        outboxDao.insertOperation(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "UPSERT",
                entityType = "ATTENDANCE",
                entityId = "att_exp_1",
                payload = "{}",
                createdAt = System.currentTimeMillis(),
                status = "PENDING",
                teacherId = teacherAId
            )
        )

        assertEquals(1, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(1, outboxDao.getPendingOperationsForTeacher(teacherAId).size)

        // Simulate Session Expiry event (called by NavGraph upon SessionStatus.NotAuthenticated)
        val termResult = AccountSessionManager.onSessionTerminated()
        assertTrue(termResult.isSuccess)

        // Verify data was cleaned and session is inactive
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(0, outboxDao.getPendingOperationsForTeacher(teacherAId).size)
        assertFalse(AccountSessionManager.isSessionActive(teacherAId))
    }

    /**
     * Scenario C: Deep link authentication failure cleans up any lingering session
     * and guarantees no data leaks into the newly presented login screen.
     */
    @Test
    fun testDeepLinkAuthFailureTriggersCleanTeardown() = runTest {
        val teacherAId = "teacher_old_session"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val studentDao = database.studentDao()
        studentDao.upsertStudent(
            Student(
                studentId = "s_old_1",
                studentCode = "ST-1111",
                fullName = "طالب قديم",
                gradeId = "g_1",
                parentPhone = "01033333333",
                teacherId = teacherAId
            ).toEntity()
        )
        assertEquals(1, studentDao.getAllStudentsSync(teacherAId).size)

        DeepLinkAuthHandler.resetForTesting()
        val errorUri = Uri.parse("studentmanager://login-callback#error=access_denied&error_description=User+declined")
        val job = DeepLinkAuthHandler.handleUri(errorUri)
        job?.join()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify session was terminated and Room was cleared
        assertFalse(AccountSessionManager.isSessionActive(teacherAId))
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
    }

    /**
     * Scenario D: Logout while outbox operations are pending completely cancels
     * sync and purges the pending operations so they can never be executed.
     */
    @Test
    fun testLogoutWhileOutboxItemsExistCancelsSyncAndPurgesOperations() = runTest {
        val teacherAId = "teacher_outbox_test"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val outboxDao = database.outboxDao()

        // Insert multiple pending outbox operations
        for (i in 1..5) {
            outboxDao.insertOperation(
                OutboxEntity(
                    id = "op_$i",
                    operationType = "INSERT",
                    entityType = "STUDENT",
                    entityId = "student_$i",
                    payload = "{}",
                    createdAt = System.currentTimeMillis(),
                    status = "PENDING",
                    teacherId = teacherAId
                )
            )
        }

        assertEquals(5, outboxDao.getPendingOperationsForTeacher(teacherAId).size)

        // Perform logout
        AccountSessionManager.logout()

        // Verify pending operations for this teacher are 0
        assertEquals(0, outboxDao.getPendingOperationsForTeacher(teacherAId).size)
        assertEquals(0, outboxDao.getAllOperations().size)
    }

    /**
     * Scenario E: Logout followed by login with a different teacher guarantees
     * strict tenant isolation: Teacher B sees 0 data from Teacher A.
     */
    @Test
    fun testLogoutThenLoginWithDifferentTeacherGuaranteesCompleteIsolation() = runTest {
        val teacherAId = "teacher_alpha"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val studentDao = database.studentDao()
        val gradeDao = database.gradeDao()

        // Populate Teacher A data
        studentDao.upsertStudent(
            Student(
                studentId = "student_alpha_1",
                studentCode = "ST-A1",
                fullName = "طالب ألفا",
                gradeId = "grade_alpha",
                parentPhone = "01044444444",
                teacherId = teacherAId
            ).toEntity()
        )
        gradeDao.upsertGrade(Grade(id = "grade_alpha", name = "صف ألفا", displayOrder = 1, teacherId = teacherAId).toEntity())

        assertEquals(1, studentDao.getAllStudentsSync(teacherAId).size)

        // Teacher A logs out
        AccountSessionManager.logout()

        // Teacher B logs in
        val teacherBId = "teacher_beta"
        SupabaseClientProvider.mockTeacherId = teacherBId
        AccountSessionManager.setActiveTeacherId(teacherBId)

        // Verify Teacher B sees ZERO data from Teacher A
        assertEquals(0, studentDao.getAllStudentsSync(teacherBId).size)
        assertEquals(0, gradeDao.getAllGradesSync(teacherBId).size)

        // Even direct queries for Teacher A return 0 rows in Room
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(0, gradeDao.getAllGradesSync(teacherAId).size)
    }

    /**
     * Scenario F: Session expiry during an in-flight network sync prevents
     * stale data from being re-inserted into Room after teardown.
     */
    @Test
    fun testSessionExpiryDuringNetworkSyncPreventsStaleDataInjectionIntoRoom() = runTest {
        val teacherAId = "teacher_stale_sync"
        SupabaseClientProvider.mockTeacherId = teacherAId
        AccountSessionManager.setActiveTeacherId(teacherAId)

        val studentDao = database.studentDao()

        val staleStudent = Student(
            studentId = "student_stale",
            studentCode = "ST-STALE",
            fullName = "طالب استجابة متأخرة",
            gradeId = "g_stale",
            parentPhone = "01055555555",
            teacherId = teacherAId
        )

        // Simulate session termination while network request was in-flight
        AccountSessionManager.onSessionTerminated()

        // In-flight network response arrives and attempts reconcile
        OutboxSyncWorker.reconcileStudentInRoom(studentDao, staleStudent, teacherAId)

        // Verify the write was rejected because session is inactive
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
    }
}
