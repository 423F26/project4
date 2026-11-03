package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardTest {

    private val big = Athlete(id = 1, name = "Big Lineman", positionGroup = "OL", bodyWeightLb = 300f)
    private val small = Athlete(id = 2, name = "Small DB", positionGroup = "DB", bodyWeightLb = 180f)

    private fun set(athlete: Athlete, weight: Float, reps: Int, id: Long) =
        LiftWithAthlete(LiftEntry(id = id, athleteId = athlete.id, exercise = "Bench", weight = weight, reps = reps), athlete)

    private val lifts = listOf(
        set(big, 315f, 3, id = 1),   // e1RM 346.5
        set(big, 335f, 1, id = 2),   // e1RM 335
        set(small, 250f, 2, id = 3)  // e1RM 266.7 -> 1.48× BW (big is 1.155× BW)
    )

    @Test
    fun eachAthleteAppearsOnceWithTheirBestSet() {
        val rows = buildLeaderboard(lifts, RankBy.ESTIMATED_MAX)
        assertEquals(2, rows.size)
        assertEquals(1L, rows.first { it.athlete.id == 1L }.bestSet.id)   // 315×3 beats 335×1
    }

    @Test
    fun rawMaxRanksBiggerLifterFirst() {
        assertEquals(listOf(1L, 2L), buildLeaderboard(lifts, RankBy.ESTIMATED_MAX).map { it.athlete.id })
    }

    @Test
    fun poundForPoundFlipsTheOrder() {
        assertEquals(listOf(2L, 1L), buildLeaderboard(lifts, RankBy.POUND_FOR_POUND).map { it.athlete.id })
    }
}
