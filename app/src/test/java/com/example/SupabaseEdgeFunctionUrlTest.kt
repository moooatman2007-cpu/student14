package com.example

import com.example.data.SupabaseClientProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseEdgeFunctionUrlTest {

    private val baseProjectUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co"

    @Test
    fun testWorkerUrlResolvedWhenConfiguredUrlPointsToWahaSession() {
        // Bug reproduction: When configured URL in .env is explicitly the waha-session URL
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session"

        val workerUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "studentmanager-notification-worker"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrl
        )
    }

    @Test
    fun testWahaSessionUrlResolvedWhenConfiguredUrlPointsToWahaSession() {
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session"

        val sessionUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "waha-session"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session",
            sessionUrl
        )
    }

    @Test
    fun testUrlsResolvedWhenConfiguredUrlIsStandardBaseEndpoint() {
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1"

        val workerUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "studentmanager-notification-worker"
        )
        val sessionUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "waha-session"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrl
        )
        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session",
            sessionUrl
        )
    }

    @Test
    fun testUrlsResolvedWithTrailingSlashInConfiguredUrl() {
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/"

        val workerUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "studentmanager-notification-worker"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrl
        )
    }

    @Test
    fun testFallbackToBaseUrlWhenConfiguredUrlIsBlankOrPlaceholder() {
        val workerUrlFromBlank = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = "",
            baseUrl = baseProjectUrl,
            functionName = "studentmanager-notification-worker"
        )
        val workerUrlFromPlaceholder = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = "https://placeholder.supabase.co/functions/v1",
            baseUrl = baseProjectUrl,
            functionName = "studentmanager-notification-worker"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrlFromBlank
        )
        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrlFromPlaceholder
        )
    }

    @Test
    fun testSanitizesLeadingAndTrailingSlashesInFunctionName() {
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session"

        val workerUrl = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = "/studentmanager-notification-worker/"
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/studentmanager-notification-worker",
            workerUrl
        )
    }

    @Test
    fun testReturnsBaseFunctionsUrlWhenFunctionNameIsBlank() {
        val configuredUrl = "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session"

        val baseEndpoint = SupabaseClientProvider.resolveEdgeFunctionUrl(
            configuredUrl = configuredUrl,
            baseUrl = baseProjectUrl,
            functionName = ""
        )

        assertEquals(
            "https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1",
            baseEndpoint
        )
    }

    @Test
    fun testLiveGetEdgeFunctionUrlProvidesExpectedEndpoints() {
        // Tests the actual method called by repositories and viewmodels
        val workerUrl = SupabaseClientProvider.getEdgeFunctionUrl("studentmanager-notification-worker")
        val sessionUrl = SupabaseClientProvider.getEdgeFunctionUrl("waha-session")

        val expectedBase = "${SupabaseClientProvider.baseUrl}/functions/v1"
        assertEquals(
            "$expectedBase/studentmanager-notification-worker",
            workerUrl
        )
        assertEquals(
            "$expectedBase/waha-session",
            sessionUrl
        )
    }
}
