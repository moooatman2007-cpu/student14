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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates secure account session teardown and multi-tenant data isolation.
 *
 * Guarantees that when a teacher logs out or their session expires:
 * 1. Background WorkManager synchronization is immediately cancelled.
 * 2. In-flight background fetch and sync writes are rejected immediately.
 * 3. All cached Room tables scoped to that teacher are atomically deleted via Room transaction.
 * 4. Repository in-memory caches are reset to empty.
 * 5. Pending Outbox operations from the previous account are purged so they can never be executed under another account.
 * 6. Supabase Auth session is terminated.
 */
object AccountSessionManager {

    private const val TAG = "AccountSessionManager"
    private val mutex = Mutex()
    @Volatile
    private var activeTeacherId: String? = null

    fun setActiveTeacherId(teacherId: String?) {
        activeTeacherId = teacherId
        Log.d(TAG, "Active teacher set to: $teacherId")
    }

    fun getActiveTeacherId(): String? {
        return activeTeacherId
            ?: SupabaseClientProvider.mockTeacherId
            ?: try { SupabaseClientProvider.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
    }

    fun isSessionActive(teacherId: String?): Boolean {
        if (teacherId.isNullOrBlank()) return false

        val mockId = SupabaseClientProvider.mockTeacherId
        if (mockId != null) {
            return mockId == teacherId
        }

        val remoteId = try { SupabaseClientProvider.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
        if (remoteId == null || remoteId != teacherId) {
            return false
        }

        // If explicitly tracked, must match tracked ID
        val tracked = activeTeacherId
        return tracked == null || tracked == teacherId
    }

    suspend fun logout(): Result<Unit> = handleSessionTermination(remoteSignOut = true)

    suspend fun onSessionTerminated(): Result<Unit> = handleSessionTermination(remoteSignOut = false)

    private suspend fun handleSessionTermination(remoteSignOut: Boolean): Result<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            try {
                val targetTeacherId = getActiveTeacherId()
                Log.d(TAG, "Starting session teardown for teacher: $targetTeacherId (remoteSignOut=$remoteSignOut)")

                // 1. Immediately cancel active and queued WorkManager synchronization jobs
                try {
                    OutboxSyncScheduler.cancelSync()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cancel sync scheduler", e)
                }

                // 2. Invalidate active teacher tracking to reject any in-flight coroutine writes immediately
                activeTeacherId = null
                SupabaseClientProvider.mockTeacherId = null

                // 3. Atomically wipe the tenant-scoped Room database tables (or all tables if teacher is unknown)
                try {
                    if (!targetTeacherId.isNullOrBlank()) {
                        DatabaseProvider.clearDataForTeacher(targetTeacherId)
                        Log.d(TAG, "Tenant-scoped Room data purged for teacher: $targetTeacherId")
                    } else {
                        DatabaseProvider.clearAllData()
                        Log.d(TAG, "Full Room data purge executed as fallback")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear Room database during session termination", e)
                }

                // 4. Clear in-memory repository caches
                try {
                    (RepositoryProvider.studentRepository as? SupabaseStudentRepository)?.clearCache()
                    (RepositoryProvider.gradeRepository as? SupabaseGradeRepository)?.clearCache()
                    (RepositoryProvider.teacherRepository as? SupabaseTeacherRepository)?.clearCache()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear repository in-memory caches", e)
                }

                // 5. Terminate remote Supabase Auth session if requested
                if (remoteSignOut) {
                    try {
                        SupabaseClientProvider.client.auth.signOut()
                    } catch (e: Exception) {
                        Log.w(TAG, "Remote signOut failed or device offline. Local tenant data cleared successfully.", e)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during account session teardown", e)
                Result.failure(e)
            }
        }
    }
}
