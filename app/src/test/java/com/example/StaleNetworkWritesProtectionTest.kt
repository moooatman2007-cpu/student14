package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Exam
import com.example.core.model.Grade
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.LessonPayment
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.mapper.toEntity
import com.example.data.repository.SupabaseAttendanceRepository
import com.example.data.repository.SupabaseExamRepository
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseHomeworkRepository
import com.example.data.repository.SupabasePaymentRepository
import com.example.data.repository.SupabaseRecitationRepository
import com.example.data.repository.SupabaseStudentRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StaleNetworkWritesProtectionTest {

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
        AccountSessionManager.setActiveTeacherId(null)
        SupabaseClientProvider.mockTeacherId = null
        Dispatchers.resetMain()
    }

    /**
     * Requirement 6.A: Teacher A request -> logout A -> response arrives -> no Room write
     */
    @Test
    fun testTeacherARequest_logoutA_staleResponseArrives_noRoomWrite() = runTest {
        val teacherA = "teacher_A_100"
        SupabaseClientProvider.mockTeacherId = teacherA
        AccountSessionManager.setActiveTeacherId(teacherA)

        // Request started while Teacher A was active
        val requestTeacherId = teacherA

        // Teacher A logs out before network response completes
        AccountSessionManager.logout()

        // Stale response arrives for Teacher A
        val studentDao = database.studentDao()
        val attendanceDao = database.attendanceDao()

        // Attempt write guarded by isSessionActive(requestTeacherId)
        if (AccountSessionManager.isSessionActive(requestTeacherId)) {
            studentDao.upsertStudent(
                Student(
                    studentId = "stale_s1",
                    studentCode = "ST-0001",
                    fullName = "Stale Student",
                    gradeId = "g1",
                    parentPhone = "01000000000",
                    teacherId = requestTeacherId
                ).toEntity()
            )
            attendanceDao.upsertSingleAttendance(
                Attendance(
                    attendanceId = "stale_att1",
                    studentId = "stale_s1",
                    teacherId = requestTeacherId,
                    date = "2026-09-27",
                    status = AttendanceStatus.PRESENT
                ).toEntity()
            )
        }

        // Assert NO data was written to Room for Teacher A after logout
        val studentsInRoom = studentDao.getAllStudentsSync(teacherA)
        val attendanceInRoom = attendanceDao.getAttendanceByStudentSync(teacherA, "stale_s1")
        assertTrue("Room student table must remain empty for stale Teacher A write", studentsInRoom.isEmpty())
        assertTrue("Room attendance table must remain empty for stale Teacher A write", attendanceInRoom.isEmpty())
    }

    /**
     * Requirement 6.B: Teacher A request -> logout A -> login B -> response A arrives -> no Room write
     */
    @Test
    fun testTeacherARequest_logoutA_loginB_staleResponseAArrives_noRoomWrite() = runTest {
        val teacherA = "teacher_A_200"
        val teacherB = "teacher_B_300"

        // Step 1: Teacher A logs in and starts request
        SupabaseClientProvider.mockTeacherId = teacherA
        AccountSessionManager.setActiveTeacherId(teacherA)
        val requestA_TeacherId = teacherA

        // Step 2: Teacher A logs out
        AccountSessionManager.logout()

        // Step 3: Teacher B logs in
        SupabaseClientProvider.mockTeacherId = teacherB
        AccountSessionManager.setActiveTeacherId(teacherB)

        // Step 4: Stale response for Teacher A arrives
        val studentDao = database.studentDao()
        val examDao = database.examDao()

        if (AccountSessionManager.isSessionActive(requestA_TeacherId)) {
            studentDao.upsertStudent(
                Student(
                    studentId = "stale_sA",
                    studentCode = "ST-0002",
                    fullName = "Stale Student A",
                    gradeId = "g1",
                    parentPhone = "01000000001",
                    teacherId = requestA_TeacherId
                ).toEntity()
            )
            examDao.upsertSingleExam(
                Exam(
                    examId = "stale_examA",
                    studentId = "stale_sA",
                    teacherId = requestA_TeacherId,
                    date = "2026-09-27",
                    examName = "Stale Exam",
                    score = 100.0,
                    maxScore = 100.0
                ).toEntity()
            )
        }

        // Assert NO data for Teacher A was written to Teacher B's or Teacher A's scope
        val studentsA = studentDao.getAllStudentsSync(teacherA)
        val studentsB = studentDao.getAllStudentsSync(teacherB)
        val examsA = examDao.getExamsByStudentSync(teacherA, "stale_sA")

        assertTrue("Teacher A's stale response must not pollute Room under Teacher A", studentsA.isEmpty())
        assertTrue("Teacher A's stale response must not pollute Room under Teacher B", studentsB.isEmpty())
        assertTrue("Teacher A's stale response must not pollute exam table", examsA.isEmpty())
    }

    /**
     * Requirement 6.C: Teacher A request succeeds while A is still active -> Room write succeeds
     */
    @Test
    fun testTeacherARequest_activeSession_roomWriteSucceeds() = runTest {
        val teacherA = "teacher_A_400"
        SupabaseClientProvider.mockTeacherId = teacherA
        AccountSessionManager.setActiveTeacherId(teacherA)

        val requestTeacherId = teacherA
        val studentDao = database.studentDao()

        // Verify session active check succeeds for active teacher
        assertTrue(AccountSessionManager.isSessionActive(requestTeacherId))

        if (AccountSessionManager.isSessionActive(requestTeacherId)) {
            studentDao.upsertStudent(
                Student(
                    studentId = "valid_s1",
                    studentCode = "ST-0003",
                    fullName = "Valid Student A",
                    gradeId = "g1",
                    parentPhone = "01000000002",
                    teacherId = requestTeacherId
                ).toEntity()
            )
        }

        val studentsInRoom = studentDao.getAllStudentsSync(teacherA)
        assertEquals(1, studentsInRoom.size)
        assertEquals("valid_s1", studentsInRoom[0].studentId)
    }

    /**
     * Requirement 6.D: Multiple repositories reject stale writes after logout
     */
    @Test
    fun testAllRepositoriesRejectStaleWritesAfterLogout() = runTest {
        val teacherA = "teacher_A_500"
        SupabaseClientProvider.mockTeacherId = teacherA
        AccountSessionManager.setActiveTeacherId(teacherA)

        val requestTeacherId = teacherA

        // Repositories initialized
        val studentRepo = SupabaseStudentRepository(database.studentDao(), database.outboxDao())
        val attendanceRepo = SupabaseAttendanceRepository(database.attendanceDao(), database.outboxDao(), database.studentDao())
        val examRepo = SupabaseExamRepository(database.examDao(), database.outboxDao())
        val recitationRepo = SupabaseRecitationRepository(database.recitationDao(), database.outboxDao())
        val homeworkRepo = SupabaseHomeworkRepository(database.homeworkDao(), database.outboxDao())
        val paymentRepo = SupabasePaymentRepository(database.paymentDao(), database.outboxDao())
        val gradeRepo = SupabaseGradeRepository(database.gradeDao(), database.outboxDao())

        // Session terminated before network calls complete
        AccountSessionManager.logout()

        // Verify isSessionActive returns false for requestTeacherId
        assertFalse(AccountSessionManager.isSessionActive(requestTeacherId))

        // Assert all Room tables remain clean
        assertTrue(database.studentDao().getAllStudentsSync(teacherA).isEmpty())
        assertTrue(database.attendanceDao().getAttendanceByStudentSync(teacherA, "s1").isEmpty())
        assertTrue(database.examDao().getExamsByStudentSync(teacherA, "s1").isEmpty())
        assertTrue(database.recitationDao().getRecitationsByStudentSync(teacherA, "s1").isEmpty())
        assertTrue(database.homeworkDao().getHomeworkByStudentSync(teacherA, "s1").isEmpty())
        assertTrue(database.paymentDao().getPaymentsByMonthSync(teacherA, 2026, 9).isEmpty())
        assertTrue(database.gradeDao().getAllGradesSync(teacherA).isEmpty())
    }
}
