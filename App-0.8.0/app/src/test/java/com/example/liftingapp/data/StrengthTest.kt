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

    @Test(expected = IllegalArgumentException::class)
    fun zeroRepsIsRejected() {
        Strength.estimatedOneRepMax(225f, 0)
    }
}
