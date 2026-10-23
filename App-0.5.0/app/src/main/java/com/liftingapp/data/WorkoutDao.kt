package com.example.liftingapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Workouts and their exercises. An abstract class (not an interface like LiftDao) so the
 * multi-step writes below can be @Transaction methods with a body: a workout and its
 * exercise list are always saved, deleted and restored together.
 */
@Dao
abstract class WorkoutDao {

    /** Every workout with its exercises. Sorting and filtering happen in WorkoutLibrary.kt. */
    @Transaction
    @Query("SELECT * FROM workouts")
    abstract fun getWorkouts(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun getWorkout(id: Long): WorkoutWithExercises?

    @Insert
    abstract suspend fun insertWorkout(workout: Workout): Long   // returns the new workout's id

    @Update
    abstract suspend fun updateWorkout(workout: Workout)

    /** Its exercises are deleted with it (ON DELETE CASCADE). */
    @Delete
    abstract suspend fun deleteWorkout(workout: Workout)

    @Insert
    abstract suspend fun insertExercises(exercises: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun deleteExercisesFor(workoutId: Long)

    /**
     * Creates the workout (when its id is 0) or updates it, then replaces its exercise list
     * with [exercises] in the order given. Returns the workout's id.
     */
    @Transaction
    open suspend fun save(workout: Workout, exercises: List<WorkoutExercise>): Long {
        val id = if (workout.id == 0L) insertWorkout(workout) else workout.id.also { updateWorkout(workout) }
        deleteExercisesFor(id)
        insertExercises(exercises.mapIndexed { index, exercise ->
            exercise.copy(id = 0, workoutId = id, position = index)
        })
        return id
    }

    /** Puts a deleted workout back exactly as it was (same ids). Used by the Undo snackbar. */
    @Transaction
    open suspend fun restore(item: WorkoutWithExercises) {
        insertWorkout(item.workout)
        insertExercises(item.exercises)
    }
}
