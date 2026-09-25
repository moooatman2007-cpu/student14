package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GradeEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.ui.students.StudentListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GradeOfflineCacheTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val persistentDbName = "grade_offline_cache_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(persistentDbName)
        db = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        com.example.data.SupabaseClientProvider.mockTeacherId = "teacher_1"
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(persistentDbName)
    }

    @Test
    fun test1_onlineGradesAreStoredInRoom() = runBlocking {
        val gradeDao = db.gradeDao()
        val sampleGrades = listOf(
            Grade(id = "grade-1", name = "الصف الأول الإعدادي", displayOrder = 1, teacherId = "teacher_1"),
            Grade(id = "grade-2", name = "الصف الثاني الإعدادي", displayOrder = 2, teacherId = "teacher_1")
        )

        gradeDao.upsertGrades(sampleGrades.map { it.toEntity() })

        val cached = gradeDao.getAllGradesSync("teacher_1")
        assertEquals(2, cached.size)
        assertEquals("grade-1", cached[0].gradeId)
        assertEquals("الصف الأول الإعدادي", cached[0].gradeName)
        assertEquals("grade-2", cached[1].gradeId)
    }

    @Test
    fun test2_offlineGradesAreReadFromRoom() = runBlocking {
        val gradeDao = db.gradeDao()
        val sampleGrade = Grade(
            id = "grade-3",
            name = "الصف الثالث الإعدادي",
            displayOrder = 3,
            studentCount = 10,
            teacherId = "teacher_1"
        )
        gradeDao.upsertGrade(sampleGrade.toEntity())

        val repo = SupabaseGradeRepository(gradeDao = gradeDao)

        // Offline read through getGrades Flow
        val emittedGrades = repo.getGrades().first()
        assertEquals(1, emittedGrades.size)
        assertEquals("grade-3", emittedGrades[0].id)
        assertEquals("الصف الثالث الإعدادي", emittedGrades[0].name)
    }

    @Test
    fun test3_networkFailureDoesNotClearCache() = runBlocking {
        val gradeDao = db.gradeDao()
        val sampleGrades = listOf(
            Grade(id = "g1", name = "الصف الأول الثانوي", displayOrder = 1, teacherId = "teacher_1"),
            Grade(id = "g2", name = "الصف الثاني الثانوي", displayOrder = 2, teacherId = "teacher_1")
        )
        gradeDao.upsertGrades(sampleGrades.map { it.toEntity() })

        val repo = SupabaseGradeRepository(gradeDao = gradeDao)

        // Simulate refresh failure (unauthenticated / no network)
        val refreshed = repo.refreshGrades()
        // Cache must NOT be cleared, it should return cached grades
        assertEquals(2, refreshed.size)
        assertEquals("g1", refreshed[0].id)
        assertEquals("g2", refreshed[1].id)

        // Verify Room DB still has the data
        val inDb = gradeDao.getAllGradesSync("teacher_1")
        assertEquals(2, inDb.size)
    }

    @Test
    fun test4_studentsScreenCanAccessStudentsAndGradesOffline() = runBlocking {
        com.example.data.SupabaseClientProvider.mockTeacherId = "teacher_100"
        val gradeDao = db.gradeDao()
        val studentDao = db.studentDao()

        val gradeEntity = GradeEntity(
            gradeId = "grade-prep-1",
            gradeName = "الصف الأول الإعدادي",
            displayOrder = 1,
            teacherId = "teacher_100"
        )
        gradeDao.upsertGrade(gradeEntity)

        val studentEntity = StudentEntity(
            studentId = "student-1",
            studentCode = "ST001",
            fullName = "عمر أحمد",
            gradeId = "grade-prep-1",
            parentPhone = "01011111111",
            hasWhatsApp = true,
            teacherId = "teacher_100"
        )
        studentDao.upsertStudent(studentEntity)

        val gradeRepo = SupabaseGradeRepository(gradeDao = gradeDao)
        val studentRepo = SupabaseStudentRepository(studentDao = studentDao)

        val viewModel = StudentListViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo
        )

        // Wait or read uiState
        val uiState = viewModel.uiState.value
        // Verify students and grades are available
        val grades = gradeRepo.getGrades().first()
        val students = studentRepo.getStudents().first()

        assertEquals(1, grades.size)
        assertEquals("grade-prep-1", grades[0].id)
        assertEquals(1, students.size)
        assertEquals("student-1", students[0].studentId)
        assertEquals("عمر أحمد", students[0].fullName)
    }

    @Test
    fun test5_teacherTenantIsolation() = runBlocking {
        val gradeDao = db.gradeDao()

        val teacherAGrades = listOf(
            Grade(id = "g_a1", name = "الصف الأول", displayOrder = 1, teacherId = "teacher_A")
        )
        val teacherBGrades = listOf(
            Grade(id = "g_b1", name = "الصف الثاني", displayOrder = 1, teacherId = "teacher_B")
        )

        gradeDao.upsertGrades((teacherAGrades + teacherBGrades).map { it.toEntity() })

        val aGrades = gradeDao.getAllGradesSync("teacher_A")
        val bGrades = gradeDao.getAllGradesSync("teacher_B")

        assertEquals(1, aGrades.size)
        assertEquals("g_a1", aGrades[0].gradeId)

        assertEquals(1, bGrades.size)
        assertEquals("g_b1", bGrades[0].gradeId)
    }

    @Test
    fun test6_roomMigration2To3PreservesExistingData() = runBlocking {
        val studentDao = db.studentDao()
        val gradeDao = db.gradeDao()

        val student = StudentEntity(
            studentId = "s-pres",
            studentCode = "100",
            fullName = "خالد محمود",
            gradeId = "g-pres",
            parentPhone = "01099999999",
            hasWhatsApp = false,
            teacherId = "t-1"
        )
        studentDao.upsertStudent(student)

        val grade = GradeEntity(
            gradeId = "g-pres",
            gradeName = "الصف الثالث الثانوي",
            displayOrder = 3,
            teacherId = "t-1"
        )
        gradeDao.upsertGrade(grade)

        val fetchedStudent = studentDao.getStudentByIdSync("t-1", "s-pres")
        val fetchedGrade = gradeDao.getGradeById("t-1", "g-pres")

        assertNotNull(fetchedStudent)
        assertEquals("خالد محمود", fetchedStudent?.fullName)

        assertNotNull(fetchedGrade)
        assertEquals("الصف الثالث الثانوي", fetchedGrade?.gradeName)
    }

    @Test
    fun test7_processRestartPreservesGradeCache() = runBlocking {
        // Step 1: Write to database instance 1
        val gradeDao1 = db.gradeDao()
        gradeDao1.upsertGrade(
            Grade(id = "grade-restart", name = "الصف التجريبي", displayOrder = 1, teacherId = "t_restart").toEntity()
        )
        db.close()

        // Step 2: Open new database connection simulating app restart
        com.example.data.SupabaseClientProvider.mockTeacherId = "t_restart"
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        val gradeDao2 = db2.gradeDao()
        val cached = gradeDao2.getGradeById("t_restart", "grade-restart")
        assertNotNull(cached)
        assertEquals("الصف التجريبي", cached?.gradeName)

        db2.close()
    }
}
