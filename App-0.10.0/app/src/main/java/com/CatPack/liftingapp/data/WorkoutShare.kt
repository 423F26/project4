package com.example.liftingapp.data

/*
 * Builds the text that goes out when a coach sends a workout by text or email.
 * Plain Kotlin, no Android calls, so it can be unit tested directly (see WorkoutShareTest).
 */
object WorkoutShare {

    /** Email subject line. */
    fun subject(workout: Workout): String = "Workout: ${workout.name.trim()}"

    /**
     * "Bench: 4 x 5 @ 75%". One set shows just the reps ("Mobility: 10 min").
     * The message uses a plain "x" ([times]) because "×" makes a carrier text cost more segments;
     * the Workouts tab passes "×" for display.
     */
    fun exerciseLine(exercise: WorkoutExercise, times: String = " x "): String =
        "${exercise.name.trim()}: ${prescription(exercise, times)}"

    /** Just the sets, reps and load: "4 x 5 @ 75%". */
    fun prescription(exercise: WorkoutExercise, times: String = " x "): String = buildString {
        if (exercise.sets > 1) append(exercise.sets).append(times)
        append(exercise.reps.trim())
        val load = exercise.load.trim()
        if (load.isNotEmpty()) append(" @ ").append(load)
    }

    /**
     * The whole message. With [athleteName] it opens with a greeting using their first name;
     * without one it opens with the workout name. Example:
     *
     *     Hi Jordan, here's your workout: Upper Body Push/Pull
     *
     *     Warm up 10 min before the first set.
     *
     *     1. Bench: 5 x 5 @ 75%
     *        Pause 1 sec on the chest
     *     2. Pull-ups: 4 x AMRAP
     */
    fun message(workout: Workout, exercises: List<WorkoutExercise>, athleteName: String? = null): String {
        val firstName = athleteName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        lines += if (firstName != null) "Hi $firstName, here's your workout: ${workout.name.trim()}" else subject(workout)

        val notes = workout.notes.trim()
        if (notes.isNotEmpty()) {
            lines += ""
            lines += notes
        }

        if (exercises.isNotEmpty()) lines += ""
        exercises.sortedBy { it.position }.forEachIndexed { index, exercise ->
            lines += "${index + 1}. ${exerciseLine(exercise)}"
            val cue = exercise.notes.trim()
            if (cue.isNotEmpty()) lines += "   $cue"
        }
        return lines.joinToString("\n")
    }
}

/*
 * Athlete contact details: both are optional, but if one is typed it should look real so the
 * texting or email app gets a usable address.
 */

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PHONE_CHARS = Regex("^[+]?[0-9 ().\\-]+$")

/** Blank, or something shaped like name@example.com. */
fun isValidEmail(raw: String): Boolean = raw.isBlank() || EMAIL.matches(raw.trim())

/** Blank, or 7–15 digits with optional spaces, dashes, dots, brackets and a leading +. */
fun isValidPhone(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return true
    return PHONE_CHARS.matches(trimmed) && trimmed.count { it.isDigit() } in 7..15
}

/** "+1 (406) 555-0100" -> "+14065550100": just the digits (and a leading +) for an smsto: link. */
fun dialablePhone(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (trimmed.startsWith("+")) "+$digits" else digits
}

/** Blank input is stored as null so "no phone" and "empty phone" are the same thing. */
fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
