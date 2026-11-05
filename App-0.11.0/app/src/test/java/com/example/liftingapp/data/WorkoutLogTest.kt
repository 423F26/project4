package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLogTest {

    private fun exercises(done: Int, total: Int) = List(total) { i -> SessionExercise("E$i", 3, "5", done = i < done) }

    private fun session(id: String, day: Long, done: Int, total: Int = 3, createdAt: Long = day) =
        WorkoutSession(id = id, athleteId = "a", workoutName = id, day = day, exercises = exercises(done, total), createdAt = createdAt)

    // Day numbers: 20393 = 2025-11-01, 20394 = 2025-11-02, 20363 = 2025-10-02.
    private val sessions = listOf(
        session("oct", 20363, done = 3),
        session("nov2", 20393, done = 1),
        session("nov3-a", 20394, done = 3, createdAt = 1),
        session("nov3-b", 20394, done = 0, createdAt = 2)
    )

    private fun ids(items: List<LogItem>) = items.filterIsInstance<LogItem.Entry>().map { it.session.id }

    @Test
    fun newestFirstGroupedByMonth() {
        val items = workoutLog(sessions, LogSort.NEWEST, LogFilter.ALL)
        assertEquals(listOf("nov3-b", "nov3-a", "nov2", "oct"), ids(items))
        val months = items.filterIsInstance<LogItem.Month>()
        assertEquals(listOf(2025 to 10, 2025 to 9), months.map { it.year to it.month })
        assertEquals(listOf(3, 1), months.map { it.count })
        assertTrue(items.first() is LogItem.Month)
    }

    @Test
    fun oldestFirst() {
        assertEquals(listOf("oct", "nov2", "nov3-a", "nov3-b"), ids(workoutLog(sessions, LogSort.OLDEST, LogFilter.ALL)))
    }

    @Test
    fun filtersByDone() {
        assertEquals(listOf("nov3-a", "oct"), ids(workoutLog(sessions, LogSort.NEWEST, LogFilter.DONE)))
        assertEquals(listOf("nov3-b", "nov2"), ids(workoutLog(sessions, LogSort.NEWEST, LogFilter.IN_PROGRESS)))
    }

    @Test
    fun checkOffsAndLoggedSets() {
        val s = session("s", 1, done = 0)
        assertTrue(s.toggled(1).exercises[1].done)
        assertFalse(s.toggled(1).toggled(1).exercises[1].done)
        assertTrue(s.allMarked(true).isComplete)
        val logged = s.withLoggedSet(0, 225f, 5).withLoggedSet(0, 227.5f, 3)
        assertTrue(logged.exercises[0].done)
        assertEquals("225 × 5, 227.5 × 3", logged.exercises[0].result)
        assertEquals(s, s.toggled(99))   // out of range does nothing
    }

    @Test
    fun anEmptyWorkoutIsNeverComplete() {
        assertFalse(session("empty", 1, done = 0, total = 0).isComplete)
    }

    @Test
    fun sessionsCopyTheWorkoutInOrder() {
        val item = WorkoutWithExercises(
            Workout(id = 1, name = " Leg Day ", notes = "Warm up"),
            listOf(
                WorkoutExercise(name = "Squat", sets = 5, reps = "5", position = 1),
                WorkoutExercise(name = "Box Jumps", sets = 3, reps = "3", position = 0)
            )
        )
        val s = sessionFrom(item, athleteId = "a", day = 10, source = "Premade", now = 99)
        assertEquals("Leg Day", s.workoutName)
        assertEquals(listOf("Box Jumps", "Squat"), s.exercises.map { it.name })
        assertEquals(0, s.doneCount)
        assertEquals(99L, s.createdAt)
    }
}
