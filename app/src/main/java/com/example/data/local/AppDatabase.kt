package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*

@Database(
    entities = [
        StudentEntity::class,
        AttendanceEntity::class,
        RecitationEntity::class,
        ExamEntity::class,
        PaymentEntity::class,
        OutboxEntity::class,
        GradeEntity::class,
        HomeworkEntity::class,
        GroupEntity::class,
        GroupDayEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun recitationDao(): RecitationDao
    abstract fun examDao(): ExamDao
    abstract fun paymentDao(): PaymentDao
    abstract fun outboxDao(): OutboxDao
    abstract fun gradeDao(): GradeDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun groupDao(): GroupDao
    abstract fun groupDayDao(): GroupDayDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` TEXT NOT NULL,
                        `teacher_id` TEXT NOT NULL,
                        `grade_id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `active` INTEGER NOT NULL DEFAULT 1,
                        `start_time` TEXT NOT NULL,
                        `end_time` TEXT NOT NULL,
                        `capacity` INTEGER,
                        `location` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_teacher_id` ON `groups` (`teacher_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_teacher_id_grade_id` ON `groups` (`teacher_id`, `grade_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_groups_teacher_id_grade_id_name` ON `groups` (`teacher_id`, `grade_id`, `name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_days` (
                        `teacher_id` TEXT NOT NULL,
                        `group_id` TEXT NOT NULL,
                        `day_of_week` TEXT NOT NULL,
                        PRIMARY KEY(`group_id`, `day_of_week`),
                        FOREIGN KEY(`group_id`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_days_group_id` ON `group_days` (`group_id`)")

                db.execSQL("ALTER TABLE `students` ADD COLUMN `group_id` TEXT")
                db.execSQL("ALTER TABLE `attendance` ADD COLUMN `group_id` TEXT")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Detect orphaned records that cannot be linked to a verified teacher_id
                val orphanAttendanceCursor = db.query(
                    "SELECT COUNT(*) FROM attendance WHERE student_id NOT IN (SELECT student_id FROM students WHERE teacher_id IS NOT NULL AND length(trim(teacher_id)) > 0)"
                )
                val orphanAttendance = if (orphanAttendanceCursor.moveToFirst()) orphanAttendanceCursor.getInt(0) else 0
                orphanAttendanceCursor.close()

                val orphanRecitationCursor = db.query(
                    "SELECT COUNT(*) FROM recitations WHERE student_id NOT IN (SELECT student_id FROM students WHERE teacher_id IS NOT NULL AND length(trim(teacher_id)) > 0)"
                )
                val orphanRecitations = if (orphanRecitationCursor.moveToFirst()) orphanRecitationCursor.getInt(0) else 0
                orphanRecitationCursor.close()

                val orphanExamCursor = db.query(
                    "SELECT COUNT(*) FROM exams WHERE student_id NOT IN (SELECT student_id FROM students WHERE teacher_id IS NOT NULL AND length(trim(teacher_id)) > 0)"
                )
                val orphanExams = if (orphanExamCursor.moveToFirst()) orphanExamCursor.getInt(0) else 0
                orphanExamCursor.close()

                if (orphanAttendance > 0 || orphanRecitations > 0 || orphanExams > 0) {
                    throw IllegalStateException(
                        "Migration 3->4 failed: Cannot backfill tenant identity. Found orphaned rows (attendance=$orphanAttendance, recitations=$orphanRecitations, exams=$orphanExams) without a valid teacher_id link."
                    )
                }

                // Step 2: Create temporary tables with final V4 schema (teacher_id TEXT NOT NULL)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_temp` (
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

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recitations_temp` (
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

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exams_temp` (
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

                // Step 3: Copy existing records backfilling teacher_id from students table
                db.execSQL(
                    """
                    INSERT INTO `attendance_temp` (`attendance_id`, `student_id`, `teacher_id`, `date`, `status`, `note`, `created_at`, `updated_at`)
                    SELECT a.`attendance_id`, a.`student_id`, s.`teacher_id`, a.`date`, a.`status`, a.`note`, a.`created_at`, a.`updated_at`
                    FROM `attendance` a
                    INNER JOIN `students` s ON a.`student_id` = s.`student_id`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `recitations_temp` (`recitation_id`, `student_id`, `teacher_id`, `date`, `title`, `content`, `score`, `max_score`, `note`, `created_at`, `updated_at`)
                    SELECT r.`recitation_id`, r.`student_id`, s.`teacher_id`, r.`date`, r.`title`, r.`content`, r.`score`, r.`max_score`, r.`note`, r.`created_at`, r.`updated_at`
                    FROM `recitations` r
                    INNER JOIN `students` s ON r.`student_id` = s.`student_id`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `exams_temp` (`exam_id`, `student_id`, `teacher_id`, `date`, `exam_name`, `subject`, `score`, `max_score`, `note`, `created_at`, `updated_at`)
                    SELECT e.`exam_id`, e.`student_id`, s.`teacher_id`, e.`date`, e.`exam_name`, e.`subject`, e.`score`, e.`max_score`, e.`note`, e.`created_at`, e.`updated_at`
                    FROM `exams` e
                    INNER JOIN `students` s ON e.`student_id` = s.`student_id`
                    """.trimIndent()
                )

                // Step 4: Drop old tables and rename temp tables
                db.execSQL("DROP TABLE `attendance`")
                db.execSQL("ALTER TABLE `attendance_temp` RENAME TO `attendance`")

                db.execSQL("DROP TABLE `recitations`")
                db.execSQL("ALTER TABLE `recitations_temp` RENAME TO `recitations`")

                db.execSQL("DROP TABLE `exams`")
                db.execSQL("ALTER TABLE `exams_temp` RENAME TO `exams`")

                // Step 5: Recreate indices for performance and room consistency
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_teacher_id_date` ON `attendance` (`teacher_id`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_teacher_id_student_id` ON `attendance` (`teacher_id`, `student_id`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitations_teacher_id_student_id` ON `recitations` (`teacher_id`, `student_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitations_teacher_id_date` ON `recitations` (`teacher_id`, `date`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_teacher_id_student_id` ON `exams` (`teacher_id`, `student_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_teacher_id_date` ON `exams` (`teacher_id`, `date`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_teacher_id` ON `students` (`teacher_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_teacher_id_grade_id` ON `students` (`teacher_id`, `grade_id`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `homework` (
                        `homework_id` TEXT NOT NULL,
                        `student_id` TEXT NOT NULL,
                        `teacher_id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `note` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`homework_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_teacher_id_student_id` ON `homework` (`teacher_id`, `student_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_teacher_id_date` ON `homework` (`teacher_id`, `date`)")
            }
        }
    }
}
