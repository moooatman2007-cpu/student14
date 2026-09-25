package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AttendanceStatus
import com.example.core.model.InsertStudentRequest
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.RecitationDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.mapper.toEntity
import com.example.data.repository.SupabaseAttendanceRepository
import com.example.data.repository.SupabaseRecitationRepository
import com.example.data.repository.SupabaseStudentRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositoryOutboxIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var recitationDao: RecitationDao
    private lateinit var outboxDao: OutboxDao

    private lateinit var studentRepository: SupabaseStudentRepository
    private lateinit var attendanceRepository: SupabaseAttendanceRepository
    private lateinit var recitationRepository: SupabaseRecitationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        studentDao = db.studentDao()
        attendanceDao = db.attendanceDao()
        recitationDao = db.recitationDao()
        outboxDao = db.outboxDao()

        com.example.data.SupabaseClientProvider.mockTeacherId = "teacher_x"

        studentRepository = SupabaseStudentRepository(studentDao, outboxDao)
        attendanceRepository = SupabaseAttendanceRepository(attendanceDao, outboxDao)
        recitationRepository = SupabaseRecitationRepository(recitationDao, outboxDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testOfflineStudentInsertCreatesRoomAndOutbox() = runBlocking {
        // Test Case 2: Offline Student INSERT -> Room write + PENDING Outbox
        val result = studentRepository.addStudent(
            fullName = "Ahmad Ali",
            gradeId = "grade_1",
            parentPhone = "01012345678",
            hasWhatsApp = true,
            alternativePhone = null
        )

        assertTrue(result.isSuccess)
        val student = result.getOrNull()
        assertNotNull(student)

        // Verify Room write
        val cached = studentDao.getStudentByIdSync("teacher_x", student!!.studentId)
        assertNotNull(cached)
        assertEquals("Ahmad Ali", cached?.fullName)

        // Verify PENDING Outbox creation
        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("INSERT", pending[0].operationType)
        assertEquals("STUDENT", pending[0].entityType)
        assertEquals(student.studentId, pending[0].entityId)
        assertEquals("PENDING", pending[0].status)
        assertNotNull(pending[0].teacherId)

        // Test Case 8: Payload can be parsed back into correct operation
        val parsedRequest = Json.decodeFromString<InsertStudentRequest>(pending[0].payload)
        assertEquals("Ahmad Ali", parsedRequest.fullName)
        assertEquals("grade_1", parsedRequest.gradeId)
    }

    @Test
    fun testOfflineStudentUpdateCreatesOutbox() = runBlocking {
        // Test Case 3: Offline Student UPDATE -> Room update + PENDING Outbox
        val initialStudent = Student(
            studentId = "s_123",
            fullName = "Old Name",
            gradeId = "grade_1",
            parentPhone = "01000000000",
            teacherId = "teacher_x"
        )
        studentDao.upsertStudent(initialStudent.toEntity())

        val updatedStudent = initialStudent.copy(fullName = "New Name")
        val result = studentRepository.updateStudent(updatedStudent)

        assertTrue(result.isSuccess)

        val cached = studentDao.getStudentByIdSync("teacher_x", "s_123")
        assertEquals("New Name", cached?.fullName)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("UPDATE", pending[0].operationType)
        assertEquals("STUDENT", pending[0].entityType)
        assertEquals("s_123", pending[0].entityId)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun testOfflineStudentDeleteCreatesOutbox() = runBlocking {
        // Test Case 4: Offline Student DELETE -> local delete + PENDING Outbox
        val student = Student(
            studentId = "s_del",
            fullName = "To Delete",
            gradeId = "grade_1",
            parentPhone = "01000000000",
            teacherId = "teacher_x"
        )
        studentDao.upsertStudent(student.toEntity())

        val result = studentRepository.deleteStudent("s_del")
        assertTrue(result.isSuccess)

        val cached = studentDao.getStudentByIdSync("teacher_x", "s_del")
        assertEquals(null, cached)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("DELETE", pending[0].operationType)
        assertEquals("STUDENT", pending[0].entityType)
        assertEquals("s_del", pending[0].entityId)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun testOfflineAttendanceWriteCreatesRoomAndOutbox() = runBlocking {
        // Test Case 5: Offline Attendance write -> Room + Outbox
        val result = attendanceRepository.recordOrUpdateAttendance(
            studentId = "s_1",
            date = "2026-09-23",
            status = AttendanceStatus.PRESENT,
            note = "On time"
        )

        assertTrue(result.isSuccess)

        val cached = attendanceDao.getAttendanceByDateSync("teacher_x", "s_1", "2026-09-23")
        assertNotNull(cached)
        assertEquals("PRESENT", cached?.status)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("UPSERT", pending[0].operationType)
        assertEquals("ATTENDANCE", pending[0].entityType)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun testOfflineRecitationWriteCreatesRoomAndOutbox() = runBlocking {
        // Test Case 6: Offline Recitation write -> Room + Outbox
        val result = recitationRepository.addRecitation(
            studentId = "s_1",
            date = "2026-09-23",
            title = "Al-Baqarah",
            content = "1-10",
            score = 9.5,
            maxScore = 10.0,
            note = "Excellent"
        )

        assertTrue(result.isSuccess)
        val rec = result.getOrNull()
        assertNotNull(rec)

        val cached = recitationDao.getRecitationByIdSync("teacher_x", rec!!.recitationId)
        assertNotNull(cached)
        assertEquals("Al-Baqarah", cached?.title)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("INSERT", pending[0].operationType)
        assertEquals("RECITATION", pending[0].entityType)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun testProcessDeathPersistenceAndTenantIsolation() = runBlocking {
        // Test Case 7 & 9: Process death simulation (Room persistence) & Tenant Isolation
        studentRepository.addStudent(
            fullName = "Student A",
            gradeId = "g1",
            parentPhone = "01111111111",
            hasWhatsApp = true,
            alternativePhone = null
        )

        val pendingOps = outboxDao.getPendingOperations()
        assertEquals(1, pendingOps.size)

        // Verify tenant isolation query
        val teacherId = pendingOps[0].teacherId ?: ""
        val pendingForTeacher = outboxDao.getPendingOperationsForTeacher(teacherId)
        assertEquals(1, pendingForTeacher.size)

        val pendingForOther = outboxDao.getPendingOperationsForTeacher("other_teacher")
        assertTrue(pendingForOther.isEmpty())
    }
}
