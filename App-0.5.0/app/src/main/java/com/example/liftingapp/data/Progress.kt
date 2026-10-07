package com.example.liftingapp.data

import java.util.TimeZone

/*
 * Plain Kotlin, no Android calls, so it can be unit tested directly (see ProgressTest).
 * The progress chart plots one point per training day on a real time axis, so gaps
 * between sessions show up as gaps instead of every set being evenly spaced.
 */

const val DAY_MS = 86_400_000L

/** The "All" view always covers at least this many days, so a couple of sessions aren't stretched edge to edge. */
const val MIN_SPAN_DAYS = 14

/** How much history the progress chart shows. [days] = null means everything. */
enum class ChartRange(val days: Int?, val phrase: String) {
    MONTH(30, "month"),
    THREE_MONTHS(91, "3 months"),
    SIX_MONTHS(182, "6 months"),
    YEAR(365, "year"),
    ALL(null, "")
}

/** One training day for one lift: the set with the best e1RM, plus the heaviest weight moved that day. */
data class DayBest(
    val day: Long,           // local calendar day number, see localDay()
    val bestSet: LiftEntry,
    val bestE1rm: Float,
    val heaviest: Float
)

/** The days that fall inside a chart range, plus the range's first and last day. */
data class ProgressWindow(val startDay: Long, val endDay: Long, val days: List<DayBest>) {
    val spanDays: Long get() = endDay - startDay
}

/** Day number (days since 1970-01-01) of the calendar day [millis] falls on in [zone]. */
fun localDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    (millis + zone.getOffset(millis)).floorDiv(DAY_MS)

/** Collapses sets into one [DayBest] per calendar day, oldest first. */
fun dailyBests(entries: List<LiftEntry>, zone: TimeZone = TimeZone.getDefault()): List<DayBest> =
    entries
        .groupBy { localDay(it.date, zone) }
        .map { (day, sets) ->
            val best = sets.maxBy { Strength.estimatedOneRepMax(it.weight, it.reps) }
            DayBest(
                day = day,
                bestSet = best,
                bestE1rm = Strength.estimatedOneRepMax(best.weight, best.reps),
                heaviest = sets.maxOf { it.weight }
            )
        }
        .sortedBy { it.day }

/**
 * Picks the days to show for [range], ending today. "All" starts at the first session
 * (or [MIN_SPAN_DAYS] ago, whichever is earlier).
 */
fun progressWindow(days: List<DayBest>, range: ChartRange, today: Long): ProgressWindow {
    val end = maxOf(today, days.lastOrNull()?.day ?: today)
    val start = when (val span = range.days) {
        null -> minOf(days.firstOrNull()?.day ?: end, end - (MIN_SPAN_DAYS - 1))
        else -> end - (span - 1)
    }
    return ProgressWindow(start, end, days.filter { it.day >= start })
}
