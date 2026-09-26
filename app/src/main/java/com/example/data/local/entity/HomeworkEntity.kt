package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "homework",
    indices = [
        Index(value = ["teacher_id", "student_id"], name = "index_homework_teacher_id_student_id"),
        Index(value = ["teacher_id", "date"], name = "index_homework_teacher_id_date")
    ]
)
data class HomeworkEntity(
    @PrimaryKey
    @ColumnInfo(name = "homework_id")
    val homeworkId: String,
    
    @ColumnInfo(name = "student_id")
    val studentId: String,
    
    @ColumnInfo(name = "teacher_id")
    val teacherId: String,
    
    @ColumnInfo(name = "date")
    val date: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "status")
    val status: String,
    
    @ColumnInfo(name = "note")
    val note: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
