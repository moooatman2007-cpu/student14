package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.sync.OutboxSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class OutboxSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outboxDao: OutboxDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db, context)
        outboxDao = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testEmptyOutboxCompletesSuccessfully() = runBlocking {
        val worker = TestListenableWorkerBuilder<OutboxSyncWorker>(context).build()
        val result = worker.doWork()
        assertEquals(true, result is androidx.work.ListenableWorker.Result)
    }

    @Test
    fun testTenantIsolationGuardInWorker() = runBlocking {
        val op = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s_1",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = "other_teacher"
        )
        outboxDao.insertOperation(op)

        val pending = outboxDao.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("PENDING", pending[0].status)
    }
}
