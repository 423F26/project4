package com.example.liftingapp.data

/*
 * Plain Kotlin, no Android or Room calls, so it can be unit tested directly (see WorkoutLibraryTest).
 */

/** Sets per exercise are capped here in the workout editor. */
const val MAX_SETS = 20

/** The segmented filter at the top of the Workouts tab. */
enum class WorkoutFilter { ALL, PREMADE, MINE }

/**
 * What the Workouts tab shows for [filter]: the coach's own workouts first, most recently
 * saved on top, then the premade ones in the order the app ships them (by id).
 */
fun workoutLibrary(workouts: List<WorkoutWithExercises>, filter: WorkoutFilter): List<WorkoutWithExercises> {
    val mine = workouts.filter { !it.workout.isPremade }
        .sortedWith(compareByDescending<WorkoutWithExercises> { it.workout.updatedAt }.thenByDescending { it.workout.id })
    val premade = workouts.filter { it.workout.isPremade }.sortedBy { it.workout.id }
    return when (filter) {
        WorkoutFilter.ALL -> mine + premade
        WorkoutFilter.PREMADE -> premade
        WorkoutFilter.MINE -> mine
    }
}

/**
 * Exercise names suggested in the workout editor. The Log tab's lifts come first so workouts
 * and logged sets use the same names; coaches can still type anything.
 */
val WORKOUT_EXERCISE_SUGGESTIONS: List<String> = (
    EXERCISES + listOf(
        "Front Squat", "Box Squat", "Romanian Deadlift", "Trap Bar Deadlift", "Hang Clean",
        "Push Press", "Overhead Press", "Incline Bench", "Dumbbell Bench", "Dumbbell Row",
        "Barbell Row", "Chin-ups", "Dips", "Face Pulls", "Walking Lunges",
        "Bulgarian Split Squat", "Step-ups", "Glute-Ham Raise", "Nordic Curl", "Hip Thrust",
        "Box Jumps", "Broad Jumps", "Med Ball Slam", "Farmer Carry", "Sled Push", "Plank"
    )
).distinct()
