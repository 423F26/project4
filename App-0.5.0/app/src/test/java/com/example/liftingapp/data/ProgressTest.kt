package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ProgressTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    private fun set(day: Long, weight: Float, reps: Int) =
        LiftEntry(athleteId = 1, exercise = "Bench", weight = weight, reps = reps, date = day * DAY_MS + 10 * hour)

    @Test
    fun oneBestPerDayOldestFirst() {
        val days = dailyBests(listOf(set(101, 200f, 3), set(100, 225f, 5), set(100, 245f, 1)), utc)
        assertEquals(listOf(100L, 101L), days.map { it.day })
        assertEquals(262.5f, days[0].bestE1rm, 0.01f)   // 225×5 (262.5) beats 245×1 (245)
        assertEquals(245f, days[0].heaviest, 0.01f)     // heaviest weight is tracked separately
    }

    @Test
    fun allRangeCoversAtLeastTwoWeeks() {
        val window = progressWindow(dailyBests(listOf(set(100, 225f, 5)), utc), ChartRange.ALL, today = 100)
        assertEquals(100L - (MIN_SPAN_DAYS - 1), window.startDay)
        assertEquals(100L, window.endDay)
        assertEquals(1, window.days.size)
    }

    @Test
    fun allRangeStartsAtFirstSessionWhenOlder() {
        val days = dailyBests(listOf(set(10, 185f, 5), set(100, 225f, 5)), utc)
        val window = progressWindow(days, ChartRange.ALL, today = 120)
        assertEquals(10L, window.startDay)
        assertEquals(120L, window.endDay)
        assertEquals(2, window.days.size)
    }

    @Test
    fun monthRangeDropsOlderDays() {
        val days = dailyBests(listOf(set(10, 185f, 5), set(90, 225f, 5)), utc)
        val window = progressWindow(days, ChartRange.MONTH, today = 100)
        assertEquals(71L, window.startDay)
        assertEquals(listOf(90L), window.days.map { it.day })
    }

    @Test
    fun localDayFollowsTheTimeZone() {
        val denver = TimeZone.getTimeZone("America/Denver")
        val lateEvening = 5 * DAY_MS + 3 * hour   // 03:00 UTC = 20:00 the previous day in Montana
        assertEquals(5L, localDay(lateEvening, utc))
        assertEquals(4L, localDay(lateEvening, denver))
    }
}
