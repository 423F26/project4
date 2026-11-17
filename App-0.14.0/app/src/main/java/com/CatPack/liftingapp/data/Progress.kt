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

/** A tappable chart point: one training day, and the set that represents it. */
interface ChartDay {
    val day: Long            // local calendar day number, see localDay()
    val bestSet: LiftEntry
}

/** One training day for one lift: the set with the best e1RM that day. */
data class DayBest(
    override val day: Long,
    override val bestSet: LiftEntry,
    val bestE1rm: Float
) : ChartDay

/** One training day for one cardio exercise: that day's fastest set (by pace), and the day's totals. */
data class CardioDay(
    override val day: Long,
    override val bestSet: LiftEntry,
    val totalMeters: Double,
    val totalSeconds: Long
) : ChartDay {
    /** The fastest set's pace in seconds per [unit]. */
    fun pace(unit: DistanceUnit): Double = Cardio.paceSeconds(bestSet.distanceMeters, bestSet.durationSeconds, unit) ?: 0.0
}

/** Collapses cardio sets into one [CardioDay] per calendar day, oldest first. Lift sets are skipped. */
fun cardioDays(entries: List<LiftEntry>, zone: TimeZone = TimeZone.getDefault()): List<CardioDay> =
    entries
        .filter { it.isCardio && it.distanceMeters > 0.0 && it.durationSeconds > 0L }
        .groupBy { localDay(it.date, zone) }
        .map { (day, sets) ->
            CardioDay(
                day = day,
                bestSet = Cardio.fastest(sets)!!,
                totalMeters = sets.sumOf { it.distanceMeters },
                totalSeconds = sets.sumOf { it.durationSeconds }
            )
        }
        .sortedBy { it.day }

/** The cardio days inside [range] (ending today), plus the range's first and last day. */
data class CardioWindow(val startDay: Long, val endDay: Long, val days: List<CardioDay>) {
    val spanDays: Long get() = endDay - startDay
    /** The fastest day in the window, ties going to the earlier one. */
    val fastest: CardioDay? get() = days.minByOrNull { it.bestSet.durationSeconds / it.bestSet.distanceMeters }
    val totalMeters: Double get() = days.sumOf { it.totalMeters }
}

fun cardioWindow(days: List<CardioDay>, range: ChartRange, today: Long): CardioWindow {
    val end = maxOf(today, days.lastOrNull()?.day ?: today)
    val start = when (val span = range.days) {
        null -> minOf(days.firstOrNull()?.day ?: end, end - (MIN_SPAN_DAYS - 1))
        else -> end - (span - 1)
    }
    return CardioWindow(start, end, days.filter { it.day >= start })
}

/** The days that fall inside a chart range, plus the range's first and last day. */
data class ProgressWindow(val startDay: Long, val endDay: Long, val days: List<DayBest>) {
    val spanDays: Long get() = endDay - startDay
}

/** Day number (days since 1970-01-01) of the calendar day [millis] falls on in [zone]. */
fun localDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    (millis + zone.getOffset(millis)).floorDiv(DAY_MS)

/**
 * A moment on local calendar day [day] to stamp a set with: right now if [day] is today,
 * otherwise noon that day (so the set lands on the right day on the chart in any time zone shift).
 */
fun timeOnDay(day: Long, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
    if (localDay(now, zone) == day) return now
    val noonUtc = day * DAY_MS + DAY_MS / 2
    return noonUtc - zone.getOffset(noonUtc)
}

/** Collapses lift sets into one [DayBest] per calendar day, oldest first. Cardio sets are skipped. */
fun dailyBests(entries: List<LiftEntry>, zone: TimeZone = TimeZone.getDefault()): List<DayBest> =
    entries
        .filter { !it.isCardio && it.reps >= 1 }
        .groupBy { localDay(it.date, zone) }
        .map { (day, sets) ->
            val best = sets.maxBy { Strength.estimatedOneRepMax(it.weight, it.reps) }
            DayBest(
                day = day,
                bestSet = best,
                bestE1rm = Strength.estimatedOneRepMax(best.weight, best.reps)
            )
        }
        .sortedBy { it.day }

/**
 * The days that set a new projected best (estimated max), oldest first: a day is kept ONLY if its
 * best set is stronger than every set before it. Equal or weaker days are left out, so the line
 * only ever steps up.
 */
fun projectedBests(days: List<DayBest>): List<DayBest> {
    var best = Float.NEGATIVE_INFINITY
    return days.sortedBy { it.day }.filter { day ->
        (day.bestE1rm > best).also { if (it) best = day.bestE1rm }
    }
}

/**
 * The projected-best chart for [range]: the new bests set inside the range ([ProjectedWindow.days]),
 * plus the best already held going in ([ProjectedWindow.carried]), so a range with no new best still
 * shows where the athlete stands.
 */
data class ProjectedWindow(val startDay: Long, val endDay: Long, val carried: DayBest?, val days: List<DayBest>) {
    val spanDays: Long get() = endDay - startDay
    /** The current projected best: the last new best, or the one carried in. */
    val best: DayBest? get() = days.lastOrNull() ?: carried
}

fun projectedWindow(daily: List<DayBest>, range: ChartRange, today: Long): ProjectedWindow {
    val bests = projectedBests(daily)
    val window = progressWindow(bests, range, today)
    val carried = bests.lastOrNull { it.day < window.startDay }
    return ProjectedWindow(window.startDay, window.endDay, carried, window.days)
}

/**
 * True if a set of [weight] × [reps] would be a new projected best, i.e. stronger than [currentBest]
 * (the athlete's best for that exercise; null if they've never logged it).
 */
fun isNewProjectedBest(weight: Float, reps: Int, currentBest: BestSet?): Boolean {
    if (reps < 1) return false
    return currentBest == null || Strength.estimatedOneRepMax(weight, reps) > currentBest.e1rm
}

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
