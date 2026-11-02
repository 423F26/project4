package com.example.liftingapp.data

/**
 * A workout the app ships with. These are copied into the database once (on a fresh install,
 * or by the 2 -> 3 migration for existing installs; see LiftDatabase.kt). After that they're
 * ordinary rows marked isPremade, so coaches can edit or delete them.
 *
 * To change what new installs get, edit this list. Phones that already have the workouts
 * keep their copies.
 */
data class PremadeWorkout(val name: String, val notes: String, val exercises: List<WorkoutExercise>)

private fun exercise(name: String, sets: Int, reps: String, load: String = "", notes: String = "") =
    WorkoutExercise(name = name, sets = sets, reps = reps, load = load, notes = notes)

/** Plain hyphens and apostrophes only, so a texted workout stays in the cheapest SMS encoding. */
val PREMADE_WORKOUTS: List<PremadeWorkout> = listOf(
    PremadeWorkout(
        name = "Full Body Strength",
        notes = "Warm up 10 min (bike + dynamic stretch). Rest 2-3 min between heavy sets.",
        exercises = listOf(
            exercise("Power Clean", 4, "3", "70%", "Fast elbows, catch high"),
            exercise("Squat", 4, "5", "75%"),
            exercise("Bench", 4, "5", "75%"),
            exercise("Romanian Deadlift", 3, "8", notes = "Soft knees, hinge to mid-shin"),
            exercise("Pull-ups", 3, "AMRAP"),
            exercise("Plank", 3, "45 sec")
        )
    ),
    PremadeWorkout(
        name = "Lower Body Power",
        notes = "Explosive intent on every rep. Full recovery between jump sets.",
        exercises = listOf(
            exercise("Box Jumps", 4, "3", notes = "Step down, don't jump down"),
            exercise("Power Clean", 5, "2", "75%"),
            exercise("Squat", 4, "4", "80%"),
            exercise("Bulgarian Split Squat", 3, "8 each leg"),
            exercise("Glute-Ham Raise", 3, "6"),
            exercise("Farmer Carry", 3, "40 yd")
        )
    ),
    PremadeWorkout(
        name = "Upper Body Push/Pull",
        notes = "Pair each press with the pull after it. Rest 90 sec between pairs.",
        exercises = listOf(
            exercise("Bench", 5, "5", "75%", "Pause 1 sec on the chest"),
            exercise("Pull-ups", 4, "AMRAP"),
            exercise("Incline Bench", 3, "8-10"),
            exercise("Dumbbell Row", 3, "10 each arm"),
            exercise("Overhead Press", 3, "8"),
            exercise("Face Pulls", 3, "15")
        )
    ),
    PremadeWorkout(
        name = "Deadlift Day",
        notes = "Reset every deadlift rep, no bouncing. Belt up for the top sets.",
        exercises = listOf(
            exercise("Deadlift", 5, "3", "80%"),
            exercise("Front Squat", 3, "6", "65%"),
            exercise("Barbell Row", 4, "8"),
            exercise("Nordic Curl", 3, "5", notes = "Slow on the way down"),
            exercise("Hip Thrust", 3, "10"),
            exercise("Med Ball Slam", 3, "10")
        )
    ),
    PremadeWorkout(
        name = "In-Season Maintenance",
        notes = "30-40 min, nothing to failure. Keep the bar moving fast.",
        exercises = listOf(
            exercise("Power Clean", 3, "2", "70%"),
            exercise("Squat", 3, "3", "75%"),
            exercise("Bench", 3, "3", "75%"),
            exercise("Pull-ups", 3, "6"),
            exercise("Walking Lunges", 2, "8 each leg")
        )
    ),
    PremadeWorkout(
        name = "Bodyweight Circuit",
        notes = "Only needs a pull-up bar. Do one set of each, then repeat. Rest 60 sec between rounds.",
        exercises = listOf(
            exercise("Push-ups", 3, "15"),
            exercise("Air Squats", 3, "20"),
            exercise("Walking Lunges", 3, "10 each leg"),
            exercise("Pull-ups", 3, "AMRAP"),
            exercise("Broad Jumps", 3, "5"),
            exercise("Plank", 3, "45 sec")
        )
    )
)
