package com.example.data.sync

enum class SyncState {
    IDLE,
    OFFLINE,
    SYNCING,
    SYNCED,
    FAILED
}

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val message: String = ""
) {
    val isOffline: Boolean get() = !isOnline || state == SyncState.OFFLINE
    val hasPending: Boolean get() = pendingCount > 0
    val hasFailed: Boolean get() = failedCount > 0
}
