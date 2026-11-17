package com.example.liftingapp.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A workout a coach can send to an athlete. Premade workouts ship with the app (see
 * PremadeWorkouts.kt) and are seeded into the database; coaches can edit or delete them
 * like any workout they make themselves.
 */
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val notes: String = "",              // warm-up, rest times, focus for the day; may be blank
    val isPremade: Boolean = false,      // true for the workouts the app ships with
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt      // bumped on every save; coach-made workouts sort by this
)

/**
 * One exercise line in a workout, e.g. "Bench: 4 x 5 @ 75%". Deleting a workout deletes its
 * exercises. [reps] and [load] are free text so coaches can write "8-10", "AMRAP", "30 sec",
 * "75%", "RPE 8" or "185 lb".
 */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long = 0,
    val position: Int = 0,               // order within the workout, 0 first
    val name: String,
    val sets: Int,
    val reps: String,
    val load: String = "",               // optional
    val notes: String = ""               // optional coaching cue
)

/**
 * A workout with its exercises. Room fills in [exercises] for any DAO query that returns this
 * type (the query must be marked @Transaction). Room doesn't promise an order, so
 * WorkoutRepository sorts the exercises by position before the UI sees them.
 */
data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExercise>
)
