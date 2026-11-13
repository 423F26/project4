package com.example.liftingapp.data

enum class RankBy { ESTIMATED_MAX, POUND_FOR_POUND }

/** One row of the leaderboard: an athlete's single best set for the chosen exercise. */
data class LeaderboardRow(
    val key: String,             // athlete id (team) or board entry id (General)
    val name: String,
    val positionGroup: String,
    val bodyWeightLb: Float,
    val best: BestSet,
    val photoUid: String?,       // account whose profile picture to show, if any
    val isMine: Boolean,         // added by, or is, the signed-in person
    val accountUid: String? = null // the account this athlete is, for following; null if they have none
) {
    val estimatedMax: Float get() = best.e1rm
    val poundForPound: Float get() = best.p4p
}

/*
 * Plain Kotlin, no Android or Firebase calls, so it can be unit tested directly (see LeaderboardTest).
 * Each athlete appears once, using their best set by e1RM (kept on the athlete as bests[exercise]).
 */

/** A team's leaderboard for [exercise], built from the roster the Log tab already has. */
fun teamLeaderboard(athletes: List<Athlete>, exercise: String, rankBy: RankBy, myUid: String): List<LeaderboardRow> =
    athletes.mapNotNull { athlete ->
        val best = athlete.bests[exercise] ?: return@mapNotNull null
        LeaderboardRow(
            key = athlete.id,
            name = athlete.name,
            positionGroup = athlete.positionGroup,
            bodyWeightLb = athlete.bodyWeightLb,
            best = best,
            photoUid = athlete.linkedUid,
            isMine = athlete.id == myUid || athlete.linkedUid == myUid,
            accountUid = athlete.linkedUid
        )
    }.ranked(rankBy)

/** The General leaderboard for [exercise] from everyone's published board entries. */
fun generalLeaderboard(entries: List<BoardEntry>, exercise: String, rankBy: RankBy, myUid: String): List<LeaderboardRow> =
    entries.mapNotNull { entry ->
        val best = entry.bests[exercise] ?: return@mapNotNull null
        LeaderboardRow(
            key = entry.id,
            name = entry.name,
            positionGroup = entry.positionGroup,
            bodyWeightLb = entry.bodyWeightLb,
            best = best,
            photoUid = entry.photoUid,
            isMine = entry.ownerUid == myUid,
            // A person's own General athlete has their uid as its id; athletes a coach added there have no account.
            accountUid = entry.ownerUid.takeIf { entry.athleteId == entry.ownerUid }
        )
    }.ranked(rankBy)

/**
 * Highest first; ties keep the earlier best set ahead (they got there first). Pound-for-pound puts
 * athletes with no body weight yet at the bottom (their p4p is 0).
 */
fun List<LeaderboardRow>.ranked(rankBy: RankBy): List<LeaderboardRow> =
    sortedWith(
        compareByDescending<LeaderboardRow> {
            when (rankBy) {
                RankBy.ESTIMATED_MAX -> it.estimatedMax
                RankBy.POUND_FOR_POUND -> it.poundForPound
            }
        }.thenBy { it.best.date }
    )

/** "Jordan Smith" -> "J. S." for people who'd rather not show their full name to strangers. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "Athlete"
    return parts.take(3).joinToString(" ") { "${it.first().uppercaseChar()}." }
}
