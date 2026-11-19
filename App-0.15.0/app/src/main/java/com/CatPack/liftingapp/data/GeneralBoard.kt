package com.example.liftingapp.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The General leaderboard: everyone using the app on their own. Each person's General athletes are
 * private (general/{uid}/...), so their owner publishes a copy of the public parts (name or
 * initials, position, body weight, bests, and whose photo to show) to generalBoard/{uid}_{athleteId}.
 * Turning off "Show me on the General leaderboard" in Account & settings deletes those rows.
 */
object GeneralBoard {

    /** How many rows the General leaderboard loads for one exercise. */
    const val LIMIT = 100L

    /** The top [LIMIT] entries for [exercise], sorted by the server on the ranking number. */
    fun top(exercise: String, rankBy: RankBy): Flow<List<BoardEntry>> {
        val field = when (rankBy) {
            RankBy.ESTIMATED_MAX -> "e1rm"
            RankBy.POUND_FOR_POUND -> "p4p"
        }
        return CloudPaths.board()
            .orderBy(FieldPath.of("bests", exercise, field), Query.Direction.DESCENDING)
            .limit(LIMIT)
            .changes()
            .map { snapshot -> snapshot.documents.map { it.toBoardEntry() } }
    }

    /** Publishes or withdraws one General athlete's row to match [profile] (read from the cloud if null). */
    suspend fun syncOne(uid: String, athleteId: String, profile: UserProfile?) {
        val settings = profile ?: CloudPaths.user(uid).get().awaitResult().toProfile(uid, "", "")
        val snapshot = CloudPaths.athlete(Scope.General, uid, athleteId).get().awaitResult()
        val entry = CloudPaths.boardEntry(uid, athleteId)
        if (!snapshot.exists() || !settings.showOnGeneral) {
            entry.delete().orReport("update the General leaderboard")
            return
        }
        val athlete = snapshot.toAthlete()
        entry.set(
            mapOf(
                "ownerUid" to uid,
                "athleteId" to athleteId,
                "name" to if (settings.initialsOnGeneral) initialsOf(athlete.name) else athlete.name,
                "positionGroup" to athlete.positionGroup,
                "bodyWeightLb" to athlete.bodyWeightLb.toDouble(),
                "photoUid" to if (settings.showPhoto) athlete.linkedUid else null,
                "bests" to athlete.bests.mapValues { it.value.toMap() },
                "updatedAt" to System.currentTimeMillis()
            )
        ).orReport("update the General leaderboard")
    }

    /** Re-publishes every General athlete of [uid], e.g. after a privacy switch changes. */
    suspend fun syncAll(uid: String, profile: UserProfile) {
        CloudPaths.athletes(Scope.General, uid).get().awaitResult().documents.forEach { syncOne(uid, it.id, profile) }
    }

    /** Removes every row [uid] published (account deletion). */
    suspend fun removeAll(uid: String) {
        CloudPaths.athletes(Scope.General, uid).get().awaitResult().documents.forEach {
            CloudPaths.boardEntry(uid, it.id).delete().awaitResult()
        }
    }
}
