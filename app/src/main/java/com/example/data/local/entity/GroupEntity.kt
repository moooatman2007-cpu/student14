package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["teacher_id"]),
        Index(value = ["teacher_id", "grade_id"]),
        Index(value = ["teacher_id", "grade_id", "name"], unique = true)
    ]
)
data class GroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String,

    @ColumnInfo(name = "grade_id")
    val gradeId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "active")
    val active: Boolean = true,

    @ColumnInfo(name = "start_time")
    val startTime: String,

    @ColumnInfo(name = "end_time")
    val endTime: String,

    @ColumnInfo(name = "capacity")
    val capacity: Int? = null,

    @ColumnInfo(name = "location")
    val location: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
