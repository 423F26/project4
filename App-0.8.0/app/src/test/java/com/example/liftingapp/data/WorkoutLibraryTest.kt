package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLibraryTest {

    private fun workout(id: Long, premade: Boolean, updatedAt: Long = 0) =
        WorkoutWithExercises(Workout(id = id, name = "W$id", isPremade = premade, createdAt = 0, updatedAt = updatedAt), emptyList())

    private val library = listOf(
        workout(3, premade = true),
        workout(1, premade = true),
        workout(10, premade = false, updatedAt = 100),
        workout(11, premade = false, updatedAt = 300),
        workout(12, premade = false, updatedAt = 200)
    )

    private fun List<WorkoutWithExercises>.ids() = map { it.workout.id }

    @Test
    fun allShowsMineNewestFirstThenPremadeInShippedOrder() {
        assertEquals(listOf(11L, 12L, 10L, 1L, 3L), workoutLibrary(library, WorkoutFilter.ALL).ids())
    }

    @Test
    fun premadeFilterShowsOnlyPremade() {
        assertEquals(listOf(1L, 3L), workoutLibrary(library, WorkoutFilter.PREMADE).ids())
    }

    @Test
    fun mineFilterShowsOnlyCoachMade() {
        assertEquals(listOf(11L, 12L, 10L), workoutLibrary(library, WorkoutFilter.MINE).ids())
    }

    @Test
    fun emptyLibraryIsEmptyForEveryFilter() {
        WorkoutFilter.entries.forEach { assertTrue(workoutLibrary(emptyList(), it).isEmpty()) }
    }

    @Test
    fun suggestionsStartWithTheLogTabLiftsAndHaveNoDuplicates() {
        assertEquals(EXERCISES, WORKOUT_EXERCISE_SUGGESTIONS.take(EXERCISES.size))
        assertEquals(WORKOUT_EXERCISE_SUGGESTIONS.size, WORKOUT_EXERCISE_SUGGESTIONS.toSet().size)
    }

    /** The premade list is seeded straight into the database, so check it would pass the editor's own rules. */
    @Test
    fun premadeWorkoutsAreValid() {
        assertTrue(PREMADE_WORKOUTS.isNotEmpty())
        assertEquals(PREMADE_WORKOUTS.size, PREMADE_WORKOUTS.map { it.name }.toSet().size)
        PREMADE_WORKOUTS.forEach { premade ->
            assertTrue(premade.name, premade.name.isNotBlank())
            assertTrue(premade.name, premade.exercises.isNotEmpty())
            premade.exercises.forEach { e ->
                assertTrue("${premade.name}: ${e.name}", e.name.isNotBlank() && e.reps.isNotBlank())
                assertTrue("${premade.name}: ${e.name}", e.sets in 1..MAX_SETS)
            }
        }
    }

    /** "×", curly quotes and en dashes would push a texted workout into the pricier SMS encoding. */
    @Test
    fun premadeMessagesArePlainText() {
        PREMADE_WORKOUTS.forEach { premade ->
            val message = WorkoutShare.message(Workout(name = premade.name, notes = premade.notes), premade.exercises, "Jordan")
            val odd = message.filter { it.code > 0x7E || (it.code < 0x20 && it != '\n') }
            assertEquals("${premade.name} has non-plain characters", "", odd)
        }
    }
}
