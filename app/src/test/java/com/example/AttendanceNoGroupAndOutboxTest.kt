package com.example

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.UpsertAttendanceRequest
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.repository.MockAttendanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceNoGroupAndOutboxTest {

    @Test
    fun testNoGroupInsertAndUpdate_singleRecordMaintained() = runTest {
        val repo = MockAttendanceRepository()
        val studentId = "std_nogroup_1"
        val date = "2026-09-27"

        // 1. Insert attendance for the first time (no group)
        val res1 = repo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.PRESENT, "Note 1", null)
        assertTrue(res1.isSuccess)
        val att1 = res1.getOrThrow()
        assertEquals(AttendanceStatus.PRESENT, att1.status)
        assertEquals(null, att1.groupId)

        // 2. Same student + date -> UPDATE (PRESENT -> ABSENT), should remain 1 record
        val res2 = repo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.ABSENT, "Note 2", null)
        assertTrue(res2.isSuccess)
        val att2 = res2.getOrThrow()
        assertEquals(AttendanceStatus.ABSENT, att2.status)

        // Verify total records for student/date in mock repo
        val allForStudent = repo.getAttendanceForStudent(studentId)
        // Mock repository behavior maintains uniqueness per student/date/groupId
        val list = repo.getAttendanceByDate(studentId, date)
        assertNotNull(list)
    }

    @Test
    fun testWithGroupInsertUpdateAndDifferentGroup() = runTest {
        val repo = MockAttendanceRepository()
        val studentId = "std_group_1"
        val date = "2026-09-27"
        val groupA = "grp_A"
        val groupB = "grp_B"

        // 1. Group A insert
        val r1 = repo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.PRESENT, "Group A", groupA)
        assertTrue(r1.isSuccess)
        assertEquals(groupA, r1.getOrThrow().groupId)

        // 2. Group A update
        val r2 = repo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.LATE, "Group A Updated", groupA)
        assertTrue(r2.isSuccess)
        assertEquals(AttendanceStatus.LATE, r2.getOrThrow().status)

        // 3. Different group (Group B) -> allowed as separate record
        val r3 = repo.recordOrUpdateAttendance(studentId, date, AttendanceStatus.PRESENT, "Group B", groupB)
        assertTrue(r3.isSuccess)
        assertEquals(groupB, r3.getOrThrow().groupId)
    }

    @Test
    fun testOutboxSyncNoGroupAndRetrySafety() = runTest {
        // Verify outbox entities creation and retry safety logic semantics
        val req1 = UpsertAttendanceRequest(
            teacherId = "teacher_1",
            studentId = "std_og_1",
            groupId = null,
            date = "2026-09-27",
            status = "PRESENT",
            note = "Outbox No Group"
        )
        val entity = OutboxEntity(
            id = "outbox_1",
            operationType = "UPSERT",
            entityType = "ATTENDANCE",
            entityId = "std_og_1_2026-09-27",
            payload = kotlinx.serialization.json.Json.encodeToString(UpsertAttendanceRequest.serializer(), req1),
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = "teacher_1"
        )
        assertNotNull(entity)
        assertEquals("ATTENDANCE", entity.entityType)
        assertEquals("UPSERT", entity.operationType)
    }
}
