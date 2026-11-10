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
 * One card in the library: a premade workout (kept on the phone, [cloud] null) or one of the
 * account's own workouts ([cloud] set, AccountWorkouts.kt).
 */
data class LibraryWorkout(val item: WorkoutWithExercises, val cloud: TeamWorkout? = null) {
    val isMine: Boolean get() = cloud != null
    /** Stable across reloads, for the list's animations. */
    val key: String get() = cloud?.let { "cloud:${it.id}" } ?: "phone:${item.workout.id}"
}

/**
 * The Workouts tab's library for [filter]: the account's workouts first (most recently saved on
 * top, as [mine] already is), then the premade ones in the order the app ships them.
 */
fun accountLibrary(mine: List<TeamWorkout>, phone: List<WorkoutWithExercises>, filter: WorkoutFilter): List<LibraryWorkout> {
    val own = mine.sortedByDescending { it.updatedAt }.map { LibraryWorkout(it.asItem(), it) }
    val premade = workoutLibrary(phone, WorkoutFilter.PREMADE).map { LibraryWorkout(it) }
    return when (filter) {
        WorkoutFilter.ALL -> own + premade
        WorkoutFilter.PREMADE -> premade
        WorkoutFilter.MINE -> own
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
