package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey
    @ColumnInfo(name = "grade_id")
    val gradeId: String,
    @ColumnInfo(name = "grade_name")
    val gradeName: String,
    @ColumnInfo(name = "display_order", defaultValue = "1")
    val displayOrder: Int = 1,
    @ColumnInfo(name = "student_count", defaultValue = "0")
    val studentCount: Int = 0,
    @ColumnInfo(name = "teacher_id")
    val teacherId: String? = null
)
