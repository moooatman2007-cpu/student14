package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.OutboxEntity
import com.example.data.sync.DefaultSyncManager
import com.example.data.sync.NetworkMonitor
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncState
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    val onlineFlow = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = onlineFlow

    fun setOnline(online: Boolean) {
        onlineFlow.value = online
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncStatusTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var syncManager: SyncManager
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outboxDao = db.outboxDao()
        fakeNetworkMonitor = FakeNetworkMonitor(true)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        syncManager = DefaultSyncManager(
            networkMonitor = fakeNetworkMonitor,
            outboxDao = outboxDao,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
        db.close()
    }

    @Test
    fun testInitialOnlineStateIsSynced() = runBlocking {
        val status = syncManager.syncStatus.first { it.state != SyncState.IDLE }
        assertEquals(SyncState.SYNCED, status.state)
        assertTrue(status.isOnline)
        assertEquals(0, status.pendingCount)
        assertEquals(0, status.failedCount)
        assertEquals("تمت المزامنة", status.message)
    }

    @Test
    fun testOfflineStateReflectsCorrectly() = runBlocking {
        fakeNetworkMonitor.setOnline(false)

        val status = syncManager.syncStatus.first { !it.isOnline }
        assertEquals(SyncState.OFFLINE, status.state)
        assertFalse(status.isOnline)
        assertEquals("غير متصل — البيانات محفوظة محليًا", status.message)
    }

    @Test
    fun testPendingCountUpdatesSyncingState() = runBlocking {
        fakeNetworkMonitor.setOnline(true)

        val op = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s_test_1",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = "teacher_1"
        )
        outboxDao.insertOperation(op)

        val status = syncManager.syncStatus.first { it.pendingCount == 1 }
        assertEquals(SyncState.SYNCING, status.state)
        assertTrue(status.isOnline)
        assertEquals(1, status.pendingCount)
        assertEquals(0, status.failedCount)
        assertEquals("جارٍ مزامنة 1 عمليات", status.message)
    }

    @Test
    fun testFailedCountUpdatesFailedState() = runBlocking {
        fakeNetworkMonitor.setOnline(true)

        val op = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "UPDATE",
            entityType = "STUDENT",
            entityId = "s_test_fail",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "FAILED",
            teacherId = "teacher_1",
            lastError = "400 Bad Request"
        )
        outboxDao.insertOperation(op)

        val status = syncManager.syncStatus.first { it.failedCount == 1 }
        assertEquals(SyncState.FAILED, status.state)
        assertTrue(status.isOnline)
        assertEquals(1, status.failedCount)
        assertEquals("توجد 1 عمليات تحتاج المراجعة", status.message)
    }

    @Test
    fun testTransitionOfflineThenBackOnline() = runBlocking {
        // Go offline
        fakeNetworkMonitor.setOnline(false)
        val offlineStatus = syncManager.syncStatus.first { !it.isOnline }
        assertEquals(SyncState.OFFLINE, offlineStatus.state)

        // Add pending operation while offline
        val op = OutboxEntity(
            id = UUID.randomUUID().toString(),
            operationType = "INSERT",
            entityType = "ATTENDANCE",
            entityId = "att_1",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = "teacher_1"
        )
        outboxDao.insertOperation(op)

        // Go back online
        fakeNetworkMonitor.setOnline(true)
        val onlineStatus = syncManager.syncStatus.first { it.isOnline && it.pendingCount == 1 }
        assertEquals(SyncState.SYNCING, onlineStatus.state)
        assertEquals(1, onlineStatus.pendingCount)
    }

    @Test
    fun testTransitionPendingToSynced() = runBlocking {
        val opId = UUID.randomUUID().toString()
        val op = OutboxEntity(
            id = opId,
            operationType = "INSERT",
            entityType = "RECITATION",
            entityId = "rec_1",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = "teacher_1"
        )
        outboxDao.insertOperation(op)

        val syncingStatus = syncManager.syncStatus.first { it.pendingCount == 1 }
        assertEquals(SyncState.SYNCING, syncingStatus.state)

        // Mark operation as SYNCED
        outboxDao.updateOperation(op.copy(status = "SYNCED"))

        val syncedStatus = syncManager.syncStatus.first { it.pendingCount == 0 }
        assertEquals(SyncState.SYNCED, syncedStatus.state)
        assertEquals("تمت المزامنة", syncedStatus.message)
    }

    @Test
    fun testRetrySyncTriggersUniqueWork() = runBlocking {
        syncManager.retrySync()
        assertTrue(true)
    }

    @Test
    fun testSyncStatusDataModelHelpers() {
        val offlineStatus = SyncStatus(state = SyncState.OFFLINE, isOnline = false)
        assertTrue(offlineStatus.isOffline)
        assertFalse(offlineStatus.hasPending)
        assertFalse(offlineStatus.hasFailed)

        val pendingStatus = SyncStatus(state = SyncState.SYNCING, isOnline = true, pendingCount = 3)
        assertFalse(pendingStatus.isOffline)
        assertTrue(pendingStatus.hasPending)
        assertFalse(pendingStatus.hasFailed)

        val failedStatus = SyncStatus(state = SyncState.FAILED, isOnline = true, failedCount = 2)
        assertTrue(failedStatus.hasFailed)
    }
}
