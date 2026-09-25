package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY year DESC, month DESC")
    fun getPaymentsByStudent(teacherId: String, studentId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE teacher_id = :teacherId AND student_id = :studentId ORDER BY year DESC, month DESC")
    suspend fun getPaymentsByStudentSync(teacherId: String, studentId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE teacher_id = :teacherId AND year = :year AND month = :month")
    suspend fun getPaymentsByMonthSync(teacherId: String, year: Int, month: Int): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE teacher_id = :teacherId AND year = :year AND month = :month")
    fun getPaymentsByMonth(teacherId: String, year: Int, month: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE teacher_id = :teacherId AND student_id = :studentId AND year = :year AND month = :month LIMIT 1")
    suspend fun getPaymentForStudentSync(teacherId: String, studentId: String, year: Int, month: Int): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSinglePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE teacher_id = :teacherId AND payment_id = :paymentId")
    suspend fun deleteById(teacherId: String, paymentId: String)

    @Query("DELETE FROM payments WHERE teacher_id = :teacherId AND student_id = :studentId")
    suspend fun deleteByStudentId(teacherId: String, studentId: String)

    @Query("DELETE FROM payments WHERE teacher_id = :teacherId")
    suspend fun deletePaymentsByTeacher(teacherId: String)

    @Query("DELETE FROM payments")
    suspend fun clearAll()
}
