package com.example.data.repository

import com.example.core.model.LessonPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

class MockPaymentRepository(
    initialPayments: List<LessonPayment> = emptyList()
) : PaymentRepository {

    private val payments = MutableStateFlow(initialPayments)

    override fun getMonthlyPayments(year: Int, month: Int): Flow<List<LessonPayment>> {
        return payments.asStateFlow().map { list ->
            list.filter { it.year == year && it.month == month }
        }
    }

    override suspend fun getMonthlyPaymentsList(year: Int, month: Int): List<LessonPayment> {
        return payments.value.filter { it.year == year && it.month == month }
    }

    override suspend fun getPaymentForStudent(studentId: String, year: Int, month: Int): LessonPayment? {
        return payments.value.find { it.studentId == studentId && it.year == year && it.month == month }
    }

    override suspend fun setPaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        isPaid: Boolean,
        amount: Double
    ): Result<LessonPayment> {
        var updatedPayment: LessonPayment? = null
        payments.update { currentList ->
            val existingIndex = currentList.indexOfFirst {
                it.studentId == studentId && it.year == year && it.month == month
            }
            val mutableList = currentList.toMutableList()
            if (existingIndex >= 0) {
                val old = mutableList[existingIndex]
                val updated = old.copy(
                    isPaid = isPaid,
                    amount = if (amount > 0) amount else old.amount,
                    paidAt = if (isPaid) java.time.OffsetDateTime.now().toString() else null,
                    updatedAt = System.currentTimeMillis()
                )
                mutableList[existingIndex] = updated
                updatedPayment = updated
            } else {
                val newPayment = LessonPayment(
                    paymentId = UUID.randomUUID().toString(),
                    studentId = studentId,
                    teacherId = "mock-teacher-id",
                    year = year,
                    month = month,
                    amount = amount,
                    isPaid = isPaid,
                    paidAt = if (isPaid) java.time.OffsetDateTime.now().toString() else null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                mutableList.add(newPayment)
                updatedPayment = newPayment
            }
            mutableList
        }
        return Result.success(updatedPayment!!)
    }

    override suspend fun togglePaymentStatus(
        studentId: String,
        year: Int,
        month: Int,
        amount: Double
    ): Result<LessonPayment> {
        val existing = getPaymentForStudent(studentId, year, month)
        val newStatus = !(existing?.isPaid ?: false)
        return setPaymentStatus(studentId, year, month, newStatus, amount)
    }
}
