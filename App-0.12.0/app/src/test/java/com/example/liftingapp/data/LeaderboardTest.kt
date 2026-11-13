package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardTest {

    private fun best(weight: Float, reps: Int, bodyWeight: Float, date: Long = 0L): BestSet =
        Strength.bestOf(listOf(LiftEntry(athleteId = "x", exercise = "Bench", weight = weight, reps = reps, date = date)), bodyWeight)!!

    private val big = Athlete(id = "big", name = "Big Lineman", positionGroup = "OL", bodyWeightLb = 300f,
        bests = mapOf("Bench" to best(315f, 3, 300f)))                     // e1RM 346.5 -> 1.155× BW
    private val small = Athlete(id = "me", name = "Small DB", positionGroup = "DB", bodyWeightLb = 180f, linkedUid = "me",
        bests = mapOf("Bench" to best(250f, 2, 180f)))                     // e1RM 266.7 -> 1.48× BW
    private val noBench = Athlete(id = "squatter", name = "Only Squats", positionGroup = "LB", bodyWeightLb = 220f,
        bests = mapOf("Squat" to best(405f, 1, 220f)))

    private val roster = listOf(big, small, noBench)

    @Test
    fun onlyAthletesWithTheExerciseAppear() {
        val rows = teamLeaderboard(roster, "Bench", RankBy.ESTIMATED_MAX, myUid = "me")
        assertEquals(listOf("big", "me"), rows.map { it.key })
    }

    @Test
    fun rawMaxRanksBiggerLifterFirst() {
        assertEquals(listOf("big", "me"), teamLeaderboard(roster, "Bench", RankBy.ESTIMATED_MAX, "me").map { it.key })
    }

    @Test
    fun poundForPoundFlipsTheOrder() {
        assertEquals(listOf("me", "big"), teamLeaderboard(roster, "Bench", RankBy.POUND_FOR_POUND, "me").map { it.key })
    }

    @Test
    fun marksTheSignedInPersonsRowAndPhoto() {
        val rows = teamLeaderboard(roster, "Bench", RankBy.ESTIMATED_MAX, "me")
        assertTrue(rows.single { it.key == "me" }.isMine)
        assertEquals("me", rows.single { it.key == "me" }.photoUid)
        assertEquals(null, rows.single { it.key == "big" }.photoUid)
    }

    @Test
    fun generalBoardUsesEachEntrysOwner() {
        val entries = listOf(
            BoardEntry("a_a", "a", "a", "J. S.", "WR", 190f, null, mapOf("Bench" to best(225f, 5, 190f))),
            BoardEntry("me_me", "me", "me", "Me", "DB", 180f, "me", mapOf("Bench" to best(275f, 1, 180f)))
        )
        val rows = generalLeaderboard(entries, "Bench", RankBy.ESTIMATED_MAX, myUid = "me")
        assertEquals(listOf("me_me", "a_a"), rows.map { it.key })
        assertTrue(rows.first().isMine)
    }

    @Test
    fun onlyAthletesWithAnAccountCanBeFollowed() {
        val entries = listOf(
            BoardEntry("a_a", "a", "a", "Own profile", "WR", 190f, null, mapOf("Bench" to best(225f, 5, 190f))),
            BoardEntry("coach_kid", "coach", "kid", "Added by coach", "DB", 180f, null, mapOf("Bench" to best(205f, 5, 180f)))
        )
        val general = generalLeaderboard(entries, "Bench", RankBy.ESTIMATED_MAX, myUid = "me")
        assertEquals("a", general.single { it.key == "a_a" }.accountUid)
        assertEquals(null, general.single { it.key == "coach_kid" }.accountUid)

        val team = teamLeaderboard(roster, "Bench", RankBy.ESTIMATED_MAX, "me")
        assertEquals("me", team.single { it.key == "me" }.accountUid)
        assertEquals(null, team.single { it.key == "big" }.accountUid)
    }

    @Test
    fun tiesGoToWhoeverGotThereFirst() {
        val early = Athlete("early", "Early", "WR", 200f, bests = mapOf("Bench" to best(225f, 5, 200f, date = 1L)))
        val late = Athlete("late", "Late", "WR", 200f, bests = mapOf("Bench" to best(225f, 5, 200f, date = 2L)))
        assertEquals(listOf("early", "late"), teamLeaderboard(listOf(late, early), "Bench", RankBy.ESTIMATED_MAX, "x").map { it.key })
    }

    @Test
    fun athletesWithoutBodyWeightRankLastPoundForPound() {
        val unknown = Athlete("new", "New Athlete", "", 0f, bests = mapOf("Bench" to best(400f, 1, 0f)))
        val rows = teamLeaderboard(roster + unknown, "Bench", RankBy.POUND_FOR_POUND, "me")
        assertEquals("new", rows.last().key)
        assertEquals("new", teamLeaderboard(roster + unknown, "Bench", RankBy.ESTIMATED_MAX, "me").first().key)
    }

    @Test
    fun accountDetailsBecomeTheOwnAthlete() {
        val profile = UserProfile(uid = "u", name = "Jordan Smith", email = "j@example.com", positionGroup = "WR", bodyWeightLb = 185f, phone = "406-555-0100")
        assertEquals(AthleteDetails("Jordan Smith", "WR", 185f, "406-555-0100", "j@example.com"), profile.athleteDetails())
        assertEquals(null, profile.copy(email = "").athleteDetails().email)
    }

    @Test
    fun initialsHideTheFullName() {
        assertEquals("J. S.", initialsOf("Jordan  Smith"))
        assertEquals("J.", initialsOf("jordan"))
        assertEquals("Athlete", initialsOf("   "))
    }
}
