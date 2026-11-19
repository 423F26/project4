package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CardioTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    private fun run(day: Long, meters: Double, seconds: Long, atHour: Long = 10) =
        LiftEntry.cardio("a", "Run", meters, seconds, date = day * DAY_MS + atHour * hour)

    // --- Unit conversion ---

    @Test
    fun milesAndKilometersConvertToMeters() {
        assertEquals(1609.344, Cardio.toMeters(1.0, DistanceUnit.MILES), 1e-9)
        assertEquals(5000.0, Cardio.toMeters(5.0, DistanceUnit.KILOMETERS), 1e-9)
        assertEquals(3.10686, Cardio.fromMeters(5000.0, DistanceUnit.MILES), 1e-5)
        assertEquals(42.195, Cardio.fromMeters(42_195.0, DistanceUnit.KILOMETERS), 1e-9)
    }

    @Test
    fun switchingUnitsRoundTripsWithoutChangingMeters() {
        val meters = Cardio.toMeters(3.1, DistanceUnit.MILES)
        val km = Cardio.fromMeters(meters, DistanceUnit.KILOMETERS)
        assertEquals(meters, Cardio.toMeters(km, DistanceUnit.KILOMETERS), 1e-9)
    }

    @Test
    fun distanceFormatting() {
        assertEquals("5 km", Cardio.formatDistance(5000.0, DistanceUnit.KILOMETERS))
        assertEquals("3.11 mi", Cardio.formatDistance(5000.0, DistanceUnit.MILES))
        assertEquals("1 mi", Cardio.formatDistance(1609.344, DistanceUnit.MILES))
        assertEquals("0.4 km", Cardio.formatDistance(400.0, DistanceUnit.KILOMETERS))
        assertEquals("3.11", Cardio.distanceInput(5000.0, DistanceUnit.MILES))
    }

    @Test
    fun unitKeysRoundTripAndDefaultToMiles() {
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.fromKey("km"))
        assertEquals(DistanceUnit.MILES, DistanceUnit.fromKey("mi"))
        assertEquals(DistanceUnit.MILES, DistanceUnit.fromKey(null))
        assertEquals(DistanceUnit.MILES, DistanceUnit.fromKey("furlongs"))
    }

    // --- Pace ---

    @Test
    fun paceIsTimeOverDistanceInTheChosenUnit() {
        // 5 km in 25:00 = 5:00 /km = 8:03 /mi
        assertEquals(300.0, Cardio.paceSeconds(5000.0, 1500, DistanceUnit.KILOMETERS)!!, 1e-9)
        assertEquals(482.8, Cardio.paceSeconds(5000.0, 1500, DistanceUnit.MILES)!!, 0.1)
        assertEquals("5:00 /km", Cardio.formatPace(5000.0, 1500, DistanceUnit.KILOMETERS))
        assertEquals("8:03 /mi", Cardio.formatPace(5000.0, 1500, DistanceUnit.MILES))
    }

    @Test
    fun paceNeedsDistanceAndTime() {
        assertNull(Cardio.paceSeconds(0.0, 600, DistanceUnit.MILES))
        assertNull(Cardio.paceSeconds(1000.0, 0, DistanceUnit.MILES))
        assertEquals("—", Cardio.formatPace(0.0, 600, DistanceUnit.MILES))
    }

    @Test
    fun slowPacesShowHours() {
        // 1 km in 1:02:10 (a walk with a long stop)
        assertEquals("1:02:10 /km", Cardio.formatPace(1000.0, 3730, DistanceUnit.KILOMETERS))
    }

    @Test
    fun describeReadsDistanceTimeAndPace() {
        assertEquals("1 mi in 7:30 (7:30 /mi)", Cardio.describe(1609.344, 450, DistanceUnit.MILES))
    }

    // --- Time input ---

    @Test
    fun parsesTimes() {
        assertEquals(1470L, Cardio.parseDuration("24:30"))
        assertEquals(3905L, Cardio.parseDuration("1:05:05"))
        assertEquals(1500L, Cardio.parseDuration("25"))        // plain minutes
        assertEquals(90L, Cardio.parseDuration(" 1:30 "))
        assertEquals(5400L, Cardio.parseDuration("90:00"))     // minutes past 59 are fine in mm:ss
    }

    @Test
    fun rejectsBadTimes() {
        assertNull(Cardio.parseDuration(""))
        assertNull(Cardio.parseDuration("0:00"))
        assertNull(Cardio.parseDuration("12:60"))
        assertNull(Cardio.parseDuration("1:60:00"))
        assertNull(Cardio.parseDuration("1::00"))
        assertNull(Cardio.parseDuration("1:2:3:4"))
        assertNull(Cardio.parseDuration("abc"))
        assertNull(Cardio.parseDuration("25:00:01"))           // over 24 hours
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:45", Cardio.formatDuration(45))
        assertEquals("24:30", Cardio.formatDuration(1470))
        assertEquals("1:05:05", Cardio.formatDuration(3905))
    }

    @Test
    fun parsesDistances() {
        assertEquals(1609.344 * 3.1, Cardio.parseDistance("3.1", DistanceUnit.MILES)!!, 1e-6)
        assertEquals(5000.0, Cardio.parseDistance("5,0", DistanceUnit.KILOMETERS)!!, 1e-9)
        assertNull(Cardio.parseDistance("0", DistanceUnit.MILES))
        assertNull(Cardio.parseDistance("-2", DistanceUnit.MILES))
        assertNull(Cardio.parseDistance("far", DistanceUnit.MILES))
        assertNull(Cardio.parseDistance("5000", DistanceUnit.KILOMETERS))   // over the 1000 km cap
    }

    // --- Which exercises are cardio ---

    @Test
    fun builtInAndCustomCardio() {
        assertTrue(Cardio.isCardio("Run"))
        assertTrue(Cardio.isCardio("  bike "))
        assertFalse(Cardio.isCardio("Bench"))
        assertFalse(Cardio.isCardio("Bent-over Row"))
        assertTrue(Cardio.isCardio("Hill Sprints", listOf("hill sprints")))
        assertTrue(ExerciseCatalog.contains(ExerciseCatalog.build(emptyList()), "Swim"))
    }

    // --- Cardio sets stay out of lift-only numbers ---

    @Test
    fun liftNumbersIgnoreCardio() {
        val sets = listOf(
            run(100, 5000.0, 1500),
            LiftEntry(athleteId = "a", exercise = "Run", weight = 225f, reps = 5, date = 100 * DAY_MS)
        )
        assertEquals(1, dailyBests(sets, utc).size)
        assertEquals(225f, Strength.bestOf(sets, 200f)!!.weight, 0.01f)
        assertNull(Strength.bestOf(listOf(run(100, 5000.0, 1500)), 200f))
        assertTrue(dailyBests(listOf(run(100, 5000.0, 1500)), utc).isEmpty())
    }

    @Test
    fun cardioDaysKeepTheFastestSetAndTotals() {
        val days = cardioDays(
            listOf(
                run(100, 5000.0, 1500, atHour = 8),    // 5:00 /km
                run(100, 1000.0, 270, atHour = 18),    // 4:30 /km: fastest that day
                run(102, 8000.0, 2800)
            ),
            utc
        )
        assertEquals(listOf(100L, 102L), days.map { it.day })
        assertEquals(1000.0, days[0].bestSet.distanceMeters, 1e-9)
        assertEquals(6000.0, days[0].totalMeters, 1e-9)
        assertEquals(1770L, days[0].totalSeconds)
        assertEquals(270.0, days[0].pace(DistanceUnit.KILOMETERS), 1e-9)
    }

    @Test
    fun cardioWindowFindsTheFastestDayInRange() {
        val days = cardioDays(listOf(run(10, 5000.0, 1400), run(100, 5000.0, 1500), run(110, 5000.0, 1450)), utc)
        val month = cardioWindow(days, ChartRange.MONTH, today = 110)
        assertEquals(listOf(100L, 110L), month.days.map { it.day })
        assertEquals(110L, month.fastest!!.day)
        val all = cardioWindow(days, ChartRange.ALL, today = 110)
        assertEquals(10L, all.fastest!!.day)
        assertEquals(15_000.0, all.totalMeters, 1e-9)
    }

    @Test
    fun loggedCardioReadsOnTheChecklist() {
        val session = WorkoutSession(
            athleteId = "a", workoutName = "Conditioning", day = 100,
            exercises = listOf(SessionExercise(name = "Run", sets = 1, reps = "2 mi"))
        )
        val logged = session.withLoggedCardio(0, 2 * 1609.344, 900, DistanceUnit.MILES)
        assertTrue(logged.exercises[0].done)
        assertEquals("2 mi in 15:00 (7:30 /mi)", logged.exercises[0].result)
    }
}
