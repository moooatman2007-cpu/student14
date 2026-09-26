package com.example

import com.example.core.model.Teacher
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockSettingsRepository
import com.example.ui.more.MoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoreViewModelWahaPollingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: MockSettingsRepository
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var teacherRepository: FakeTeacherRepository

    private val requestCount = AtomicInteger(0)
    private val activeConcurrentRequests = AtomicInteger(0)
    private val maxConcurrentRequests = AtomicInteger(0)
    private val bodiesClosedCount = AtomicInteger(0)

    private fun createTestOkHttpClient(
        responseDelayMs: Long = 0,
        responseJson: String = """{"success":true,"status":"SCAN_QR_CODE","qr":{"data":"dummy_qr_base64"}}"""
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            requestCount.incrementAndGet()
            val concurrent = activeConcurrentRequests.incrementAndGet()
            synchronized(maxConcurrentRequests) {
                if (concurrent > maxConcurrentRequests.get()) {
                    maxConcurrentRequests.set(concurrent)
                }
            }

            if (responseDelayMs > 0) {
                Thread.sleep(responseDelayMs)
            }

            activeConcurrentRequests.decrementAndGet()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val isBodyClosed = AtomicBoolean(false)

            val rawBody = responseJson.toResponseBody(mediaType)
            val trackingBody = object : okhttp3.ResponseBody() {
                override fun contentType() = rawBody.contentType()
                override fun contentLength() = rawBody.contentLength()
                override fun source() = rawBody.source()
                override fun close() {
                    if (!isBodyClosed.getAndSet(true)) {
                        bodiesClosedCount.incrementAndGet()
                    }
                    rawBody.close()
                }
            }

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(trackingBody)
                .build()
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = MockSettingsRepository()
        gradeRepository = MockGradeRepository()
        teacherRepository = FakeTeacherRepository()

        teacherRepository.setTeacher(
            Teacher(
                id = "t_1",
                email = "teacher@test.com",
                fullName = "أستاذ تجريبي",
                phoneNumber = "01012345678",
                subject = "الرياضيات",
                centerName = "المركز التعليمي",
                educationalStage = "PRIMARY",
                avatarUrl = null
            )
        )

        requestCount.set(0)
        activeConcurrentRequests.set(0)
        maxConcurrentRequests.set(0)
        bodiesClosedCount.set(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStartWahaPolling_SingleJobReusesHttpClientAndClosesBodies() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        requestCount.set(0)
        bodiesClosedCount.set(0)

        // Start polling once
        viewModel.startWahaPolling()

        // Advance past 1 interval (2500ms)
        testDispatcher.scheduler.advanceTimeBy(2600)
        assertEquals(1, requestCount.get())
        assertEquals(1, bodiesClosedCount.get())

        // Advance past 2nd interval (5000ms total)
        testDispatcher.scheduler.advanceTimeBy(2500)
        assertEquals(2, requestCount.get())
        assertEquals(2, bodiesClosedCount.get())

        viewModel.stopWahaPolling()
        testDispatcher.scheduler.advanceTimeBy(5000)

        // No more requests after stop
        assertEquals(2, requestCount.get())
    }

    @Test
    fun testStartWahaPolling_MultipleCallsDoNotAccumulateJobs() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        requestCount.set(0)

        // Call start multiple times consecutively
        viewModel.startWahaPolling()
        viewModel.startWahaPolling()
        viewModel.startWahaPolling()

        // Advance 1 cycle (2500ms)
        testDispatcher.scheduler.advanceTimeBy(2600)

        // Must only be 1 request per cycle, not 3!
        assertEquals(1, requestCount.get())

        // Advance another cycle
        testDispatcher.scheduler.advanceTimeBy(2500)
        assertEquals(2, requestCount.get())

        viewModel.stopWahaPolling()
    }

    @Test
    fun testStopWahaPolling_CancelsImmediately() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        requestCount.set(0)

        viewModel.startWahaPolling()
        testDispatcher.scheduler.advanceTimeBy(2600)
        assertEquals(1, requestCount.get())

        // Stop polling
        viewModel.stopWahaPolling()

        // Advance multiple intervals
        testDispatcher.scheduler.advanceTimeBy(20000)

        // Request count must remain 1
        assertEquals(1, requestCount.get())
    }

    @Test
    fun testCloseDialogStopsPolling() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        requestCount.set(0)

        viewModel.startWahaPolling()
        testDispatcher.scheduler.advanceTimeBy(2600)
        assertEquals(1, requestCount.get())

        val countBeforeClose = requestCount.get()
        viewModel.closeWahaPairingDialog()

        assertFalse(viewModel.uiState.value.showWahaPairingDialog)

        // Advance time - no new polling requests should occur
        testDispatcher.scheduler.advanceTimeBy(12000)
        assertEquals(countBeforeClose, requestCount.get())
    }

    @Test
    fun testConnectedStateStopsPollingAutomatically() = runTest {
        val client = createTestOkHttpClient(
            responseJson = """{"success":true,"status":"CONNECTED","connected_phone":"201012345678"}"""
        )
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        requestCount.set(0)

        viewModel.startWahaPolling()
        testDispatcher.scheduler.advanceTimeBy(2600)

        assertEquals(1, requestCount.get())
        assertEquals("CONNECTED", viewModel.uiState.value.wahaSessionStatus)
        assertEquals("201012345678", viewModel.uiState.value.wahaConnectedPhone)

        // Polling should auto-stop because status reached CONNECTED
        testDispatcher.scheduler.advanceTimeBy(10000)
        assertEquals(1, requestCount.get())
    }

    @Test
    fun testNoOverlappingRequests() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startWahaPolling()
        testDispatcher.scheduler.advanceTimeBy(10500) // 4 cycles

        assertEquals(4, requestCount.get())
        // Max concurrent requests must never exceed 1
        assertEquals(1, maxConcurrentRequests.get())

        viewModel.stopWahaPolling()
    }

    @Test
    fun testPairingCodeAutoCopyTrackingAndSafeClipboardCleanup() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val sampleCode = "ABCD-1234"
        viewModel.recordPairingCodeCopied(sampleCode)

        // Same code in clipboard -> should clear
        assertTrue(viewModel.shouldClearClipboard(sampleCode))

        // After clearing once, second check returns false
        assertFalse(viewModel.shouldClearClipboard(sampleCode))
    }

    @Test
    fun testClipboardNotClearedWhenUserCopiedDifferentText() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.recordPairingCodeCopied("PAIR-9999")

        // User copied a shopping list or phone number
        assertFalse(viewModel.shouldClearClipboard("01099999999"))
        assertFalse(viewModel.shouldClearClipboard("Another text"))
        assertFalse(viewModel.shouldClearClipboard(null))
        assertFalse(viewModel.shouldClearClipboard(""))
    }

    @Test
    fun testImmediateCheckStatusPollsWithoutWaiting() = runTest {
        val client = createTestOkHttpClient()
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        requestCount.set(0)

        // Trigger immediate check (simulating returning from WhatsApp to foreground)
        viewModel.checkStatusImmediately()
        // Advance time just enough to complete the immediate poll without draining all polling intervals
        testDispatcher.scheduler.advanceTimeBy(100)

        // Immediate check executed 1 request right away without waiting 2500ms
        assertEquals(1, requestCount.get())

        viewModel.stopWahaPolling()
    }

    @Test
    fun testPairingCodeTimeoutSetsExpiredStateAndStopsPolling() = runTest {
        val client = createTestOkHttpClient(
            responseJson = """{"success":true,"status":"SCAN_QR_CODE"}"""
        )
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startWahaPolling()

        // Advance past all 60 iterations (60 * 2500ms = 150,000ms)
        testDispatcher.scheduler.advanceTimeBy(160000)

        // Status must indicate expired code
        assertTrue(viewModel.uiState.value.isPairingCodeExpired)
        assertEquals("انتهت صلاحية الكود", viewModel.uiState.value.pairingCodeError)

        val countAtTimeout = requestCount.get()
        testDispatcher.scheduler.advanceTimeBy(10000)
        // Polling has stopped
        assertEquals(countAtTimeout, requestCount.get())
    }

    @Test
    fun testRequestNewPairingCodeClearsOldState() = runTest {
        val client = createTestOkHttpClient(
            responseJson = """{"success":true,"code":"NEW-5678"}"""
        )
        val viewModel = MoreViewModel(
            settingsRepository = settingsRepository,
            gradeRepository = gradeRepository,
            teacherRepository = teacherRepository,
            okHttpClient = client,
            ioDispatcher = testDispatcher,
            runInitialCheck = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPairingPhoneNumberChange("01012345678")
        viewModel.requestNewPairingCode()
        // Advance time to complete the request without draining all 60 polling intervals
        testDispatcher.scheduler.advanceTimeBy(500)

        assertEquals("NEW-5678", viewModel.uiState.value.pairingCode)
        assertFalse(viewModel.uiState.value.isPairingCodeExpired)
        assertNull(viewModel.uiState.value.pairingCodeError)

        // Test clipboard cleanup matches new code
        assertTrue(viewModel.shouldClearClipboard("NEW-5678"))

        viewModel.stopWahaPolling()
    }
}
