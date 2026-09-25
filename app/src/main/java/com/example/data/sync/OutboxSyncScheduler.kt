package com.example.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

object OutboxSyncScheduler {
    fun scheduleSync() {
        try {
            val user = try { SupabaseClientProvider.client.auth.currentUserOrNull() } catch (_: Exception) { null }
            val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            if (teacherId.isNullOrBlank()) {
                // Prevent scheduling sync when no teacher is authenticated
                return
            }

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

    fun cancelSync() {
        try {
            val context = DatabaseProvider.context
            WorkManager.getInstance(context).cancelUniqueWork("outbox_sync")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
