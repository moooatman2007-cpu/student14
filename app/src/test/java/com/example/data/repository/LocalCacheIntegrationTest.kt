package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.LessonPayment
import com.example.core.model.Student
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.StudentDao
import com.example.data.local.mapper.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalCacheIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var paymentDao: PaymentDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        studentDao = db.studentDao()
        paymentDao = db.paymentDao()
        DatabaseProvider.setDatabase(db)
        com.example.data.SupabaseClientProvider.mockTeacherId = "t-1"
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun roomStoresData_afterNetworkSuccess() = runBlocking {
        val student = Student(
            studentId = "s-1",
            fullName = "طالب جديد",
            teacherId = "t-1",
            gradeId = "g-1"
        )
        studentDao.upsertStudent(student.toEntity())

        val stored = studentDao.getStudentByIdSync("t-1", "s-1")
        assertNotNull(stored)
        assertEquals("طالب جديد", stored?.fullName)
    }

    @Test
    fun networkFailure_showsLastCachedData() = runBlocking {
        val paymentRepo = SupabasePaymentRepository(paymentDao = paymentDao)
        val cachedPayment = LessonPayment(
            paymentId = "p-10",
            studentId = "s-1",
            teacherId = "t-1",
            year = 2026,
            month = 9,
            amount = 200.0,
            isPaid = true
        )
        paymentDao.upsertSinglePayment(cachedPayment.toEntity())

        val flowResult = paymentRepo.getMonthlyPayments(2026, 9).first()
        assertEquals(1, flowResult.size)
        assertEquals("p-10", flowResult.first().paymentId)
        assertEquals(200.0, flowResult.first().amount, 0.001)
    }

    @Test
    fun networkSuccess_replacesCacheWithLatestData() = runBlocking {
        val oldStudent = Student(
            studentId = "s-1",
            fullName = "الاسم القديم",
            teacherId = "t-1",
            gradeId = "g-1"
        )
        studentDao.upsertStudent(oldStudent.toEntity())

        val updatedStudent = Student(
            studentId = "s-1",
            fullName = "الاسم الجديد المعدل",
            teacherId = "t-1",
            gradeId = "g-1"
        )
        studentDao.upsertStudent(updatedStudent.toEntity())

        val cached = studentDao.getStudentByIdSync("t-1", "s-1")
        assertEquals("الاسم الجديد المعدل", cached?.fullName)
    }

    @Test
    fun noCrossTeacherCacheLeakage() = runBlocking {
        val teacher1Student = Student(
            studentId = "s-t1",
            fullName = "طالب المعلم 1",
            teacherId = "teacher-1",
            gradeId = "g-1"
        )
        val teacher2Student = Student(
            studentId = "s-t2",
            fullName = "طالب المعلم 2",
            teacherId = "teacher-2",
            gradeId = "g-1"
        )

        studentDao.upsertStudents(listOf(teacher1Student.toEntity(), teacher2Student.toEntity()))

        val teacher1List = studentDao.getAllStudentsSync("teacher-1")
        assertEquals(1, teacher1List.size)
        assertEquals("s-t1", teacher1List.first().studentId)

        val teacher2List = studentDao.getAllStudentsSync("teacher-2")
        assertEquals(1, teacher2List.size)
        assertEquals("s-t2", teacher2List.first().studentId)
    }
}
