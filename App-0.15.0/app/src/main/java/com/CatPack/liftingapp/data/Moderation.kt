package com.example.liftingapp.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import kotlin.math.abs

/**
 * Taking a fake lift off the leaderboard (someone "benched 4,000 lb"): coaches on their team, and
 * CatPack staff (admin / developer) on the General leaderboard. The set that made the best is
 * deleted, then the athlete's best for that exercise is worked out again from their other sets, so
 * their next-best set takes its place (or the row goes if there's none). Run it again if more than
 * one set was faked. firestore.rules lets coaches do this on their team and staff in General.
 */
object Moderation {

    /** Who may remove a best from [row]: a coach on a team, or staff on General (not their own rows). */
    fun canRemove(scope: Scope, row: LeaderboardRow, isCoach: Boolean, isStaff: Boolean, myUid: String): Boolean =
        when (scope) {
            is Scope.Team -> isCoach
            Scope.General -> isStaff && row.ownerUid != null && row.ownerUid != myUid
        }

    /**
     * Deletes [row]'s best [exercise] set and stores their new best. Returns the new best (null =
     * none left). [myUid] is used for team paths, which don't depend on the owner.
     */
    suspend fun removeBest(scope: Scope, row: LeaderboardRow, exercise: String, myUid: String): BestSet? {
        val owner = row.ownerUid ?: myUid
        val best = row.allBests[exercise]
        val lifts = CloudPaths.lifts(scope, owner, row.athleteId)
        val sets = lifts.whereEqualTo("exercise", exercise).get().awaitResult()
            .documents.mapNotNull { it.toLift(row.athleteId) }
            .filter { !it.isCardio }
        // The set the best came from (same date, weight and reps); otherwise the strongest set.
        val target = sets.firstOrNull { best != null && it.date == best.date && abs(it.weight - best.weight) < 0.01f && it.reps == best.reps }
            ?: sets.maxByOrNull { Strength.estimatedOneRepMax(it.weight, it.reps) }
        if (target != null) lifts.document(target.id).delete().awaitResult()

        val athleteRef = CloudPaths.athlete(scope, owner, row.athleteId)
        val bodyWeight = athleteRef.get().awaitResult().get("bodyWeightLb").asFloat()
        val newBest = Strength.bestOf(sets.filter { it.id != target?.id }, bodyWeight)
        val value: Any = newBest?.toMap() ?: FieldValue.delete()
        athleteRef.update(FieldPath.of("bests", exercise), value).awaitResult()
        if (scope == Scope.General) {
            // The public copy too; the owner's app re-publishes it from their bests next time anyway.
            CloudPaths.boardEntry(owner, row.athleteId)
                .update(FieldPath.of("bests", exercise), value, FieldPath.of("updatedAt"), System.currentTimeMillis())
                .awaitResult()
        }
        return newBest
    }
}
