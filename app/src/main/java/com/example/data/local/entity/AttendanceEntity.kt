package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [
        Index(value = ["teacher_id", "date"]),
        Index(value = ["teacher_id", "student_id"])
    ]
)
data class AttendanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "attendance_id")
    val attendanceId: String,

    @ColumnInfo(name = "student_id")
    val studentId: String,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String,

    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
