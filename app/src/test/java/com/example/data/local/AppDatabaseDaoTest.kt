package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Exam
import com.example.core.model.LessonPayment
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.ExamDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.RecitationDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var recitationDao: RecitationDao
    private lateinit var examDao: ExamDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var gradeDao: com.example.data.local.dao.GradeDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        studentDao = db.studentDao()
        attendanceDao = db.attendanceDao()
        recitationDao = db.recitationDao()
        examDao = db.examDao()
        paymentDao = db.paymentDao()
        gradeDao = db.gradeDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun saveAndReadStudent_andEmptyCache() = runBlocking {
        val teacherId = "t-1"
        // Initially empty
        val initialList = studentDao.getAllStudents(teacherId).first()
        assertTrue(initialList.isEmpty())

        val student = Student(
            studentId = "std-101",
            studentCode = "C101",
            fullName = "أحمد محمد",
            gradeId = "grade-1",
            parentPhone = "01000000000",
            teacherId = teacherId
        )
        studentDao.upsertStudent(student.toEntity())

        val retrievedList = studentDao.getAllStudents(teacherId).first()
        assertEquals(1, retrievedList.size)
        val domainStudent = retrievedList.first().toDomain()
        assertEquals("std-101", domainStudent.studentId)
        assertEquals("أحمد محمد", domainStudent.fullName)

        val single = studentDao.getStudentByIdSync(teacherId, "std-101")
        assertNotNull(single)
        assertEquals("C101", single?.studentCode)

        val notFound = studentDao.getStudentByIdSync(teacherId, "std-999")
        assertNull(notFound)
    }

    @Test
    fun saveAndReadAttendanceByStudent() = runBlocking {
        val teacherId = "t-1"
        // Empty check
        val emptyResult = attendanceDao.getAttendanceByStudent(teacherId, "std-101").first()
        assertTrue(emptyResult.isEmpty())

        val attendance1 = Attendance(
            attendanceId = "att-1",
            studentId = "std-101",
            teacherId = teacherId,
            date = "2026-09-20",
            status = AttendanceStatus.PRESENT,
            note = "حاضر في الموعد"
        )
        val attendance2 = Attendance(
            attendanceId = "att-2",
            studentId = "std-102",
            teacherId = teacherId,
            date = "2026-09-20",
            status = AttendanceStatus.ABSENT,
            note = "غياب"
        )
        attendanceDao.upsertAttendance(listOf(attendance1.toEntity(), attendance2.toEntity()))

        val listFor101 = attendanceDao.getAttendanceByStudent(teacherId, "std-101").first()
        assertEquals(1, listFor101.size)
        assertEquals("att-1", listFor101.first().attendanceId)
        assertEquals(AttendanceStatus.PRESENT, listFor101.first().toDomain().status)
    }

    @Test
    fun saveAndReadRecitationsByStudent() = runBlocking {
        val teacherId = "t-1"
        // Empty check
        val emptyRecitations = recitationDao.getRecitationsByStudent(teacherId, "std-101").first()
        assertTrue(emptyRecitations.isEmpty())

        val recitation = Recitation(
            recitationId = "rec-1",
            studentId = "std-101",
            teacherId = teacherId,
            date = "2026-09-21",
            title = "سورة الكهف",
            content = "الآيات 1 إلى 10",
            score = 10.0,
            maxScore = 10.0,
            note = "ممتاز"
        )
        recitationDao.upsertSingleRecitation(recitation.toEntity())

        val list = recitationDao.getRecitationsByStudent(teacherId, "std-101").first()
        assertEquals(1, list.size)
        val domain = list.first().toDomain()
        assertEquals("rec-1", domain.recitationId)
        assertEquals("سورة الكهف", domain.title)
        assertEquals(10.0, domain.score, 0.001)
    }

    @Test
    fun saveAndReadExamsByStudent() = runBlocking {
        val teacherId = "t-1"
        // Empty check
        val emptyExams = examDao.getExamsByStudent(teacherId, "std-101").first()
        assertTrue(emptyExams.isEmpty())

        val exam = Exam(
            examId = "exam-1",
            studentId = "std-101",
            teacherId = teacherId,
            date = "2026-09-22",
            examName = "امتحان التجويد",
            subject = "أحكام النون الساكنة",
            score = 95.0,
            maxScore = 100.0,
            note = "أداء رائع"
        )
        examDao.upsertSingleExam(exam.toEntity())

        val list = examDao.getExamsByStudent(teacherId, "std-101").first()
        assertEquals(1, list.size)
        val domain = list.first().toDomain()
        assertEquals("exam-1", domain.examId)
        assertEquals("امتحان التجويد", domain.examName)
        assertEquals(95.0, domain.score, 0.001)
    }

    @Test
    fun saveAndReadPaymentsByStudent() = runBlocking {
        val teacherId = "t-1"
        // Empty check
        val emptyPayments = paymentDao.getPaymentsByStudent(teacherId, "std-101").first()
        assertTrue(emptyPayments.isEmpty())

        val payment = LessonPayment(
            paymentId = "pay-1",
            studentId = "std-101",
            teacherId = teacherId,
            year = 2026,
            month = 9,
            amount = 150.0,
            isPaid = true,
            paidAt = "2026-09-01"
        )
        paymentDao.upsertSinglePayment(payment.toEntity())

        val list = paymentDao.getPaymentsByStudent(teacherId, "std-101").first()
        assertEquals(1, list.size)
        val domain = list.first().toDomain()
        assertEquals("pay-1", domain.paymentId)
        assertEquals(150.0, domain.amount, 0.001)
        assertTrue(domain.isPaid)
    }

    @Test
    fun saveAndReadGrades_andTenantIsolation() = runBlocking {
        val grade1 = com.example.core.model.Grade(
            id = "g-1",
            name = "الأول الإعدادي",
            displayOrder = 1,
            studentCount = 5,
            teacherId = "teacher-a"
        )
        val grade2 = com.example.core.model.Grade(
            id = "g-2",
            name = "الثاني الإعدادي",
            displayOrder = 2,
            studentCount = 8,
            teacherId = "teacher-b"
        )
        gradeDao.upsertGrades(listOf(grade1.toEntity(), grade2.toEntity()))

        // Teacher A should only see grade1
        val gradesA = gradeDao.getAllGrades("teacher-a").first()
        assertEquals(1, gradesA.size)
        assertEquals("g-1", gradesA[0].gradeId)
        assertEquals("الأول الإعدادي", gradesA[0].gradeName)

        // Teacher B should only see grade2
        val gradesB = gradeDao.getAllGrades("teacher-b").first()
        assertEquals(1, gradesB.size)
        assertEquals("g-2", gradesB[0].gradeId)
    }
}
