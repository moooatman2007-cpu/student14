package com.example.data.repository

import com.example.core.model.LessonPayment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    /**
     * Observes/fetches all payments for all students of the current teacher for a specific month and year in a single query.
     */
    fun getMonthlyPayments(year: Int, month: Int): Flow<List<LessonPayment>>

    /**
     * Direct suspend function to fetch all payments for the month in one single query.
     */
    suspend fun getMonthlyPaymentsList(year: Int, month: Int): List<LessonPayment>

    /**
     * Retrieves the payment record for a single student for a specific month and year.
     */
    suspend fun getPaymentForStudent(studentId: String, year: Int, month: Int): LessonPayment?

    /**
     * Sets the payment status (Paid / Unpaid) for a student for a specific month and year.
     */
    suspend fun setPaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        isPaid: Boolean,
        amount: Double = 0.0
    ): Result<LessonPayment>

    /**
     * Toggles the payment status for a student for a specific month and year.
     */
    suspend fun togglePaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        amount: Double = 0.0
    ): Result<LessonPayment>
}
