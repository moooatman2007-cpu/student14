package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: OutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperations(operations: List<OutboxEntity>)

    @Update
    suspend fun updateOperation(operation: OutboxEntity)

    @Query("SELECT * FROM outbox_operations WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    suspend fun getPendingOperations(): List<OutboxEntity>

    @Query("SELECT * FROM outbox_operations WHERE teacher_id = :teacherId AND status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    suspend fun getPendingOperationsForTeacher(teacherId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox_operations WHERE id = :id")
    suspend fun getOperationById(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox_operations")
    suspend fun getAllOperations(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status = 'PENDING' AND teacher_id = :teacherId")
    fun observePendingCountForTeacher(teacherId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status = 'FAILED' AND teacher_id = :teacherId")
    fun observeFailedCountForTeacher(teacherId: String): Flow<Int>

    @Query("DELETE FROM outbox_operations WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("DELETE FROM outbox_operations WHERE teacher_id = :teacherId")
    suspend fun clearOperationsForTeacher(teacherId: String)

    @Query("DELETE FROM outbox_operations")
    suspend fun clearAllOperations()
}
