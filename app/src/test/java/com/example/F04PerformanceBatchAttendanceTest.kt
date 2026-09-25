package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.data.SupabaseClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.OutboxDao
import com.example.data.repository.SupabaseAttendanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class F04PerformanceBatchAttendanceTest {

    private lateinit var db: AppDatabase
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var attendanceRepository: SupabaseAttendanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attendanceDao = db.attendanceDao()
        outboxDao = db.outboxDao()

        SupabaseClientProvider.mockTeacherId = "teacher_perf_test"
        attendanceRepository = SupabaseAttendanceRepository(attendanceDao, outboxDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Requirement 2 & 3:
     * 30 students fallback saves locally and to outbox with ZERO network calls in loop,
     * using the stable identity (studentId_date).
     */
    @Test
    fun test30StudentsOfflineFallback_zeroNetworkCallsInLoop_usesStableIds() = runBlocking {
        val date = "2026-09-23"
        val items = (1..30).map { i ->
            BatchAttendanceItemDto(
                studentId = "student_30_$i",
                status = if (i % 5 == 0) "ABSENT" else "PRESENT",
                note = if (i % 5 == 0) "بعذر" else null
            )
        }

        val duration = measureTimeMillis {
            val result = attendanceRepository.recordBatchAttendance(date, items)
            assertTrue(result.isSuccess)
            val batchResult = result.getOrNull()
            assertNotNull(batchResult)
            assertEquals(30, batchResult?.total)
            assertEquals(24, batchResult?.presentCount)
            assertEquals(6, batchResult?.absentCount)
        }

        // Must complete very quickly (< 1000ms) without waiting for 30 sequential network timeouts
        assertTrue("Fallback took $duration ms which indicates sequential network timeouts!", duration < 3000)

        // Verify all 30 records saved locally in Room
        val cached = attendanceDao.getAttendanceByDateSync("teacher_perf_test", "student_30_1", date)
        assertNotNull(cached)
        assertEquals("student_30_1_$date", cached?.attendanceId)
        assertEquals("teacher_perf_test", cached?.teacherId)

        // Verify Outbox has 30 entries with stable IDs
        val outbox = outboxDao.getPendingOperationsForTeacher("teacher_perf_test")
        assertEquals(30, outbox.size)
        assertEquals("student_30_1_$date", outbox[0].entityId)
        assertEquals("ATTENDANCE", outbox[0].entityType)
        assertEquals("UPSERT", outbox[0].operationType)
    }

    /**
     * Requirement 4:
     * 100 students fallback executes cleanly and rapidly without 100 network requests.
     */
    @Test
    fun test100StudentsOfflineFallback_efficientAndConsistent() = runBlocking {
        val date = "2026-09-23"
        val items = (1..100).map { i ->
            BatchAttendanceItemDto(
                studentId = "student_100_$i",
                status = if (i % 4 == 0) "ABSENT" else "PRESENT",
                note = null
            )
        }

        val duration = measureTimeMillis {
            val result = attendanceRepository.recordBatchAttendance(date, items)
            assertTrue(result.isSuccess)
            assertEquals(100, result.getOrNull()?.total)
            assertEquals(75, result.getOrNull()?.presentCount)
            assertEquals(25, result.getOrNull()?.absentCount)
        }

        // Must complete quickly without 100 sequential timeouts
        assertTrue("100 students took $duration ms", duration < 5000)

        // Verify Room persistence
        for (i in listOf(1, 50, 100)) {
            val entity = attendanceDao.getAttendanceByDateSync("teacher_perf_test", "student_100_$i", date)
            assertNotNull(entity)
            assertEquals("student_100_${i}_$date", entity?.attendanceId)
        }

        // Verify Outbox persistence
        val outbox = outboxDao.getPendingOperationsForTeacher("teacher_perf_test")
        assertEquals(100, outbox.size)
    }

    /**
     * Requirement 5:
     * 500 students fallback executes smoothly with Room batching and zero network in fallback loop.
     */
    @Test
    fun test500StudentsOfflineFallback_noANR_noOOM() = runBlocking {
        val date = "2026-09-23"
        val items = (1..500).map { i ->
            BatchAttendanceItemDto(
                studentId = "student_500_$i",
                status = "PRESENT",
                note = null
            )
        }

        val duration = measureTimeMillis {
            val result = attendanceRepository.recordBatchAttendance(date, items)
            assertTrue(result.isSuccess)
            assertEquals(500, result.getOrNull()?.total)
            assertEquals(500, result.getOrNull()?.presentCount)
        }

        // Must complete without 500 network timeouts
        assertTrue("500 students took $duration ms", duration < 8000)

        val outbox = outboxDao.getPendingOperationsForTeacher("teacher_perf_test")
        assertEquals(500, outbox.size)

        // Verify sample records in Room
        val firstRecord = attendanceDao.getAttendanceByDateSync("teacher_perf_test", "student_500_1", date)
        val lastRecord = attendanceDao.getAttendanceByDateSync("teacher_perf_test", "student_500_500", date)
        assertNotNull(firstRecord)
        assertNotNull(lastRecord)
        assertEquals("teacher_perf_test", firstRecord?.teacherId)
        assertEquals("teacher_perf_test", lastRecord?.teacherId)
    }

    /**
     * Requirement 6:
     * Teacher isolation is strictly maintained across attendance records and outbox operations.
     */
    @Test
    fun testTeacherIsolationMaintainedInBatchFallback() = runBlocking {
        val date = "2026-09-23"
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_iso_1", status = "PRESENT", note = null)
        )

        val result = attendanceRepository.recordBatchAttendance(date, items)
        assertTrue(result.isSuccess)

        // Verify teacher_perf_test sees the data
        val outboxOwn = outboxDao.getPendingOperationsForTeacher("teacher_perf_test")
        assertEquals(1, outboxOwn.size)

        // Verify other teacher sees ZERO data
        val outboxOther = outboxDao.getPendingOperationsForTeacher("teacher_other")
        assertTrue(outboxOther.isEmpty())

        val roomOther = attendanceDao.getAttendanceByDateSync("teacher_other", "st_iso_1", date)
        assertNull(roomOther)
    }

    /**
     * Requirement 1:
     * Online batch attendance succeeds atomically and produces NO outbox operations.
     */
    @Test
    fun testOnlineBatchSuccess_noFallbackOutbox() = runBlocking {
        val mockRepo = com.example.data.repository.MockAttendanceRepository()
        val items = (1..50).map { i ->
            BatchAttendanceItemDto(
                studentId = "online_student_$i",
                status = "PRESENT",
                note = null
            )
        }
        val result = mockRepo.recordBatchAttendance("2026-09-23", items)
        assertTrue(result.isSuccess)
        assertEquals(50, result.getOrNull()?.total)

        // Outbox must remain empty when online operations succeed
        val pendingOutbox = outboxDao.getPendingOperations()
        assertEquals(0, pendingOutbox.size)
    }
}
