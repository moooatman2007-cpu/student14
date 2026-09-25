package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "payment_id")
    val paymentId: String,

    @ColumnInfo(name = "student_id")
    val studentId: String,

    @ColumnInfo(name = "teacher_id")
    val teacherId: String = "",

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "month")
    val month: Int,

    @ColumnInfo(name = "amount")
    val amount: Double = 0.0,

    @ColumnInfo(name = "is_paid")
    val isPaid: Boolean = false,

    @ColumnInfo(name = "paid_at")
    val paidAt: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
