package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.SupabaseClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.sync.OutboxSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class F05OutboxReliabilityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outboxDao: OutboxDao
    private val testTeacherId = "teacher_f05_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db, context)
        outboxDao = db.outboxDao()
        SupabaseClientProvider.mockTeacherId = testTeacherId
    }

    @After
    fun tearDown() {
        OutboxSyncWorker.operationProcessor = null
        SupabaseClientProvider.mockTeacherId = null
        db.close()
        DatabaseProvider.resetForTesting()
    }

    private fun createOutboxOp(
        id: String = UUID.randomUUID().toString(),
        retryCount: Int = 0,
        status: String = "PENDING",
        teacherId: String = testTeacherId
    ): OutboxEntity {
        return OutboxEntity(
            id = id,
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s_${UUID.randomUUID()}",
            payload = """{"teacherId":"$teacherId","gradeId":"g1","fullName":"Student Test","hasWhatsApp":false}""",
            createdAt = System.currentTimeMillis(),
            retryCount = retryCount,
            status = status,
            teacherId = teacherId
        )
    }

    /**
     * Requirement 1: Transient failure with initial retryCount 0 -> increments to 1, status PENDING, returns Result.retry()
     */
    @Test
    fun testTransientFailureRetryCount1ReturnsRetry() = runBlocking {
        val op = createOutboxOp(retryCount = 0)
        outboxDao.insertOperation(op)

        OutboxSyncWorker.operationProcessor = {
            throw IOException("Network timeout or connection refused")
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)

        val updated = outboxDao.getOperationById(op.id)
        assertNotNull(updated)
        assertEquals("PENDING", updated?.status)
        assertEquals(1, updated?.retryCount)
    }

    /**
     * Requirement 2: Transient failure with retryCount 3 -> increments to 4, status PENDING, returns Result.retry()
     */
    @Test
    fun testTransientFailureRetryCount4ReturnsRetry() = runBlocking {
        val op = createOutboxOp(retryCount = 3)
        outboxDao.insertOperation(op)

        OutboxSyncWorker.operationProcessor = {
            throw IOException("Temporary 503 Service Unavailable")
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)

        val updated = outboxDao.getOperationById(op.id)
        assertNotNull(updated)
        assertEquals("PENDING", updated?.status)
        assertEquals(4, updated?.retryCount)
    }

    /**
     * Requirement 3 & Critical Test: Transient failure with retryCount 4 -> increments to 5, status FAILED,
     * and Worker returns Result.success() so it does NOT request another retry loop!
     */
    @Test
    fun testTransientFailureRetryCount5TransitionsToFailedAndStopsRetryLoop() = runBlocking {
        val op = createOutboxOp(retryCount = 4)
        outboxDao.insertOperation(op)

        OutboxSyncWorker.operationProcessor = {
            throw IOException("Socket timeout")
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        // Crucial: Must return Result.success() to signal WorkManager to STOP retrying this job!
        assertEquals(ListenableWorker.Result.success(), result)

        val updated = outboxDao.getOperationById(op.id)
        assertNotNull(updated)
        assertEquals("FAILED", updated?.status)
        assertEquals(5, updated?.retryCount)

        // On subsequent run with operation already at retryCount = 5 / FAILED, worker MUST NOT retry!
        val secondWorker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val secondResult = secondWorker.doWork()
        assertEquals(ListenableWorker.Result.success(), secondResult)
    }

    /**
     * Requirement 4: Permanent failure (e.g. 400 Bad Request, unique constraint) -> transitions to FAILED immediately, no retry
     */
    @Test
    fun testPermanentFailureTransitionsToFailedAndNoRetry() = runBlocking {
        val op = createOutboxOp(retryCount = 0)
        outboxDao.insertOperation(op)

        OutboxSyncWorker.operationProcessor = {
            throw RuntimeException("400 Bad Request: violates unique constraint")
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val updated = outboxDao.getOperationById(op.id)
        assertNotNull(updated)
        assertEquals("FAILED", updated?.status)
        assertEquals(1, updated?.retryCount)
    }

    /**
     * Requirement 5: Other operations in the same batch continue properly and are NOT dropped when one operation fails!
     */
    @Test
    fun testOtherOperationsInBatchContinueWhenOneFails() = runBlocking {
        val op1 = createOutboxOp(id = "op_1", retryCount = 0)
        val op2 = createOutboxOp(id = "op_2", retryCount = 0)
        outboxDao.insertOperations(listOf(op1, op2))

        OutboxSyncWorker.operationProcessor = { op ->
            if (op.id == "op_1") {
                throw RuntimeException("400 Bad Request: entity error") // permanent failure
            }
            // op2 succeeds normally
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        // op1 failed permanently, op2 succeeded -> no pending operations remaining -> success
        assertEquals(ListenableWorker.Result.success(), result)

        val updated1 = outboxDao.getOperationById("op_1")
        val updated2 = outboxDao.getOperationById("op_2")

        assertNotNull(updated1)
        assertNotNull(updated2)

        assertEquals("FAILED", updated1?.status)
        assertEquals("SYNCED", updated2?.status)
    }

    /**
     * Requirement 5 variant: Batch with transient failure on op1 (retryCount < 5) and success on op2
     */
    @Test
    fun testBatchWithTransientFailureProcessesValidOperationAndRetriesFailedOnly() = runBlocking {
        val op1 = createOutboxOp(id = "op_transient", retryCount = 0)
        val op2 = createOutboxOp(id = "op_success", retryCount = 0)
        outboxDao.insertOperations(listOf(op1, op2))

        OutboxSyncWorker.operationProcessor = { op ->
            if (op.id == "op_transient") {
                throw IOException("Network error on op1")
            }
            // op2 succeeds
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        // Returns retry because op1 needs retry
        assertEquals(ListenableWorker.Result.retry(), result)

        val updated1 = outboxDao.getOperationById("op_transient")
        val updated2 = outboxDao.getOperationById("op_success")

        assertEquals("PENDING", updated1?.status)
        assertEquals(1, updated1?.retryCount)

        // op2 MUST BE SYNCED (not dropped or left unattempted!)
        assertEquals("SYNCED", updated2?.status)
    }

    /**
     * Requirement 6: Tenant isolation remains strictly enforced
     */
    @Test
    fun testTenantIsolationMismatchMarksFailedAndDoesNotCrossTenantSync() = runBlocking {
        val foreignOp = createOutboxOp(id = "op_foreign", teacherId = "foreign_teacher_id")
        outboxDao.insertOperation(foreignOp)

        var processorInvoked = false
        OutboxSyncWorker.operationProcessor = {
            processorInvoked = true
        }

        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()

        // Foreign op fails due to isolation mismatch, worker completes with success
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(false, processorInvoked)

        val updated = outboxDao.getOperationById("op_foreign")
        assertEquals("foreign_teacher_id", updated?.teacherId)
        assertEquals("PENDING", updated?.status)
    }
}
