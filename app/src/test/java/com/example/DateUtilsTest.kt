package com.example

import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {

    @Test
    fun testJanuary_31Days() {
        val range = DateUtils.getMonthDateRange(2026, 1)
        assertEquals("2026-01-01", range.startDate)
        assertEquals("2026-01-31", range.endDate)
        assertValidLocalDate(range.startDate)
        assertValidLocalDate(range.endDate)
    }

    @Test
    fun testFebruary_LeapYear_29Days() {
        // 2024 is a leap year
        val range2024 = DateUtils.getMonthDateRange(2024, 2)
        assertEquals("2024-02-01", range2024.startDate)
        assertEquals("2024-02-29", range2024.endDate)
        assertValidLocalDate(range2024.startDate)
        assertValidLocalDate(range2024.endDate)

        // 2000 was a century leap year (divisible by 400)
        val range2000 = DateUtils.getMonthDateRange(2000, 2)
        assertEquals("2000-02-01", range2000.startDate)
        assertEquals("2000-02-29", range2000.endDate)
        assertValidLocalDate(range2000.startDate)
        assertValidLocalDate(range2000.endDate)
    }

    @Test
    fun testFebruary_NonLeapYear_28Days() {
        // 2026 is a standard non-leap year
        val range2026 = DateUtils.getMonthDateRange(2026, 2)
        assertEquals("2026-02-01", range2026.startDate)
        assertEquals("2026-02-28", range2026.endDate)
        assertValidLocalDate(range2026.startDate)
        assertValidLocalDate(range2026.endDate)

        // 2025 is also a non-leap year
        val range2025 = DateUtils.getMonthDateRange(2025, 2)
        assertEquals("2025-02-01", range2025.startDate)
        assertEquals("2025-02-28", range2025.endDate)
        assertValidLocalDate(range2025.startDate)
        assertValidLocalDate(range2025.endDate)

        // 2100 is a century non-leap year (divisible by 100 but not 400)
        val range2100 = DateUtils.getMonthDateRange(2100, 2)
        assertEquals("2100-02-01", range2100.startDate)
        assertEquals("2100-02-28", range2100.endDate)
        assertValidLocalDate(range2100.startDate)
        assertValidLocalDate(range2100.endDate)
    }

    @Test
    fun testApril_30Days() {
        val range = DateUtils.getMonthDateRange(2026, 4)
        assertEquals("2026-04-01", range.startDate)
        assertEquals("2026-04-30", range.endDate)
        assertValidLocalDate(range.startDate)
        assertValidLocalDate(range.endDate)
    }

    @Test
    fun testJune_30Days() {
        val range = DateUtils.getMonthDateRange(2026, 6)
        assertEquals("2026-06-01", range.startDate)
        assertEquals("2026-06-30", range.endDate)
        assertValidLocalDate(range.startDate)
        assertValidLocalDate(range.endDate)
    }

    @Test
    fun testSeptember_30Days() {
        val range = DateUtils.getMonthDateRange(2026, 9)
        assertEquals("2026-09-01", range.startDate)
        assertEquals("2026-09-30", range.endDate)
        assertValidLocalDate(range.startDate)
        assertValidLocalDate(range.endDate)
    }

    @Test
    fun testNovember_30Days() {
        val range = DateUtils.getMonthDateRange(2026, 11)
        assertEquals("2026-11-01", range.startDate)
        assertEquals("2026-11-30", range.endDate)
        assertValidLocalDate(range.startDate)
        assertValidLocalDate(range.endDate)
    }

    @Test
    fun testOther31DayMonths() {
        val months31 = listOf(1, 3, 5, 7, 8, 10, 12)
        for (m in months31) {
            val range = DateUtils.getMonthDateRange(2026, m)
            val monthStr = m.toString().padStart(2, '0')
            assertEquals("2026-$monthStr-01", range.startDate)
            assertEquals("2026-$monthStr-31", range.endDate)
            assertValidLocalDate(range.startDate)
            assertValidLocalDate(range.endDate)
        }
    }

    private fun assertValidLocalDate(dateStr: String) {
        val parsed = LocalDate.parse(dateStr)
        assertNotNull(parsed)
    }
}
