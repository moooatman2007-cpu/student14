package com.example.data.repository

import com.example.core.model.SystemMonitoringAlert
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SystemMonitoringRepository {
    suspend fun getOpenAlerts(): Result<List<SystemMonitoringAlert>>
}

class SupabaseSystemMonitoringRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SystemMonitoringRepository {

    override suspend fun getOpenAlerts(): Result<List<SystemMonitoringAlert>> = withContext(ioDispatcher) {
        try {
            val alerts = client.postgrest["system_monitoring_alerts"]
                .select {
                    filter {
                        eq("status", "OPEN")
                    }
                    order("created_at", order = Order.DESCENDING)
                }
                .decodeList<SystemMonitoringAlert>()

            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
