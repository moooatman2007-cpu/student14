package com.example.data.sync

import com.example.data.local.dao.OutboxDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

interface SyncManager {
    val syncStatus: StateFlow<SyncStatus>
    fun retrySync()
}

class DefaultSyncManager(
    private val networkMonitor: NetworkMonitor,
    private val outboxDao: OutboxDao?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SyncManager {

    private val pendingCountFlow: Flow<Int> = outboxDao?.observePendingCount() ?: flowOf(0)
    private val failedCountFlow: Flow<Int> = outboxDao?.observeFailedCount() ?: flowOf(0)

    override val syncStatus: StateFlow<SyncStatus> = combine(
        networkMonitor.isOnline,
        pendingCountFlow,
        failedCountFlow
    ) { isOnline, pendingCount, failedCount ->
        val state = when {
            !isOnline -> SyncState.OFFLINE
            failedCount > 0 -> SyncState.FAILED
            pendingCount > 0 -> SyncState.SYNCING
            else -> SyncState.SYNCED
        }

        val message = when (state) {
            SyncState.OFFLINE -> "غير متصل — البيانات محفوظة محليًا"
            SyncState.FAILED -> "توجد $failedCount عمليات تحتاج المراجعة"
            SyncState.SYNCING -> "جارٍ مزامنة $pendingCount عمليات"
            SyncState.SYNCED -> "تمت المزامنة"
            SyncState.IDLE -> ""
        }

        SyncStatus(
            state = state,
            isOnline = isOnline,
            pendingCount = pendingCount,
            failedCount = failedCount,
            message = message
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncStatus(
            state = SyncState.IDLE,
            isOnline = true,
            pendingCount = 0,
            failedCount = 0,
            message = ""
        )
    )

    override fun retrySync() {
        OutboxSyncScheduler.scheduleSync()
    }
}
