package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.OutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoomMigrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        context.deleteDatabase("quran_app.db")
        DatabaseProvider.resetForTesting()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
        context.deleteDatabase("quran_app.db")
        DatabaseProvider.resetForTesting()
    }

    private fun createV1Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `students` (
                            `student_id` TEXT NOT NULL,
                            `student_code` TEXT NOT NULL,
                            `full_name` TEXT NOT NULL,
                            `grade_id` TEXT NOT NULL,
                            `parent_phone` TEXT NOT NULL,
                            `has_whatsapp` INTEGER NOT NULL,
                            `alternative_phone` TEXT,
                            `teacher_id` TEXT,
                            `deleted_at` TEXT,
                            `created_at_raw` TEXT,
                            `updated_at_raw` TEXT,
                            PRIMARY KEY(`student_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `attendance` (
                            `attendance_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`attendance_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recitations` (
                            `recitation_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`recitation_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `exams` (
                            `exam_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `exam_name` TEXT NOT NULL,
                            `subject` TEXT,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`exam_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `payments` (
                            `payment_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `year` INTEGER NOT NULL,
                            `month` INTEGER NOT NULL,
                            `amount` REAL NOT NULL,
                            `is_paid` INTEGER NOT NULL,
                            `paid_at` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`payment_id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    private fun createV3Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `students` (
                            `student_id` TEXT NOT NULL,
                            `student_code` TEXT NOT NULL,
                            `full_name` TEXT NOT NULL,
                            `grade_id` TEXT NOT NULL,
                            `parent_phone` TEXT NOT NULL,
                            `has_whatsapp` INTEGER NOT NULL,
                            `alternative_phone` TEXT,
                            `teacher_id` TEXT,
                            `deleted_at` TEXT,
                            `created_at_raw` TEXT,
                            `updated_at_raw` TEXT,
                            PRIMARY KEY(`student_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `attendance` (
                            `attendance_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`attendance_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recitations` (
                            `recitation_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`recitation_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `exams` (
                            `exam_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `exam_name` TEXT NOT NULL,
                            `subject` TEXT,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`exam_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `payments` (
                            `payment_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `year` INTEGER NOT NULL,
                            `month` INTEGER NOT NULL,
                            `amount` REAL NOT NULL,
                            `is_paid` INTEGER NOT NULL,
                            `paid_at` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`payment_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `outbox_operations` (
                            `id` TEXT NOT NULL,
                            `operation_type` TEXT NOT NULL,
                            `entity_type` TEXT NOT NULL,
                            `entity_id` TEXT NOT NULL,
                            `payload` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `retry_count` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `last_error` TEXT,
                            `teacher_id` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grades` (
                            `grade_id` TEXT NOT NULL,
                            `grade_name` TEXT NOT NULL,
                            `display_order` INTEGER NOT NULL DEFAULT 1,
                            `student_count` INTEGER NOT NULL DEFAULT 0,
                            `teacher_id` TEXT,
                            PRIMARY KEY(`grade_id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun testMigrationAllPreservesData() = runBlocking {
        // Step 1: Create V1 database and insert sample data
        val v1Db = createV1Database()
        v1Db.execSQL(
            "INSERT INTO students (student_id, student_code, full_name, grade_id, parent_phone, has_whatsapp, teacher_id) " +
                    "VALUES ('s1', '101', 'أحمد علي', '1', '01012345678', 1, 't1')"
        )
        v1Db.execSQL(
            "INSERT INTO attendance (attendance_id, student_id, date, status, note, created_at, updated_at) " +
                    "VALUES ('att1', 's1', '2026-09-23', 'PRESENT', 'حاضر في الموعد', 1000, 1000)"
        )
        v1Db.execSQL(
            "INSERT INTO recitations (recitation_id, student_id, date, title, content, score, max_score, created_at, updated_at) " +
                    "VALUES ('rec1', 's1', '2026-09-23', 'سورة البقرة', '1-50', 9.5, 10.0, 1000, 1000)"
        )
        v1Db.execSQL(
            "INSERT INTO exams (exam_id, student_id, date, exam_name, subject, score, max_score, created_at, updated_at) " +
                    "VALUES ('ex1', 's1', '2026-09-23', 'اختبار شهري', 'قرآن كريم', 95.0, 100.0, 1000, 1000)"
        )
        v1Db.execSQL(
            "INSERT INTO payments (payment_id, student_id, teacher_id, year, month, amount, is_paid, created_at, updated_at) " +
                    "VALUES ('pay1', 's1', 't1', 2026, 9, 200.0, 1, 1000, 1000)"
        )
        v1Db.close()

        // Step 2: Open with Room V5 using MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, and MIGRATION_4_5
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        // Verify V1 data is 100% preserved
        val student = roomDb.studentDao().getStudentByIdSync("t1", "s1")
        assertNotNull(student)
        assertEquals("أحمد علي", student?.fullName)
        assertEquals("101", student?.studentCode)

        val attendances = roomDb.attendanceDao().getAttendanceByStudentSync("t1", "s1")
        assertEquals(1, attendances.size)
        assertEquals("PRESENT", attendances[0].status)

        val recitations = roomDb.recitationDao().getRecitationsByStudentSync("t1", "s1")
        assertEquals(1, recitations.size)
        assertEquals("سورة البقرة", recitations[0].title)

        val exams = roomDb.examDao().getExamsByStudentSync("t1", "s1")
        assertEquals(1, exams.size)
        assertEquals("اختبار شهري", exams[0].examName)

        val payments = roomDb.paymentDao().getPaymentsByStudentSync("t1", "s1")
        assertEquals(1, payments.size)
        assertEquals(200.0, payments[0].amount, 0.01)

        // Step 3: Verify outbox_operations table exists and can be written/read
        val outboxOp = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s2",
            payload = "{\"name\":\"محمد\"}",
            createdAt = System.currentTimeMillis(),
            retryCount = 0,
            status = "PENDING",
            lastError = null,
            teacherId = "t1"
        )
        roomDb.outboxDao().insertOperation(outboxOp)

        val pending = roomDb.outboxDao().getPendingOperationsForTeacher("t1")
        assertEquals(1, pending.size)
        assertEquals("INSERT", pending[0].operationType)
        assertEquals("s2", pending[0].entityId)

        roomDb.close()
    }

    @Test
    fun testSchemaIntegrityAfterMigration() = runBlocking {
        val v1Db = createV1Database()
        v1Db.close()

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        // Trigger open
        val list = roomDb.outboxDao().getPendingOperationsForTeacher("t1")
        assertEquals(0, list.size)

        roomDb.close()
    }

    @Test
    fun testMigration3To4CorrectlyBackfillsAndIsNotEmpty() = runBlocking {
        // Step 1: Create V3 database and insert data
        val v3Db = createV3Database()
        v3Db.execSQL(
            "INSERT INTO students (student_id, student_code, full_name, grade_id, parent_phone, has_whatsapp, teacher_id) " +
                    "VALUES ('s1', '101', 'أحمد علي', 'g1', '01012345678', 1, 't1')"
        )
        // V3 attendance, recitations, exams tables have no teacher_id column yet.
        v3Db.execSQL(
            "INSERT INTO attendance (attendance_id, student_id, date, status, note, created_at, updated_at) " +
                    "VALUES ('att1', 's1', '2026-09-23', 'PRESENT', 'حاضر', 1000, 1000)"
        )
        v3Db.execSQL(
            "INSERT INTO recitations (recitation_id, student_id, date, title, content, score, max_score, created_at, updated_at) " +
                    "VALUES ('rec1', 's1', '2026-09-23', 'التسميع الأول', 'الآيات 1-10', 10.0, 10.0, 1000, 1000)"
        )
        v3Db.execSQL(
            "INSERT INTO exams (exam_id, student_id, date, exam_name, subject, score, max_score, created_at, updated_at) " +
                    "VALUES ('ex1', 's1', '2026-09-23', 'الامتحان الأول', 'التجويد', 90.0, 100.0, 1000, 1000)"
        )
        v3Db.close()

        // Step 2: Open with Room V5 using MIGRATION_3_4 and MIGRATION_4_5
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        // Verify Student data is 100% preserved
        val student = roomDb.studentDao().getStudentByIdSync("t1", "s1")
        assertNotNull(student)
        assertEquals("أحمد علي", student?.fullName)

        // Verify attendance has been backfilled with teacher_id = t1
        val attendances = roomDb.attendanceDao().getAttendanceByStudentSync("t1", "s1")
        assertEquals(1, attendances.size)
        assertEquals("PRESENT", attendances[0].status)
        assertEquals("t1", attendances[0].teacherId)

        // Verify recitations has been backfilled with teacher_id = t1
        val recitations = roomDb.recitationDao().getRecitationsByStudentSync("t1", "s1")
        assertEquals(1, recitations.size)
        assertEquals("t1", recitations[0].teacherId)

        // Verify exams has been backfilled with teacher_id = t1
        val exams = roomDb.examDao().getExamsByStudentSync("t1", "s1")
        assertEquals(1, exams.size)
        assertEquals("t1", exams[0].teacherId)

        roomDb.close()
    }

    @Test
    fun testDatabaseProviderBuildsWithoutDestructiveMigration() = runBlocking {
        DatabaseProvider.init(context)
        val db = DatabaseProvider.getDatabase(context)
        assertNotNull(db)
        // Verify database operations succeed with test teacher id
        val students = db.studentDao().getAllStudentsSync("t1")
        assertNotNull(students)
        val grades = db.gradeDao().getAllGradesSync("t1")
        assertNotNull(grades)
    }

    private fun createV4Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `students` (
                            `student_id` TEXT NOT NULL,
                            `student_code` TEXT NOT NULL,
                            `full_name` TEXT NOT NULL,
                            `grade_id` TEXT NOT NULL,
                            `parent_phone` TEXT NOT NULL,
                            `has_whatsapp` INTEGER NOT NULL,
                            `alternative_phone` TEXT,
                            `teacher_id` TEXT,
                            `deleted_at` TEXT,
                            `created_at_raw` TEXT,
                            `updated_at_raw` TEXT,
                            PRIMARY KEY(`student_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `attendance` (
                            `attendance_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`attendance_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_teacher_id_date` ON `attendance` (`teacher_id`, `date`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_teacher_id_student_id` ON `attendance` (`teacher_id`, `student_id`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recitations` (
                            `recitation_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`recitation_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitations_teacher_id_student_id` ON `recitations` (`teacher_id`, `student_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitations_teacher_id_date` ON `recitations` (`teacher_id`, `date`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `exams` (
                            `exam_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `exam_name` TEXT NOT NULL,
                            `subject` TEXT,
                            `score` REAL NOT NULL,
                            `max_score` REAL NOT NULL,
                            `note` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`exam_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_teacher_id_student_id` ON `exams` (`teacher_id`, `student_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_teacher_id_date` ON `exams` (`teacher_id`, `date`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `payments` (
                            `payment_id` TEXT NOT NULL,
                            `student_id` TEXT NOT NULL,
                            `teacher_id` TEXT NOT NULL,
                            `year` INTEGER NOT NULL,
                            `month` INTEGER NOT NULL,
                            `amount` REAL NOT NULL,
                            `is_paid` INTEGER NOT NULL,
                            `paid_at` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`payment_id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `outbox_operations` (
                            `id` TEXT NOT NULL,
                            `operation_type` TEXT NOT NULL,
                            `entity_type` TEXT NOT NULL,
                            `entity_id` TEXT NOT NULL,
                            `payload` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `retry_count` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `last_error` TEXT,
                            `teacher_id` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grades` (
                            `grade_id` TEXT NOT NULL,
                            `grade_name` TEXT NOT NULL,
                            `display_order` INTEGER NOT NULL DEFAULT 1,
                            `student_count` INTEGER NOT NULL DEFAULT 0,
                            `teacher_id` TEXT,
                            PRIMARY KEY(`grade_id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun testMigration4To5AddsStudentIndexesAndPreservesData() = runBlocking {
        // Step 1: Create V4 database and insert student records
        val v4Db = createV4Database()
        v4Db.execSQL(
            "INSERT INTO students (student_id, student_code, full_name, grade_id, parent_phone, has_whatsapp, teacher_id) " +
                    "VALUES ('s10', 'CODE10', 'طالب الخامس', 'grade_1', '01099999999', 1, 'teacher_1')"
        )
        v4Db.close()

        // Step 2: Open with Room V5 using MIGRATION_4_5
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        // Step 3: Verify student data is completely preserved
        val student = roomDb.studentDao().getStudentByIdSync("teacher_1", "s10")
        assertNotNull(student)
        assertEquals("طالب الخامس", student?.fullName)
        assertEquals("CODE10", student?.studentCode)

        // Step 4: Verify indices exist in SQLite
        val helper = roomDb.openHelper.readableDatabase
        val cursor = helper.query("PRAGMA index_list('students')")
        val indexNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val nameCol = cursor.getColumnIndex("name")
            if (nameCol >= 0) {
                indexNames.add(cursor.getString(nameCol))
            }
        }
        cursor.close()

        assertTrue(indexNames.contains("index_students_teacher_id"))
        assertTrue(indexNames.contains("index_students_teacher_id_grade_id"))

        roomDb.close()
    }
}
