package com.example.liftingapp.data

/**
 * The lifts offered in the app (Log tab, Leaderboard, workout editor suggestions). Add more here and
 * they show up everywhere.
 *
 * The first six are the originals and stay first so existing logs and the default (Bench) don't change.
 * The rest are every lifting/rep exercise used in the premade workouts and the Football/Basketball plans,
 * A-Z. Same lift under two names is listed once: Bench Press -> Bench, Parallel Squat -> Squat,
 * Hex Bar Deadlift -> Trap Bar Deadlift, Pull-Ups/Push-Ups -> Pull-ups/Push-ups, Air Squats -> Air Squat.
 * Timed, distance and footwork drills (sprints, ladders, jumps, planks, flexibility) aren't here
 * because the Log tab records weight x reps.
 */
val EXERCISES = listOf(
    "Bench", "Squat", "Deadlift", "Power Clean", "Pull-ups", "Push-ups",
    "Air Squat", "Barbell Row", "Box Squat", "Bulgarian Split Squat", "DB Lunge", "Dips",
    "Dumbbell Row", "Face Pulls", "Farmer Carry", "Front Squat", "Glute Bridge", "Glute-Ham Raise",
    "Goblet Squat", "Hip Thrust", "Incline Bench", "Lat Pulldown", "Med Ball Slam", "Med Ball Throws",
    "Neck", "Nordic Curl", "Overhead Press", "Reverse Lunge", "Romanian Deadlift", "Single-Leg Deadlift",
    "Straight-Leg Deadlift", "Towel Bench", "Trap Bar Deadlift", "Walking Lunges", "Walking Overhead Lunge",
    "Walking RDL"
)

/** Reps above this make 1RM estimates unreliable, so the log screen caps input here. */
const val MAX_REPS = 20

object Strength {
    /**
     * Epley formula: e1RM = weight × (1 + reps / 30).
     * A true single (1 rep) returns the weight itself.
     * This puts a 225×5 and a 245×2 on the same scale so they can be compared.
     */
    fun estimatedOneRepMax(weight: Float, reps: Int): Float {
        require(reps >= 1) { "reps must be at least 1" }
        return if (reps == 1) weight else weight * (1 + reps / 30f)
    }
}
