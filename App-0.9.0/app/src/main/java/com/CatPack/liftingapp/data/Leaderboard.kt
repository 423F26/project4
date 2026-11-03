package com.example.liftingapp.data

enum class RankBy { ESTIMATED_MAX, POUND_FOR_POUND }

/** One row of the leaderboard: an athlete's single best set for the chosen exercise. */
data class LeaderboardRow(
    val athlete: Athlete,
    val bestSet: LiftEntry,
    val estimatedMax: Float,     // e1RM of the best set, in pounds
    val poundForPound: Float     // estimatedMax / body weight (e.g. 1.5 = 1.5× body weight)
)

/**
 * Plain Kotlin, no Android or Room calls, so it can be unit tested directly
 * (see LeaderboardTest). Each athlete appears once, using their best set by e1RM.
 */
fun buildLeaderboard(lifts: List<LiftWithAthlete>, rankBy: RankBy): List<LeaderboardRow> =
    lifts
        .groupBy { it.athlete.id }
        .values
        .map { setsForAthlete ->
            val best = setsForAthlete.maxBy { Strength.estimatedOneRepMax(it.lift.weight, it.lift.reps) }
            val e1rm = Strength.estimatedOneRepMax(best.lift.weight, best.lift.reps)
            LeaderboardRow(
                athlete = best.athlete,
                bestSet = best.lift,
                estimatedMax = e1rm,
                poundForPound = e1rm / best.athlete.bodyWeightLb
            )
        }
        .sortedByDescending {
            when (rankBy) {
                RankBy.ESTIMATED_MAX -> it.estimatedMax
                RankBy.POUND_FOR_POUND -> it.poundForPound
            }
        }
