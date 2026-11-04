package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthTest {

    @Test
    fun singleRepReturnsTheWeight() {
        assertEquals(315f, Strength.estimatedOneRepMax(315f, 1), 0.001f)
    }

    @Test
    fun epleyForFiveReps() {
        // 225 × (1 + 5/30) = 262.5
        assertEquals(262.5f, Strength.estimatedOneRepMax(225f, 5), 0.001f)
    }

    @Test
    fun bestOfPicksTheHighestEstimateAndKeepsTheEarlierOnTies() {
        val sets = listOf(
            LiftEntry(id = "late", athleteId = "a", exercise = "Bench", weight = 225f, reps = 5, date = 20),
            LiftEntry(id = "heavy", athleteId = "a", exercise = "Bench", weight = 245f, reps = 1, date = 15),
            LiftEntry(id = "early", athleteId = "a", exercise = "Bench", weight = 225f, reps = 5, date = 10)
        )
        val best = Strength.bestOf(sets, bodyWeightLb = 200f)!!
        assertEquals(262.5f, best.e1rm, 0.001f)   // 225×5 beats 245×1
        assertEquals(10L, best.date)              // the first time it was done
        assertEquals(1.3125f, best.p4p, 0.0001f)
    }

    @Test
    fun bestOfNothingIsNull() {
        assertEquals(null, Strength.bestOf(emptyList(), 200f))
        assertEquals(0f, Strength.poundForPound(300f, 0f), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRepsIsRejected() {
        Strength.estimatedOneRepMax(225f, 0)
    }
}
