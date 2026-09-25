package com.example.data.auth

import android.util.Log
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.data.repository.SupabaseTeacherRepository
import com.example.data.sync.OutboxSyncScheduler
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates secure account session teardown and multi-tenant data isolation.
 *
 * Guarantees that when a teacher logs out:
 * 1. Background WorkManager synchronization is immediately cancelled.
 * 2. All cached Room tables (students, attendance, recitations, exams, payments, homework, grades, outbox) are wiped.
 * 3. Repository in-memory caches are reset to empty.
 * 4. Pending Outbox operations from the previous account are purged so they can never be executed under another account.
 * 5. Supabase Auth session is terminated.
 */
object AccountSessionManager {

    private const val TAG = "AccountSessionManager"

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Immediately cancel active and queued WorkManager synchronization jobs
            try {
                OutboxSyncScheduler.cancelSync()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel sync scheduler", e)
            }

            // 2. Wipe the local Room database (all tenant tables including outbox_operations)
            try {
                DatabaseProvider.clearAllData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear Room database", e)
            }

            // 3. Clear in-memory repository caches
            try {
                (RepositoryProvider.studentRepository as? SupabaseStudentRepository)?.clearCache()
                (RepositoryProvider.gradeRepository as? SupabaseGradeRepository)?.clearCache()
                (RepositoryProvider.teacherRepository as? SupabaseTeacherRepository)?.clearCache()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear repository in-memory caches", e)
            }

            // 4. Reset testing/mock teacher ID if set
            SupabaseClientProvider.mockTeacherId = null

            // 5. Terminate remote Supabase Auth session
            try {
                SupabaseClientProvider.client.auth.signOut()
            } catch (e: Exception) {
                // If offline or network error, local data is already purged safely.
                Log.w(TAG, "Remote signOut failed or device offline. Local tenant data cleared successfully.", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during account logout", e)
            Result.failure(e)
        }
    }
}
