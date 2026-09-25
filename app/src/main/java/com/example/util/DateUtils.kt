package com.example.util

import java.time.YearMonth

/**
 * Data class representing the closed date range [startDate, endDate] for a given calendar month.
 * Dates are ISO-8601 strings in "YYYY-MM-DD" format.
 */
data class MonthDateRange(
    val startDate: String,
    val endDate: String
)

object DateUtils {
    /**
     * Calculates the exact start date and end date of a given month and year.
     * Uses [YearMonth] to safely compute the real last day of the month, correctly
     * handling:
     * - February in leap years (e.g. 2024-02-29)
     * - February in non-leap years (e.g. 2025-02-28, 2026-02-28)
     * - 30-day months (April, June, September, November -> 30)
     * - 31-day months (January, March, May, July, August, October, December -> 31)
     *
     * This avoids invalid dates such as "2026-02-31" which cause PostgreSQL / Supabase
     * queries to fail with 22008 date out of range errors.
     */
    fun getMonthDateRange(year: Int, month: Int): MonthDateRange {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.atEndOfMonth().toString()
        return MonthDateRange(startDate, endDate)
    }
}
