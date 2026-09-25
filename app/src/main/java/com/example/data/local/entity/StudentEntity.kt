package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["teacher_id"]),
        Index(value = ["teacher_id", "grade_id"])
    ]
)
data class StudentEntity(
    @PrimaryKey
    @ColumnInfo(name = "student_id")
    val studentId: String,

    @ColumnInfo(name = "student_code")
    val studentCode: String = "",

    @ColumnInfo(name = "full_name")
    val fullName: String = "",

    @ColumnInfo(name = "grade_id")
    val gradeId: String = "",

    @ColumnInfo(name = "parent_phone")
    val parentPhone: String = "",

    @ColumnInfo(name = "has_whatsapp")
    val hasWhatsApp: Boolean = true,

    @ColumnInfo(name = "alternative_phone")
    val alternativePhone: String? = null,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String? = null,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,

    @ColumnInfo(name = "created_at_raw")
    val createdAtRaw: String? = null,

    @ColumnInfo(name = "updated_at_raw")
    val updatedAtRaw: String? = null
)
