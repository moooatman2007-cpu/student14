package com.example.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.DatabaseProvider
import java.util.concurrent.TimeUnit

object OutboxSyncScheduler {
    fun scheduleSync() {
        try {
            val context = DatabaseProvider.context
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "outbox_sync",
                ExistingWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
