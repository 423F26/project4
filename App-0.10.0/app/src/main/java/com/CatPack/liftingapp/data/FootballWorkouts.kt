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
    /** A different scheme for some weeks of the cycle (0-based), e.g. BFS cleans and deadlifts do 4-4-2 in week 4. */
    val weekOverride: Map<Int, SetRep> = emptyMap(),
    /**
     * Week-by-week versions of this line (0-based), for drills that progress, like heavier sleds.
     * Week n uses progression[n % size], so the block repeats. A version with sets = 0 means
     * "not this week" and is left out of the day.
     */
    val progression: List<SportExercise> = emptyList()
) {
    fun forWeek(week: Int): SportExercise = if (progression.isEmpty()) this else progression[week % progression.size]
}

data class SportDay(val name: String, val notes: String, val exercises: List<SportExercise>)

data class Season(
    val sport: String,
    val name: String,
    /** Main-lift scheme for each week of the cycle. Empty = no weekly rotation (every week is the same). */
    val cycle: List<SetRep>,
    val about: String,
    val days: List<SportDay>,
    /** How many weeks the plan runs. The cycle repeats to fill them (BFS: week 5 = week 1 of cycle 2). */
    val weeks: Int = cycle.size
) {
    val hasWeeks: Boolean get() = cycle.isNotEmpty()

    /** Weeks to pick from in the app (0 for plans without weekly rotation). */
    val totalWeeks: Int get() = if (hasWeeks) maxOf(weeks, cycle.size) else 0

    /** 0-based week -> the main-lift scheme for that week. */
    fun schemeFor(week: Int): SetRep = cycle[week % cycle.size]

    /** "Week 6: 5 x 5 (cycle 2)" for the week dropdown. */
    fun weekLabel(week: Int): String = buildString {
        append("Week ").append(week + 1).append(": ").append(schemeFor(week).label)
        if (totalWeeks > cycle.size) append(" (cycle ").append(week / cycle.size + 1).append(')')
    }

    /** A day as a regular Workout + exercises for [weekIndex] (0-based), so it can use the Send dialog. */
    fun workoutFor(day: SportDay, weekIndex: Int): WorkoutWithExercises {
        val week = if (hasWeeks) weekIndex.coerceIn(0, totalWeeks - 1) else 0
        val slot = if (hasWeeks) week % cycle.size else 0          // position within the 4-week cycle
        val exercises = day.exercises
            .map { it.forWeek(week) }
            .filter { it.sets > 0 || it.isMainLift }                 // sets = 0: not in this week
            .mapIndexed { position, e ->
                val scheme = if (e.isMainLift && hasWeeks) e.weekOverride[slot] ?: cycle[slot] else null
                WorkoutExercise(
                    position = position,
                    name = e.name,
                    sets = scheme?.sets ?: e.sets,
                    reps = scheme?.reps ?: e.reps,
                    load = e.load,
                    notes = e.notes
                )
            }
        val lifting = day.exercises.any { it.isMainLift }
        val cycleNo = week / maxOf(cycle.size, 1) + 1
        val weekNote = when {
            !hasWeeks -> ""
            lifting && cycleNo > 1 -> "Week ${week + 1} of $totalWeeks (cycle $cycleNo). Beat your numbers from last cycle. "
            else -> "Week ${week + 1} of $totalWeeks. "
        }
        return WorkoutWithExercises(
            workout = Workout(name = "$sport $name: ${day.name}", notes = weekNote + day.notes, isPremade = true),
            exercises = exercises
        )
    }
}

data class SportSection(val sport: String, val seasons: List<Season>, val source: String)

// --- Shared pieces ---

private const val WARM_UP = "Start with the dot drill. Finish with BFS flexibility."
private const val SPRINT_REST = "Rest about 1 min for every 10 yd sprinted so every rep is fast."
private const val SPRINT_REST_SHORT = "About 1-2 min rest between races"

internal fun mainLift(name: String, notes: String = "", week4: SetRep? = null) =
    SportExercise(name, isMainLift = true, notes = notes, weekOverride = week4?.let { mapOf(3 to it) } ?: emptyMap())

internal fun aux(name: String, reps: String = "8-10", notes: String = "") =
    SportExercise(name, sets = 3, reps = reps, notes = notes)

internal fun drill(name: String, sets: Int, reps: String, load: String = "", notes: String = "") =
    SportExercise(name, sets = sets, reps = reps, load = load, notes = notes)

/** A drill that changes week to week: one version per week of the block (see SportExercise.progression). */
internal fun progressing(vararg weeks: SportExercise) = weeks.first().copy(progression = weeks.toList())

/** A drill only some weeks use. */
internal fun off(name: String) = SportExercise(name, sets = 0)

/*
 * NSCA speed progression (Clark, Table 2), 6 weeks; weeks 7-12 repeat it. The article gives the start
 * and end values; the weeks in between are spread evenly:
 *  - sled pulls 6 x 10-15 yd, load 20% -> 40% bodyweight
 *  - partner races 4 x 15 yd (week 1) -> 6 x 15 yd (weeks 4-6)
 *  - wicket runs 9 -> 12 mini-hurdles; fly sprints 10 yd fly -> 20 yd fly by week 4
 *  - sprint-float-sprints added in weeks 5-6
 */
private val SLED_PULLS = progressing(
    drill("Sled Pulls", 6, "10 yd", "20% bodyweight", "Light enough to keep sprint form"),
    drill("Sled Pulls", 6, "10 yd", "25% bodyweight"),
    drill("Sled Pulls", 6, "10-15 yd", "30% bodyweight"),
    drill("Sled Pulls", 6, "15 yd", "35% bodyweight"),
    drill("Sled Pulls", 6, "15 yd", "40% bodyweight"),
    drill("Sled Pulls", 6, "15 yd", "40% bodyweight", "Last week of the block; next week starts light again")
)
private val PARTNER_RACES = progressing(
    drill("Partner Races", 4, "15 yd", notes = SPRINT_REST_SHORT),
    drill("Partner Races", 5, "15 yd", notes = SPRINT_REST_SHORT),
    drill("Partner Races", 5, "15 yd", notes = SPRINT_REST_SHORT),
    drill("Partner Races", 6, "15 yd", notes = SPRINT_REST_SHORT),
    drill("Partner Races", 6, "15 yd", notes = SPRINT_REST_SHORT),
    drill("Partner Races", 6, "15 yd", notes = SPRINT_REST_SHORT)
)
private val WICKET_RUNS = progressing(
    drill("Wicket Runs", 3, "9 hurdles"),
    drill("Wicket Runs", 3, "9 hurdles"),
    drill("Wicket Runs", 3, "10 hurdles"),
    drill("Wicket Runs", 3, "11 hurdles"),
    drill("Wicket Runs", 3, "12 hurdles"),
    drill("Wicket Runs", 3, "12 hurdles")
)
private const val FLY_NOTE = "30 yd build-up. Linemen do fewer"
private val FLY_SPRINTS = progressing(
    drill("Fly Sprints", 3, "10 yd fly", notes = FLY_NOTE),
    drill("Fly Sprints", 3, "10 yd fly", notes = FLY_NOTE),
    drill("Fly Sprints", 3, "15 yd fly", notes = FLY_NOTE),
    drill("Fly Sprints", 3, "20 yd fly", notes = FLY_NOTE),
    drill("Fly Sprints", 3, "20 yd fly", notes = FLY_NOTE),
    drill("Fly Sprints", 3, "20 yd fly", notes = FLY_NOTE)
)
private val SPRINT_FLOAT_SPRINT = progressing(
    off("Sprint-Float-Sprint"), off("Sprint-Float-Sprint"), off("Sprint-Float-Sprint"), off("Sprint-Float-Sprint"),
    drill("Sprint-Float-Sprint", 3, "10 yd sprint, 10 yd float, 10 yd sprint", notes = "Stay tall and relaxed in the float"),
    drill("Sprint-Float-Sprint", 3, "10 yd sprint, 10 yd float, 10 yd sprint", notes = "Stay tall and relaxed in the float")
)

// --- Football ---

val FOOTBALL = SportSection(
    sport = "Football",
    seasons = listOf(
        Season(
            sport = "Football",
            name = "Off-season",
            cycle = listOf(SetRep(3, "3"), SetRep(5, "5"), SetRep(1, "5-4-3-2-1"), SetRep(1, "10-8-6")),
            about = "12 weeks: three 4-week BFS cycles. Lift Mon/Wed/Fri, speed and plyos Tue/Thu. Main lifts rotate every week; each new cycle, beat your numbers from the last one. Speed drills build over 6 weeks, then start again.",
            weeks = 12,
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
                    SLED_PULLS,
                    PARTNER_RACES,
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
                    WICKET_RUNS,
                    FLY_SPRINTS,
                    SPRINT_FLOAT_SPRINT,
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
            sport = "Football",
            name = "In-season",
            cycle = listOf(SetRep(3, "3"), SetRep(3, "5"), SetRep(1, "5-3-1"), SetRep(1, "6-8-10")),
            about = "12 weeks: three 4-week BFS cycles, about a season. Lift twice a week, about 30 min, mornings if possible. Hardest lift on the non-contact day.",
            weeks = 12,
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
val SPORT_SECTIONS: List<SportSection> = listOf(FOOTBALL, BASKETBALL)
