package com.example

import com.example.core.model.EducationalStages
import com.example.core.model.NotificationEvent
import com.example.core.model.NotificationEventType
import com.example.core.model.NotificationRecipient
import com.example.core.model.Teacher
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockHomeworkRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.ui.reports.NotificationBatchSummary
import com.example.data.repository.NotificationEventCreationResult
import com.example.data.repository.NotificationEventRepository
import com.example.data.repository.NotificationWorkerResult
import com.example.ui.reports.ReportsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportsNotificationWorkerRetryTest {

    private val testDispatcher = StandardTestDispatcher()

    private val defaultTeacher = Teacher(
        id = "t_test_1",
        fullName = "المدرس التجريبي",
        phoneNumber = "01000000001",
        subject = "قرآن كريم",
        educationalStage = EducationalStages.PREPARATORY
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestNotificationEventRepository(
        var creationResultBuilder: (NotificationEvent) -> NotificationEventCreationResult,
        var workerResultBuilder: suspend () -> NotificationWorkerResult = { NotificationWorkerResult.Success("OK") }
    ) : NotificationEventRepository {
        val createdEvents = mutableListOf<NotificationEvent>()
        val workerTriggerCount = AtomicInteger(0)

        override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
            createdEvents.add(event)
            return creationResultBuilder(event)
        }

        override suspend fun triggerNotificationWorker(): NotificationWorkerResult {
            workerTriggerCount.incrementAndGet()
            return workerResultBuilder()
        }
    }

    private fun createViewModel(repo: NotificationEventRepository): ReportsViewModel {
        return ReportsViewModel(
            studentRepository = MockStudentRepository(),
            gradeRepository = MockGradeRepository(),
            attendanceRepository = MockAttendanceRepository(),
            recitationRepository = MockRecitationRepository(),
            examRepository = MockExamRepository(),
            homeworkRepository = MockHomeworkRepository(),
            groupRepository = MockGroupRepository(),
            teacherRepository = MockTeacherRepository(defaultTeacher),
            notificationEventRepository = repo
        )
    }

    @Test
    fun testWorkerIsTriggeredWhenEventsAlreadyExistAndNoNewEventsCreated() = runTest {
        // Simulates the exact bug: event is already in DB (AlreadyExists)
        val testRepo = TestNotificationEventRepository(
            creationResultBuilder = { event ->
                NotificationEventCreationResult.AlreadyExists(
                    sourceId = event.sourceId,
                    eventType = event.eventType
                )
            }
        )

        val vm = createViewModel(testRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val recipients = listOf(
            NotificationRecipient(
                studentId = "std_1",
                studentName = "طالب غائب",
                parentPhone = "01012345678",
                sourceId = "att_1",
                notificationType = NotificationEventType.ABSENCE,
                previewDetails = "غائب في تاريخ اليوم"
            )
        )

        var completedSummary: NotificationBatchSummary? = null
        vm.sendBatchNotifications(recipients) { summary ->
            completedSummary = summary
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Verify that createNotificationEvent was invoked
        assertEquals(1, testRepo.createdEvents.size)

        // 2. CRITICAL: Verify that triggerNotificationWorker was invoked despite created == 0!
        assertEquals(1, testRepo.workerTriggerCount.get())

        // 3. Verify batch summary reports existing event without errors
        assertNotNull(completedSummary)
        assertEquals(0, completedSummary?.createdCount)
        assertEquals(1, completedSummary?.alreadyExistsCount)
        assertEquals(0, completedSummary?.failedCount)
        assertEquals(0, completedSummary?.skippedCount)
    }

    @Test
    fun testWorkerIsTriggeredWhenNewEventsAreCreated() = runTest {
        val testRepo = TestNotificationEventRepository(
            creationResultBuilder = { event ->
                NotificationEventCreationResult.Created(event)
            }
        )

        val vm = createViewModel(testRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val recipients = listOf(
            NotificationRecipient(
                studentId = "std_2",
                studentName = "طالب جديد",
                parentPhone = "01098765432",
                sourceId = "att_2",
                notificationType = NotificationEventType.ABSENCE,
                previewDetails = "غائب اليوم"
            )
        )

        var completedSummary: NotificationBatchSummary? = null
        vm.sendBatchNotifications(recipients) { summary ->
            completedSummary = summary
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify creation and worker trigger
        assertEquals(1, testRepo.createdEvents.size)
        assertEquals(1, testRepo.workerTriggerCount.get())

        assertNotNull(completedSummary)
        assertEquals(1, completedSummary?.createdCount)
        assertEquals(0, completedSummary?.alreadyExistsCount)
    }

    @Test
    fun testWorkerNotTriggeredWhenNoEligibleEvents() = runTest {
        val testRepo = TestNotificationEventRepository(
            creationResultBuilder = { NotificationEventCreationResult.Created(it) }
        )

        val vm = createViewModel(testRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        // All recipients have blank phone numbers (skipped)
        val recipients = listOf(
            NotificationRecipient(
                studentId = "std_3",
                studentName = "طالب بدون هاتف",
                parentPhone = "",
                sourceId = "att_3",
                notificationType = NotificationEventType.ABSENCE,
                previewDetails = "غائب بدون هاتف"
            )
        )

        var completedSummary: NotificationBatchSummary? = null
        vm.sendBatchNotifications(recipients) { summary ->
            completedSummary = summary
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, testRepo.createdEvents.size)
        assertEquals(0, testRepo.workerTriggerCount.get())
        assertNotNull(completedSummary)
        assertEquals(1, completedSummary?.skippedCount)
        assertEquals(0, completedSummary?.createdCount)
    }

    @Test
    fun testConcurrencyGuardRejectsConcurrentCallsWhileInFlight() = runTest {
        val workerStarted = CompletableDeferred<Unit>()
        val proceedWorker = CompletableDeferred<Unit>()

        val testRepo = TestNotificationEventRepository(
            creationResultBuilder = { NotificationEventCreationResult.Created(it) },
            workerResultBuilder = {
                workerStarted.complete(Unit)
                proceedWorker.await() // Hold execution in-flight
                NotificationWorkerResult.Success("Done")
            }
        )

        val vm = createViewModel(testRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val recipient = NotificationRecipient(
            studentId = "std_4",
            studentName = "طالب اختبار تزامن",
            parentPhone = "01055556666",
            sourceId = "att_4",
            notificationType = NotificationEventType.ABSENCE,
            previewDetails = "غياب"
        )

        // Trigger first call
        vm.sendBatchNotifications(listOf(recipient))
        testDispatcher.scheduler.runCurrent()

        // Worker started and is blocked waiting for proceedWorker
        assertTrue(workerStarted.isCompleted)

        // Attempt second concurrent call while first is in-flight - MUST BE REJECTED
        vm.sendBatchNotifications(listOf(recipient))
        testDispatcher.scheduler.runCurrent()

        // Unblock worker and let first call finish
        proceedWorker.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify only 1 event created and 1 worker invocation occurred, not 2!
        assertEquals(1, testRepo.createdEvents.size)
        assertEquals(1, testRepo.workerTriggerCount.get())
    }
}
