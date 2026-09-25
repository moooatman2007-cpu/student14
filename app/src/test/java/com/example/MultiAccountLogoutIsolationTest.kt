package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(database, context)
    }

    @After
    fun tearDown() {
        database.close()
        DatabaseProvider.resetForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testLogoutClearsRoomDatabaseAndOutboxAndCaches() = runTest {
        val teacherAId = "teacher_a_123"
        SupabaseClientProvider.mockTeacherId = teacherAId

        val studentDao = database.studentDao()
        val attendanceDao = database.attendanceDao()
        val gradeDao = database.gradeDao()
        val outboxDao = database.outboxDao()

        // 1. Insert Teacher A data into Room
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

        // Verify Teacher A data is present
        assertEquals(1, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(1, outboxDao.getPendingOperationsForTeacher(teacherAId).size)
        assertEquals(1, gradeDao.getAllGradesSync(teacherAId).size)

        // 2. Perform AccountSessionManager logout
        val logoutResult = AccountSessionManager.logout()
        assertTrue(logoutResult.isSuccess)

        // 3. Verify Room database is completely purged
        assertEquals(0, studentDao.getAllStudentsSync(teacherAId).size)
        assertEquals(0, outboxDao.getAllOperations().size)
        assertEquals(0, gradeDao.getAllGradesSync(teacherAId).size)

        // 4. Verify mockTeacherId is reset
        assertEquals(null, SupabaseClientProvider.mockTeacherId)

        // 5. Verify Teacher B logging in sees ZERO data from Teacher A
        val teacherBId = "teacher_b_456"
        SupabaseClientProvider.mockTeacherId = teacherBId

        assertEquals(0, studentDao.getAllStudentsSync(teacherBId).size)
        assertEquals(0, outboxDao.getPendingOperationsForTeacher(teacherBId).size)
        assertEquals(0, gradeDao.getAllGradesSync(teacherBId).size)
    }
}
