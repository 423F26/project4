package com.example.liftingapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * Sending workouts inside the app ("Send in CatPack"):
 *  - On a team, to anyone on it (athletes and coaches) or the whole team: teams/{teamId}/sent
 *    (TeamInbox in TeamWorkouts.kt). Who's on the team comes from teams/{teamId}/people, which
 *    every member writes for themselves and every member can read.
 *  - Anywhere, to a mutual friend (you follow each other from the leaderboard): inbox/{uid}/items.
 *    These belong to the account, so they show on every team and in General use.
 */

/** Someone a workout can be sent to in the app. */
data class Recipient(val uid: String, val name: String, val isCoach: Boolean = false)

/** teams/{teamId}/people/{uid}: name and whether they're a coach, for the in-app Send list. */
object TeamPeople {

    /** Adds or updates this person on the team's list. Called whenever they open the team. */
    suspend fun announce(teamId: String, uid: String, name: String, isCoach: Boolean) {
        CloudPaths.teamPeople(teamId).document(uid).set(
            mapOf("name" to name, "coach" to isCoach, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).awaitResult()
    }

    /** Everyone on the team except [exceptUid]: coaches first, then A to Z. */
    fun list(teamId: String, exceptUid: String): Flow<List<Recipient>> =
        CloudPaths.teamPeople(teamId).changes().map { snapshot ->
            snapshot.documents
                .filter { it.id != exceptUid }
                .map { Recipient(it.id, it.getString("name").orEmpty().ifBlank { "Teammate" }, it.get("coach").asBool(false)) }
                .sortedWith(compareBy({ !it.isCoach }, { it.name.lowercase() }))
        }

    suspend fun remove(teamId: String, uid: String) {
        CloudPaths.teamPeople(teamId).document(uid).delete().awaitResult()
    }
}

/** Friends are people who follow each other (Follows.kt). Only friends can send each other workouts. */
object Friends {

    /**
     * The people [uid] follows who follow back. The rules let you check whether someone follows you
     * only when you follow them, so this never reveals other followers.
     */
    suspend fun mutual(uid: String): List<Recipient> =
        CloudPaths.following(uid).get().awaitResult().documents.mapNotNull { doc ->
            val followsBack = runCatching {
                CloudPaths.following(doc.id).document(uid).get().awaitResult().exists()
            }.getOrDefault(false)
            if (followsBack) Recipient(doc.id, doc.getString("name").orEmpty().ifBlank { "CatPack friend" }) else null
        }.sortedBy { it.name.lowercase() }
}

/** inbox/{uid}/items: workouts friends sent this account. */
object FriendInbox {

    /** Newest first. Shown as "Sent to you" on the Workouts tab everywhere. */
    fun list(uid: String): Flow<List<SentWorkout>> =
        CloudPaths.inbox(uid).changes().map { snapshot ->
            snapshot.documents.map { it.toSentWorkout() }.sortedByDescending { it.sentAt }
        }

    suspend fun send(item: WorkoutWithExercises, fromUid: String, fromName: String, to: Recipient) {
        CloudPaths.inbox(to.uid).document().set(
            mapOf(
                "name" to item.workout.name.trim(),
                "notes" to item.workout.notes.trim(),
                "exercises" to exercisesToMaps(item.exercises.sortedBy { it.position }),
                "fromUid" to fromUid,
                "fromName" to fromName,
                "to" to listOf(to.uid),
                "toName" to to.name,
                "sentAt" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).awaitResult()
    }

    suspend fun delete(uid: String, id: String) {
        CloudPaths.inbox(uid).document(id).delete().awaitResult()
    }

    suspend fun deleteAll(uid: String) {
        CloudPaths.inbox(uid).get().awaitResult().documents.forEach { it.reference.delete().awaitResult() }
    }
}
