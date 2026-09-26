package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.model.Attendance
import com.example.core.model.Exam
import com.example.core.model.InsertStudentRequest
import com.example.core.model.Student
import com.example.data.SupabaseClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.ExamDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ExamEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import com.example.data.repository.SupabaseStudentRepository
import com.example.data.sync.OutboxSyncWorker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class StudentCodeReconciliationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var examDao: ExamDao
    private lateinit var studentRepository: SupabaseStudentRepository

    private val teacherA = "teacher_alpha_test"
    private val teacherB = "teacher_beta_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db, context)

        studentDao = db.studentDao()
        outboxDao = db.outboxDao()
        attendanceDao = db.attendanceDao()
        examDao = db.examDao()

        SupabaseClientProvider.mockTeacherId = teacherA
        studentRepository = SupabaseStudentRepository(studentDao, outboxDao)
    }

    @After
    fun tearDown() {
        OutboxSyncWorker.operationProcessor = null
        SupabaseClientProvider.mockTeacherId = null
        db.close()
        DatabaseProvider.resetForTesting()
    }

    /**
     * Test 1: Offline student creation -> temporary non-colliding code -> sync -> final server code reconciliation.
     */
    @Test
    fun testOfflineStudentCreation_thenSync_reconcilesToFinalServerCode() = runBlocking {
        // Offline addition falls back to local Room + Outbox
        val result = studentRepository.addStudent(
            fullName = "ياسين أحمد",
            gradeId = "grade_1",
            parentPhone = "01011112222",
            hasWhatsApp = true,
            alternativePhone = null
        )

        assertTrue("Student creation should succeed", result.isSuccess)
        val createdStudent = result.getOrNull()
        assertNotNull(createdStudent)
        val localStudentId = createdStudent!!.studentId

        // Verify temporary code structure
        val initialRoomStudent = studentDao.getStudentByIdSync(teacherA, localStudentId)
        assertNotNull(initialRoomStudent)
        assertTrue("Offline temporary code must start with ST-", initialRoomStudent!!.studentCode.startsWith("ST-"))
        assertTrue("Offline temporary code must contain TMP", initialRoomStudent.studentCode.contains("TMP"))

        // Verify Outbox queued
        val pendingOps = outboxDao.getPendingOperationsForTeacher(teacherA)
        assertEquals(1, pendingOps.size)
        val op = pendingOps[0]
        assertEquals("INSERT", op.operationType)
        assertEquals("STUDENT", op.entityType)
        assertEquals(localStudentId, op.entityId)

        // Simulate server assigning authoritative code (e.g. ST-00001) during sync
        val serverGeneratedStudent = createdStudent.copy(
            studentCode = "ST-00001"
        )
        OutboxSyncWorker.reconcileStudentInRoom(studentDao, serverGeneratedStudent, teacherA)

        // Verify Room updated to final server code while preserving ID
        val reconciledRoomStudent = studentDao.getStudentByIdSync(teacherA, localStudentId)
        assertNotNull(reconciledRoomStudent)
        assertEquals("ST-00001", reconciledRoomStudent!!.studentCode)
        assertEquals(localStudentId, reconciledRoomStudent.studentId)
        assertEquals("ياسين أحمد", reconciledRoomStudent.fullName)

        // Verify student can be looked up by final code
        val lookupByCode = studentDao.getStudentByCode(teacherA, "ST-00001")
        assertNotNull(lookupByCode)
        assertEquals(localStudentId, lookupByCode!!.studentId)
    }

    /**
     * Test 2: Two offline students created close together (rapid succession)
     * must never have colliding student codes.
     */
    @Test
    fun testTwoOfflineStudentsCreatedCloseTogether_haveDistinctCodesAndNoCollisions() = runBlocking {
        val s1Result = studentRepository.addStudent(
            fullName = "طالب أول سريع",
            gradeId = "grade_1",
            parentPhone = "01000000001",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val s2Result = studentRepository.addStudent(
            fullName = "طالب ثان سريع",
            gradeId = "grade_1",
            parentPhone = "01000000002",
            hasWhatsApp = false,
            alternativePhone = null
        )

        assertTrue(s1Result.isSuccess)
        assertTrue(s2Result.isSuccess)

        val s1 = s1Result.getOrNull()!!
        val s2 = s2Result.getOrNull()!!

        assertNotEquals("Student IDs must be unique", s1.studentId, s2.studentId)
        assertNotEquals("Student Codes must never collide", s1.studentCode, s2.studentCode)

        assertTrue(s1.studentCode.startsWith("ST-"))
        assertTrue(s2.studentCode.startsWith("ST-"))

        // Lookup each by code in Room
        val foundS1 = studentDao.getStudentByCode(teacherA, s1.studentCode)
        val foundS2 = studentDao.getStudentByCode(teacherA, s2.studentCode)

        assertNotNull(foundS1)
        assertNotNull(foundS2)
        assertEquals(s1.studentId, foundS1!!.studentId)
        assertEquals(s2.studentId, foundS2!!.studentId)
    }

    /**
     * Test 3: Retry after failed sync does not corrupt state and succeeds on subsequent attempt.
     */
    @Test
    fun testRetryAfterFailedSync_doesNotCorruptStateAndSucceedsOnNextAttempt() = runBlocking {
        val addResult = studentRepository.addStudent(
            fullName = "طارق محمود",
            gradeId = "grade_2",
            parentPhone = "01122334455",
            hasWhatsApp = true,
            alternativePhone = null
        )
        val student = addResult.getOrNull()!!
        val pendingOps = outboxDao.getPendingOperationsForTeacher(teacherA)
        val op = pendingOps[0]

        // Attempt 1: Transient network failure
        OutboxSyncWorker.operationProcessor = {
            throw IOException("503 Service Temporarily Unavailable")
        }

        val worker1 = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result1 = worker1.doWork()
        assertEquals(ListenableWorker.Result.retry(), result1)

        val updatedOp1 = outboxDao.getOperationById(op.id)
        assertNotNull(updatedOp1)
        assertEquals("PENDING", updatedOp1!!.status)
        assertEquals(1, updatedOp1.retryCount)

        // Room student is intact with temporary code
        val roomBeforeRetry = studentDao.getStudentByIdSync(teacherA, student.studentId)
        assertNotNull(roomBeforeRetry)
        assertEquals(student.studentCode, roomBeforeRetry!!.studentCode)

        // Attempt 2: Network recovered, sync succeeds
        OutboxSyncWorker.operationProcessor = { entity ->
            val serverStudent = student.copy(studentCode = "ST-00007")
            OutboxSyncWorker.reconcileStudentInRoom(studentDao, serverStudent, teacherA)
        }

        val worker2 = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result2 = worker2.doWork()
        assertEquals(ListenableWorker.Result.success(), result2)

        val updatedOp2 = outboxDao.getOperationById(op.id)
        assertNotNull(updatedOp2)
        assertEquals("SYNCED", updatedOp2!!.status)

        // Reconciled student in Room
        val roomAfterSync = studentDao.getStudentByIdSync(teacherA, student.studentId)
        assertNotNull(roomAfterSync)
        assertEquals("ST-00007", roomAfterSync!!.studentCode)
    }

    /**
     * Test 4: Duplicate or replayed outbox operation is completely idempotent and does not create duplicate student.
     */
    @Test
    fun testDuplicateOrReplayedOutboxOperation_isIdempotent() = runBlocking {
        val initialStudent = Student(
            studentId = "s_idempotent_1",
            studentCode = "ST-TMP-112233",
            fullName = "طالب مكرر الأوت بوكس",
            gradeId = "grade_1",
            parentPhone = "01099887766",
            teacherId = teacherA
        )
        studentDao.upsertStudent(initialStudent.toEntity())

        val serverStudent = initialStudent.copy(studentCode = "ST-00012")

        // First execution of reconciliation
        OutboxSyncWorker.reconcileStudentInRoom(studentDao, serverStudent, teacherA)

        val afterFirst = studentDao.getAllStudentsSync(teacherA)
        assertEquals(1, afterFirst.size)
        assertEquals("ST-00012", afterFirst[0].studentCode)

        // Second (duplicate/replayed) execution
        OutboxSyncWorker.reconcileStudentInRoom(studentDao, serverStudent, teacherA)

        val afterReplay = studentDao.getAllStudentsSync(teacherA)
        assertEquals("Replayed operation must not create duplicate students", 1, afterReplay.size)
        assertEquals("ST-00012", afterReplay[0].studentCode)
        assertEquals("s_idempotent_1", afterReplay[0].studentId)
    }

    /**
     * Test 5: Local foreign references (attendance, exams) are preserved across student code reconciliation.
     */
    @Test
    fun testServerGeneratedStudentCodeReconciliation_preservesLocalReferences() = runBlocking {
        val studentId = "student_with_references_1"
        val temporaryCode = "ST-TMP-ABCDEF"
        val offlineStudent = Student(
            studentId = studentId,
            studentCode = temporaryCode,
            fullName = "كريم سامي",
            gradeId = "grade_1",
            parentPhone = "01055556666",
            teacherId = teacherA
        )
        studentDao.upsertStudent(offlineStudent.toEntity())

        // Insert related attendance and exam referencing studentId
        attendanceDao.upsertSingleAttendance(
            AttendanceEntity(
                attendanceId = "att_1",
                studentId = studentId,
                teacherId = teacherA,
                date = "2026-09-26",
                status = "PRESENT",
                note = "حاضر في الموعد"
            )
        )
        examDao.upsertSingleExam(
            ExamEntity(
                examId = "exam_1",
                studentId = studentId,
                teacherId = teacherA,
                date = "2026-09-26",
                examName = "امتحان شهري",
                subject = "القرآن الكريم",
                score = 20.0,
                maxScore = 20.0
            )
        )

        // Reconcile server code
        val finalServerStudent = offlineStudent.copy(studentCode = "ST-00025")
        OutboxSyncWorker.reconcileStudentInRoom(studentDao, finalServerStudent, teacherA)

        // Check references remain 100% valid
        val studentAttendances = attendanceDao.getAttendanceByStudentSync(teacherA, studentId)
        assertEquals(1, studentAttendances.size)
        assertEquals("att_1", studentAttendances[0].attendanceId)

        val studentExams = examDao.getExamsByStudentSync(teacherA, studentId)
        assertEquals(1, studentExams.size)
        assertEquals("exam_1", studentExams[0].examId)
    }

    /**
     * Test 6: Existing student code must remain stable during student updates.
     */
    @Test
    fun testExistingStudentCode_remainsStableDuringUpdates() = runBlocking {
        val existingStudent = Student(
            studentId = "s_stable_code",
            studentCode = "ST-00045",
            fullName = "الاسم القديم",
            gradeId = "grade_1",
            parentPhone = "01011111111",
            teacherId = teacherA
        )
        studentDao.upsertStudent(existingStudent.toEntity())

        // Update student with empty studentCode passed in UI update request
        val updateCandidate = existingStudent.copy(
            fullName = "الاسم المعدل",
            studentCode = "" // UI or caller did not specify code
        )
        val updateResult = studentRepository.updateStudent(updateCandidate)
        assertTrue(updateResult.isSuccess)

        val savedInRoom = studentDao.getStudentByIdSync(teacherA, "s_stable_code")
        assertNotNull(savedInRoom)
        assertEquals("الاسم المعدل", savedInRoom!!.fullName)
        assertEquals("Existing student code must be preserved", "ST-00045", savedInRoom.studentCode)
    }

    /**
     * Test 7: Teacher A cannot affect or access Teacher B's student codes (tenant isolation).
     */
    @Test
    fun testTeacherIsolation_cannotAffectOrAccessOtherTeacherCodes() = runBlocking {
        // Teacher A student
        val studentA = Student(
            studentId = "s_teacher_a",
            studentCode = "ST-00001",
            fullName = "طالب المعلم أ",
            gradeId = "grade_1",
            parentPhone = "01011111111",
            teacherId = teacherA
        )
        studentDao.upsertStudent(studentA.toEntity())

        // Teacher B student with same code
        val studentB = Student(
            studentId = "s_teacher_b",
            studentCode = "ST-00001",
            fullName = "طالب المعلم ب",
            gradeId = "grade_2",
            parentPhone = "01022222222",
            teacherId = teacherB
        )
        studentDao.upsertStudent(studentB.toEntity())

        // Lookup by code for Teacher A returns ONLY Teacher A's student
        val lookupA = studentDao.getStudentByCode(teacherA, "ST-00001")
        assertNotNull(lookupA)
        assertEquals("s_teacher_a", lookupA!!.studentId)
        assertEquals(teacherA, lookupA.teacherId)

        // Lookup by code for Teacher B returns ONLY Teacher B's student
        val lookupB = studentDao.getStudentByCode(teacherB, "ST-00001")
        assertNotNull(lookupB)
        assertEquals("s_teacher_b", lookupB!!.studentId)
        assertEquals(teacherB, lookupB.teacherId)

        // Outbox isolation: Worker for Teacher A skips Teacher B operations
        val opB = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s_teacher_b",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = teacherB
        )
        outboxDao.insertOperation(opB)

        val workerA = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        workerA.doWork()

        val opBAfter = outboxDao.getOperationById(opB.id)
        assertNotNull(opBAfter)
        assertEquals("PENDING", opBAfter!!.status) // Not touched by Teacher A's worker
    }

    /**
     * Test 8: Room state equals server state after successful reconciliation.
     */
    @Test
    fun testRoomStateEqualsServerStateAfterSuccessfulReconciliation() = runBlocking {
        val localStudent = Student(
            studentId = "s_exact_reconcile",
            studentCode = "ST-TMP-888999",
            fullName = "زياد شريف",
            gradeId = "grade_3",
            parentPhone = "01033334444",
            hasWhatsApp = true,
            alternativePhone = "01055556666",
            teacherId = teacherA,
            groupId = "group_primary"
        )
        studentDao.upsertStudent(localStudent.toEntity())

        // Authoritative server state
        val serverStudent = Student(
            studentId = "s_exact_reconcile",
            studentCode = "ST-00099",
            fullName = "زياد شريف عبد الرحمن",
            gradeId = "grade_3",
            parentPhone = "01033334444",
            hasWhatsApp = true,
            alternativePhone = "01055556666",
            teacherId = teacherA,
            deletedAt = null,
            createdAtRaw = "2026-09-26T00:00:00Z",
            updatedAtRaw = "2026-09-26T01:00:00Z"
        )

        OutboxSyncWorker.reconcileStudentInRoom(studentDao, serverStudent, teacherA)

        val roomStudent = studentDao.getStudentByIdSync(teacherA, "s_exact_reconcile")
        assertNotNull(roomStudent)
        assertEquals(serverStudent.studentId, roomStudent!!.studentId)
        assertEquals(serverStudent.studentCode, roomStudent.studentCode)
        assertEquals(serverStudent.fullName, roomStudent.fullName)
        assertEquals(serverStudent.gradeId, roomStudent.gradeId)
        assertEquals(serverStudent.parentPhone, roomStudent.parentPhone)
        assertEquals(serverStudent.hasWhatsApp, roomStudent.hasWhatsApp)
        assertEquals(serverStudent.alternativePhone, roomStudent.alternativePhone)
        assertEquals(serverStudent.teacherId, roomStudent.teacherId)
        // Group assignment preserved
        assertEquals("group_primary", roomStudent.groupId)
    }
}
