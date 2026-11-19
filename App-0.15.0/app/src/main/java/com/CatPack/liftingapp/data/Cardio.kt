package com.example.liftingapp.data

import java.util.Locale
import kotlin.math.roundToLong

/*
 * Cardio: runs, rides, rows and swims logged as a distance and a time instead of weight × reps.
 * Plain Kotlin, no Android or Firebase calls, so it's unit tested directly (see CardioTest).
 *
 * Every cardio set is stored in one unit, meters and seconds (LiftEntry.distanceMeters and
 * LiftEntry.durationSeconds), so switching between miles and kilometers only changes what's shown.
 */

/** Built-in cardio exercises. Custom exercises can be cardio too (UserProfile.customCardio). */
val CARDIO_EXERCISES = listOf("Run", "Walk", "Bike", "Row", "Swim", "Elliptical", "Stair Climber")

/** How distances and paces are shown. The saved data is always meters and seconds. */
enum class DistanceUnit(val meters: Double, val short: String, val long: String) {
    MILES(1609.344, "mi", "miles"),
    KILOMETERS(1000.0, "km", "kilometers");

    /** "mi" / "km": how the choice is saved on the phone (and on iPhone). */
    val key: String get() = short

    companion object {
        val DEFAULT = MILES
        fun fromKey(key: String?): DistanceUnit = values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Longest single cardio set the log dialog accepts: 24 hours. */
const val MAX_CARDIO_SECONDS = 24L * 60 * 60

/** Longest distance the log dialog accepts: 1000 km (a very long bike ride). */
const val MAX_CARDIO_METERS = 1_000_000.0

object Cardio {

    /** Whether [exercise] is cardio: a built-in cardio exercise or one in [customCardio] (ignoring case and spacing). */
    fun isCardio(exercise: String, customCardio: Collection<String> = emptyList()): Boolean {
        val key = key(exercise)
        return CARDIO_EXERCISES.any { key(it) == key } || customCardio.any { key(it) == key }
    }

    fun toMeters(distance: Double, unit: DistanceUnit): Double = distance * unit.meters

    fun fromMeters(meters: Double, unit: DistanceUnit): Double = meters / unit.meters

    /** Seconds per [unit] (e.g. per mile), or null when there's no distance or no time. */
    fun paceSeconds(meters: Double, seconds: Long, unit: DistanceUnit): Double? {
        if (meters <= 0.0 || seconds <= 0L) return null
        return seconds / fromMeters(meters, unit)
    }

    /** "7:45 /mi", or "—" when there's no pace. Hours appear for very slow paces: "1:02:10 /km". */
    fun formatPace(meters: Double, seconds: Long, unit: DistanceUnit): String {
        val pace = paceSeconds(meters, seconds, unit) ?: return "—"
        return "${formatDuration(pace.roundToLong())} /${unit.short}"
    }

    /** "3.1 mi", "5 km", "0.25 mi": up to two decimals, no trailing zeros. */
    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        val value = fromMeters(meters, unit)
        val text = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        return "$text ${unit.short}"
    }

    /** The distance as typed in the log dialog (no unit), e.g. "3.1". */
    fun distanceInput(meters: Double, unit: DistanceUnit): String =
        String.format(Locale.US, "%.2f", fromMeters(meters, unit)).trimEnd('0').trimEnd('.')

    /** 95 -> "1:35", 3725 -> "1:02:05". */
    fun formatDuration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        else String.format(Locale.US, "%d:%02d", minutes, secs)
    }

    /**
     * Reads a time typed as "hh:mm:ss", "mm:ss" or plain minutes ("25" = 25:00). Seconds and
     * minutes after the first part must be 0–59. Returns null for anything else, or for zero.
     */
    fun parseDuration(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 4 }) return null
        val numbers = parts.map { it.toLong() }
        val total = when (numbers.size) {
            1 -> numbers[0] * 60
            2 -> {
                if (numbers[1] > 59) return null
                numbers[0] * 60 + numbers[1]
            }
            else -> {
                if (numbers[1] > 59 || numbers[2] > 59) return null
                numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            }
        }
        return total.takeIf { it in 1..MAX_CARDIO_SECONDS }
    }

    /** A typed distance in [unit] -> meters, or null if it isn't a positive number in range. */
    fun parseDistance(text: String, unit: DistanceUnit): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value.isNaN() || value <= 0.0) return null
        return toMeters(value, unit).takeIf { it <= MAX_CARDIO_METERS }
    }

    /** "3.1 mi in 24:10 (7:48 /mi)": how one cardio set reads everywhere in the app. */
    fun describe(meters: Double, seconds: Long, unit: DistanceUnit): String =
        "${formatDistance(meters, unit)} in ${formatDuration(seconds)} (${formatPace(meters, seconds, unit)})"

    /** The fastest set (lowest pace) among [sets], ties going to the earlier one; non-cardio sets are skipped. */
    fun fastest(sets: List<LiftEntry>): LiftEntry? =
        sets.filter { it.isCardio && it.distanceMeters > 0.0 && it.durationSeconds > 0L }
            .sortedBy { it.date }
            .minByOrNull { it.durationSeconds / it.distanceMeters }

    private fun key(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()
}
