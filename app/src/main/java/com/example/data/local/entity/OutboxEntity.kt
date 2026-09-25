package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_operations")
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID client-generated

    @ColumnInfo(name = "operation_type")
    val operationType: String, // INSERT, UPDATE, DELETE

    @ColumnInfo(name = "entity_type")
    val entityType: String, // STUDENT, ATTENDANCE, RECITATION

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "status")
    val status: String = "PENDING", // PENDING, SYNCING, SYNCED, FAILED

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String? = null
)
