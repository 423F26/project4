package com.example.liftingapp.data

/*
 * Football section of the Workouts tab (see ui/SportSections.kt).
 *
 * Lifting days follow the BFS (Bigger Faster Stronger) off-season and in-season programs. Main lifts
 * rotate through a 4-week set-rep cycle, so their sets and reps depend on the week picked in the
 * app. Speed days come from the NSCA Coach article "The Need for Speed: Improving Sprinting
 * Performance in Football Players" (Ken Clark).
 *
 * Sources:
 *  - biggerfasterstronger.com/blogs/news/the-bfs-five-day-workout
 *  - biggerfasterstronger.com/blogs/news/football-in-season-training
 *  - nsca.com, NSCA Coach 5.4, "The Need for Speed" (K. Clark)
 *
 * Auxiliary lifts use 3 x 8-10 because BFS doesn't fix their sets and reps; a coach can change them.
 * Plain hyphens and "x" only, so a texted workout stays in the cheapest SMS encoding.
 */

/** One week of a set-rep cycle, e.g. "5 x 5" or "5-4-3-2-1" (sets = 1 shows just the reps). */
data class SetRep(val sets: Int, val reps: String) {
    val label: String get() = if (sets > 1) "$sets x $reps" else reps
}

/** A line in a day's workout. Main lifts take their sets/reps from the week; others are fixed. */
data class SportExercise(
    val name: String,
    val sets: Int = 1,
    val reps: String = "",
    val load: String = "",
    val notes: String = "",
    val isMainLift: Boolean = false,
    /** A different scheme for some weeks (0-based), e.g. BFS cleans and deadlifts do 4-4-2 in week 4. */
    val weekOverride: Map<Int, SetRep> = emptyMap()
)

data class SportDay(val name: String, val notes: String, val exercises: List<SportExercise>)

data class Season(
    val name: String,
    /** Main-lift scheme for each week of the cycle. */
    val cycle: List<SetRep>,
    val about: String,
    val days: List<SportDay>
) {
    /** A day as a regular Workout + exercises for [weekIndex] (0-based), so it can use the Send dialog. */
    fun workoutFor(day: SportDay, weekIndex: Int): WorkoutWithExercises {
        val week = weekIndex.coerceIn(0, cycle.lastIndex)
        val exercises = day.exercises.mapIndexed { position, e ->
            val scheme = if (e.isMainLift) e.weekOverride[week] ?: cycle[week] else null
            WorkoutExercise(
                position = position,
                name = e.name,
                sets = scheme?.sets ?: e.sets,
                reps = scheme?.reps ?: e.reps,
                load = e.load,
                notes = e.notes
            )
        }
        val weekNote = if (day.exercises.any { it.isMainLift }) "Week ${week + 1} of ${cycle.size}. " else ""
        return WorkoutWithExercises(
            workout = Workout(name = "Football $name: ${day.name}", notes = weekNote + day.notes, isPremade = true),
            exercises = exercises
        )
    }
}

data class SportSection(val sport: String, val seasons: List<Season>, val source: String)

// --- Shared pieces ---

private const val WARM_UP = "Start with the dot drill. Finish with BFS flexibility."
private const val SPRINT_REST = "Rest about 1 min for every 10 yd sprinted so every rep is fast."

private fun mainLift(name: String, notes: String = "", week4: SetRep? = null) =
    SportExercise(name, isMainLift = true, notes = notes, weekOverride = week4?.let { mapOf(3 to it) } ?: emptyMap())

private fun aux(name: String, reps: String = "8-10", notes: String = "") =
    SportExercise(name, sets = 3, reps = reps, notes = notes)

private fun drill(name: String, sets: Int, reps: String, load: String = "", notes: String = "") =
    SportExercise(name, sets = sets, reps = reps, load = load, notes = notes)

// --- Football ---

val FOOTBALL = SportSection(
    sport = "Football",
    seasons = listOf(
        Season(
            name = "Off-season",
            cycle = listOf(SetRep(3, "3"), SetRep(5, "5"), SetRep(1, "5-4-3-2-1"), SetRep(1, "10-8-6")),
            about = "Lift Mon/Wed/Fri, speed and plyos Tue/Thu. Main lifts rotate every week, then the cycle repeats.",
            days = listOf(
                SportDay("Monday - Squat + Bench", WARM_UP, listOf(
                    mainLift("Box Squat"),
                    mainLift("Towel Bench"),
                    aux("Lat Pulldown"),
                    aux("Glute-Ham Raise"),
                    aux("Single-Leg Deadlift", "8 each leg")
                )),
                SportDay("Tuesday - Acceleration", "$WARM_UP $SPRINT_REST", listOf(
                    drill("Broad Jumps", 4, "6"),
                    drill("Power Skips", 4, "20 yd"),
                    drill("Sled Pulls", 6, "10-15 yd", "20-40% bodyweight", "Start at 20% and build to 40% over 6 weeks"),
                    drill("Partner Races", 4, "15 yd", notes = "Work up to 6 races"),
                    drill("Box Jumps", 3, "5", notes = "Step down, don't jump down"),
                    drill("Ladder Drills", 1, "5-10 min")
                )),
                SportDay("Wednesday - Clean + Deadlift", WARM_UP, listOf(
                    mainLift("Power Clean", week4 = SetRep(1, "4-4-2")),
                    mainLift("Hex Bar Deadlift", week4 = SetRep(1, "4-4-2")),
                    aux("DB Lunge", "8 each leg"),
                    aux("Dips"),
                    aux("Single-Leg Deadlift", "8 each leg")
                )),
                SportDay("Thursday - Max Velocity", "$WARM_UP $SPRINT_REST", listOf(
                    drill("Pogo Jumps", 4, "6"),
                    drill("Mini-Hurdle Jumps", 4, "6"),
                    drill("Wicket Runs", 3, "9-12 hurdles"),
                    drill("Fly Sprints", 3, "10-20 yd fly", notes = "30 yd build-up. Linemen do fewer"),
                    drill("Ladder Drills", 1, "5-10 min")
                )),
                SportDay("Friday - Squat + Bench", WARM_UP, listOf(
                    mainLift("Parallel Squat"),
                    mainLift("Bench"),
                    aux("Lat Pulldown"),
                    aux("Glute-Ham Raise"),
                    aux("DB Lunge", "8 each leg"),
                    aux("Dips")
                ))
            )
        ),
        Season(
            name = "In-season",
            cycle = listOf(SetRep(3, "3"), SetRep(3, "5"), SetRep(1, "5-3-1"), SetRep(1, "6-8-10")),
            about = "Lift twice a week, about 30 min, mornings if possible. Hardest lift on the non-contact day.",
            days = listOf(
                SportDay("Monday - Hardest Lift", "$WARM_UP Non-contact day, so this is the tough session.", listOf(
                    mainLift("Box Squat", "Keep within about 100 lb of your parallel squat"),
                    mainLift("Bench"),
                    mainLift("Hex Bar Deadlift"),
                    drill("Straight-Leg Deadlift", 2, "10", "Light", "No more than 40% of your parallel squat max"),
                    aux("Lat Pulldown"),
                    aux("Dips")
                )),
                SportDay("Tuesday - Light Speed", WARM_UP, listOf(
                    drill("Sprint Technique", 1, "10 min"),
                    drill("Box Jumps", 3, "3"),
                    drill("Med Ball Throws", 3, "5")
                )),
                SportDay("Wednesday - Second Lift", "$WARM_UP Lower-stress lifts before game day.", listOf(
                    mainLift("Power Clean"),
                    mainLift("Towel Bench", "Easier on the shoulders before game day"),
                    mainLift("Box Squat", "Keep within about 100 lb of your parallel squat"),
                    aux("Neck", "10"),
                    aux("Glute-Ham Raise")
                )),
                SportDay("Thursday - Light Speed", WARM_UP, listOf(
                    drill("Sprint Technique", 1, "10 min"),
                    drill("Ladder Drills", 1, "5 min")
                )),
                SportDay("Friday - Game Day", "No heavy lifting. Save it for the game.", listOf(
                    drill("Dot Drill", 1, "Warm-up"),
                    drill("BFS Flexibility", 1, "Full routine"),
                    drill("Sprint Technique", 1, "Light, 5 min")
                ))
            )
        )
    ),
    source = "Bigger Faster Stronger (BFS) programs; speed days from NSCA Coach (K. Clark)."
)

/** Every sport section in the Workouts tab, in display order. Add new sports here. */
val SPORT_SECTIONS: List<SportSection> = listOf(FOOTBALL)
