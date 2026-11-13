package com.example.liftingapp.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * The workout log under the Log tab's chart: workouts an athlete logged from the Workouts tab,
 * each with a date and a checklist. Plain Kotlin, so it's unit tested (see WorkoutLogTest).
 */

enum class LogSort { NEWEST, OLDEST }

enum class LogFilter { ALL, IN_PROGRESS, DONE }

/** A row in the log list: a month heading, or one logged workout. */
sealed interface LogItem {
    /** [month] is 0-based (January = 0), like java.util.Calendar. */
    data class Month(val year: Int, val month: Int, val count: Int) : LogItem
    data class Entry(val session: WorkoutSession) : LogItem
}

/**
 * Sorted, filtered and grouped by month. Within a day, the workout logged last comes first when
 * sorting newest first (and last when oldest first).
 */
fun workoutLog(sessions: List<WorkoutSession>, sort: LogSort, filter: LogFilter): List<LogItem> {
    val kept = sessions.filter {
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.IN_PROGRESS -> !it.isComplete
            LogFilter.DONE -> it.isComplete
        }
    }
    val oldestFirst = kept.sortedWith(compareBy<WorkoutSession> { it.day }.thenBy { it.createdAt })
    val ordered = if (sort == LogSort.NEWEST) oldestFirst.reversed() else oldestFirst

    val items = mutableListOf<LogItem>()
    ordered.groupBy { monthOf(it.day) }.forEach { (month, inMonth) ->   // groupBy keeps the sorted order
        items += LogItem.Month(month.first, month.second, inMonth.size)
        inMonth.forEach { items += LogItem.Entry(it) }
    }
    return items
}

/** (year, 0-based month) of a local day number. */
fun monthOf(day: Long): Pair<Int, Int> {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
    calendar.timeInMillis = day * DAY_MS   // day numbers already have the local offset baked in
    return calendar.get(Calendar.YEAR) to calendar.get(Calendar.MONTH)
}

/** A new log entry for [athleteId] from one of the Workouts tab's workouts, nothing checked off yet. */
fun sessionFrom(item: WorkoutWithExercises, athleteId: String, day: Long, source: String, now: Long = System.currentTimeMillis()) =
    WorkoutSession(
        athleteId = athleteId,
        workoutName = item.workout.name.trim(),
        notes = item.workout.notes.trim(),
        source = source,
        day = day,
        exercises = item.exercises.sortedBy { it.position }.map {
            SessionExercise(name = it.name.trim(), sets = it.sets, reps = it.reps.trim(), load = it.load.trim(), notes = it.notes.trim())
        },
        createdAt = now,
        updatedAt = now
    )

/** Flips one exercise's check mark. */
fun WorkoutSession.toggled(index: Int): WorkoutSession {
    if (index !in exercises.indices) return this
    val updated = exercises.toMutableList()
    updated[index] = updated[index].copy(done = !updated[index].done)
    return copy(exercises = updated)
}

/** Checks off every exercise (or clears them all when [done] is false). */
fun WorkoutSession.allMarked(done: Boolean): WorkoutSession = copy(exercises = exercises.map { it.copy(done = done) })

/** Records a set logged from exercise [index] and checks the exercise off. */
fun WorkoutSession.withLoggedSet(index: Int, weight: Float, reps: Int): WorkoutSession {
    if (index !in exercises.indices) return this
    val updated = exercises.toMutableList()
    val exercise = updated[index]
    val set = formatSet(weight, reps)
    updated[index] = exercise.copy(done = true, result = if (exercise.result.isBlank()) set else "${exercise.result}, $set")
    return copy(exercises = updated)
}

/** "225 × 5", "227.5 × 3". Same number style as the rest of the app. */
fun formatSet(weight: Float, reps: Int): String {
    val w = if (weight % 1f == 0f) weight.toInt().toString() else String.format(Locale.US, "%.1f", weight)
    return "$w × $reps"
}
