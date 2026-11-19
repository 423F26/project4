package com.example.liftingapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Workouts for the Workouts tab and the editor. Same role as LiftRepository. */
class WorkoutRepository(private val dao: WorkoutDao) {

    /** Every workout, each with its exercises in order. Room re-emits whenever either table changes. */
    val workouts: Flow<List<WorkoutWithExercises>> = dao.getWorkouts().map { list -> list.map { it.inOrder() } }

    suspend fun workout(id: Long): WorkoutWithExercises? = dao.getWorkout(id)?.inOrder()

    /** Creates or updates [workout] and replaces its exercises. Returns the workout's id. */
    suspend fun save(workout: Workout, exercises: List<WorkoutExercise>): Long = dao.save(workout, exercises)

    suspend fun delete(workout: Workout) = dao.deleteWorkout(workout)

    suspend fun restore(item: WorkoutWithExercises) = dao.restore(item)

    private fun WorkoutWithExercises.inOrder() = copy(exercises = exercises.sortedBy { it.position })
}
