package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recitations",
    indices = [
        Index(value = ["teacher_id", "student_id"]),
        Index(value = ["teacher_id", "date"])
    ]
)
data class RecitationEntity(
    @PrimaryKey
    @ColumnInfo(name = "recitation_id")
    val recitationId: String,

    @ColumnInfo(name = "student_id")
    val studentId: String,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "score")
    val score: Double,

    @ColumnInfo(name = "max_score")
    val maxScore: Double = 10.0,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
