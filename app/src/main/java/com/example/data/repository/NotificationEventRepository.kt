package com.example.data.repository

import com.example.core.model.NotificationEvent
import com.example.core.model.NotificationEventType

/**
 * Result abstraction for Notification Event creation operations.
 */
sealed interface NotificationEventCreationResult {
    /**
     * Successfully created and queued as PENDING in Supabase.
     */
    data class Created(val event: NotificationEvent) : NotificationEventCreationResult

    /**
     * The event already exists for this source and teacher (caught by DB Unique Index).
     * Non-fatal; callers should not retry or crash.
     */
    data class AlreadyExists(
        val sourceId: String,
        val eventType: NotificationEventType
    ) : NotificationEventCreationResult

    /**
     * Network, authorization, or unexpected failure.
     */
    data class Failed(
        val error: Throwable,
        val message: String
    ) : NotificationEventCreationResult
}

/**
 * Result abstraction for triggering the Supabase notification worker.
 */
sealed interface NotificationWorkerResult {
    data class Success(val message: String, val detailsJson: String? = null) : NotificationWorkerResult
    data class Skipped(val reason: String) : NotificationWorkerResult
    data class Error(val error: Throwable, val message: String) : NotificationWorkerResult
}

/**
 * Contract for inserting notification events into Supabase event tables and triggering processing.
 */
interface NotificationEventRepository {
    /**
     * Creates a single notification event as PENDING for the authenticated teacher.
     */
    suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult

    /**
     * Dispatches an asynchronous cycle of the notification worker Edge Function.
     * Optionally filters execution to a specific event type (e.g. "ABSENCE").
     * Safe no-op in test/mock environments or when configured with placeholder endpoints.
     */
    suspend fun triggerNotificationWorker(eventType: String? = null): NotificationWorkerResult = NotificationWorkerResult.Skipped("Default implementation")
}
