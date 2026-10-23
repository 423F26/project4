package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutShareTest {

    private val workout = Workout(id = 7, name = "Upper Body Push/Pull", notes = "Warm up 10 min before the first set.")

    private val exercises = listOf(
        WorkoutExercise(workoutId = 7, position = 1, name = "Pull-ups", sets = 4, reps = "AMRAP"),
        WorkoutExercise(workoutId = 7, position = 0, name = "Bench", sets = 5, reps = "5", load = "75%", notes = "Pause 1 sec on the chest")
    )

    @Test
    fun exerciseLineShowsSetsRepsAndLoad() {
        assertEquals("Bench: 5 x 5 @ 75%", WorkoutShare.exerciseLine(exercises[1]))
        assertEquals("Pull-ups: 4 x AMRAP", WorkoutShare.exerciseLine(exercises[0]))
    }

    @Test
    fun oneSetShowsJustTheReps() {
        val mobility = WorkoutExercise(name = "Mobility", sets = 1, reps = "10 min")
        assertEquals("Mobility: 10 min", WorkoutShare.exerciseLine(mobility))
    }

    @Test
    fun displayCanUseATimesSign() {
        assertEquals("5 × 5 @ 75%", WorkoutShare.prescription(exercises[1], times = " × "))
    }

    @Test
    fun messageGreetsTheAthleteByFirstNameAndNumbersExercisesInOrder() {
        val expected = """
            Hi Jordan, here's your workout: Upper Body Push/Pull

            Warm up 10 min before the first set.

            1. Bench: 5 x 5 @ 75%
               Pause 1 sec on the chest
            2. Pull-ups: 4 x AMRAP
        """.trimIndent()
        assertEquals(expected, WorkoutShare.message(workout, exercises, athleteName = "  Jordan Smith "))
    }

    @Test
    fun messageWithoutAnAthleteOpensWithTheWorkoutName() {
        val message = WorkoutShare.message(workout.copy(notes = ""), exercises, athleteName = null)
        assertEquals("Workout: Upper Body Push/Pull\n\n1. Bench: 5 x 5 @ 75%\n   Pause 1 sec on the chest\n2. Pull-ups: 4 x AMRAP", message)
    }

    @Test
    fun blankAthleteNameIsTreatedAsNoAthlete() {
        assertTrue(WorkoutShare.message(workout, exercises, athleteName = "   ").startsWith("Workout: "))
    }

    @Test
    fun subjectNamesTheWorkout() {
        assertEquals("Workout: Upper Body Push/Pull", WorkoutShare.subject(workout))
    }

    @Test
    fun emailValidation() {
        assertTrue(isValidEmail(""))                       // optional
        assertTrue(isValidEmail("coach@montana.edu"))
        assertTrue(isValidEmail("  a.b+c@x.co  "))
        assertFalse(isValidEmail("coach"))
        assertFalse(isValidEmail("coach@montana"))
        assertFalse(isValidEmail("two words@x.com"))
    }

    @Test
    fun phoneValidation() {
        assertTrue(isValidPhone(""))                       // optional
        assertTrue(isValidPhone("406-555-0100"))
        assertTrue(isValidPhone("+1 (406) 555.0100"))
        assertFalse(isValidPhone("555"))                   // too short
        assertFalse(isValidPhone("call me"))
        assertFalse(isValidPhone("1234567890123456"))      // too long
    }

    @Test
    fun dialablePhoneKeepsDigitsAndLeadingPlus() {
        assertEquals("+14065550100", dialablePhone(" +1 (406) 555-0100 "))
        assertEquals("4065550100", dialablePhone("406.555.0100"))
    }

    @Test
    fun blankBecomesNull() {
        assertEquals(null, "   ".orNullIfBlank())
        assertEquals(null, (null as String?).orNullIfBlank())
        assertEquals("x", " x ".orNullIfBlank())
    }
}
