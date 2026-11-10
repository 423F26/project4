package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {

    @Test
    fun mainLiftsComeFirstInTheirUsualOrder() {
        assertEquals(EXERCISES, ExerciseCatalog.build(emptyList()).take(EXERCISES.size))
    }

    @Test
    fun includesEveryExerciseFromTheWorkoutsTab() {
        val catalog = ExerciseCatalog.build(emptyList())
        val fromSports = SPORT_SECTIONS.flatMap { s -> s.seasons.flatMap { season -> season.days.flatMap { d -> d.exercises.map { it.name } } } }
        val fromPremade = PREMADE_WORKOUTS.flatMap { w -> w.exercises.map { it.name } }
        (WORKOUT_EXERCISE_SUGGESTIONS + fromSports + fromPremade).forEach { name ->
            assertTrue("missing $name", ExerciseCatalog.contains(catalog, name))
        }
    }

    @Test
    fun theRestIsAlphabeticalWithoutCaseDuplicates() {
        val rest = ExerciseCatalog.build(listOf("zercher squat", "Landmine Press")).drop(EXERCISES.size)
        assertEquals(rest.sortedWith(String.CASE_INSENSITIVE_ORDER), rest)
        assertEquals(rest.size, rest.map { it.lowercase() }.toSet().size)
        assertTrue(rest.contains("Landmine Press"))
        // "Push-Ups" (basketball) and "Push-ups" (main lift) are one exercise.
        assertFalse(rest.any { it.equals("Push-ups", ignoreCase = true) })
    }

    @Test
    fun canonicalMatchesExistingSpelling() {
        val catalog = ExerciseCatalog.build(emptyList())
        assertEquals("Push-ups", ExerciseCatalog.canonical("push-UPS ", catalog))
        assertEquals("Landmine Press", ExerciseCatalog.canonical(" Landmine   Press", catalog))
    }

    @Test
    fun newNamesAreTidiedAndLimited() {
        assertEquals("Sled Drag", ExerciseCatalog.cleanNewName("  Sled   Drag "))
        assertNull(ExerciseCatalog.cleanNewName("   "))
        assertNull(ExerciseCatalog.cleanNewName("x".repeat(ExerciseCatalog.MAX_NAME_LENGTH + 1)))
    }
}
