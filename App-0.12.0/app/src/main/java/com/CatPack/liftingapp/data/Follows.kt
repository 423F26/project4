package com.example.liftingapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Following people from the leaderboard. Only counts are shared, never who:
 *  - users/{me}/following/{target}  who I follow; only I can read it.
 *  - followerCounts/{target}        { count }; only the target can read it, so each person sees how
 *                                   many follow them, and nobody else sees anyone's count.
 * People who follow each other are friends and can send each other workouts (Friends in
 * InAppSending.kt); that's the one place a follow-back is visible, and only to someone you follow.
 * A follow and its +1 are written in one batch, and the security rules only accept a count change
 * that matches a follow being added or removed in the same batch, so the count can't be pumped up.
 */
object Follows {

    /** The accounts [uid] follows. */
    fun following(uid: String): Flow<Set<String>> =
        CloudPaths.following(uid).changes().map { snapshot -> snapshot.documents.map { it.id }.toSet() }

    /** How many people follow [uid]. Only [uid] is allowed to read this. */
    fun followerCount(uid: String): Flow<Int> =
        CloudPaths.followerCount(uid).changes().map { maxOf(0, it.get("count").asInt()) }

    /** [name] is how they appear on the leaderboard; it labels them in your friends list when sending. */
    suspend fun follow(me: String, target: String, name: String) {
        if (me == target) return
        val batch = CloudPaths.db.batch()
        batch.set(CloudPaths.following(me).document(target), mapOf("followedAt" to FieldValue.serverTimestamp(), "name" to name))
        batch.set(CloudPaths.followerCount(target), mapOf("count" to FieldValue.increment(1)), SetOptions.merge())
        batch.commit().awaitResult()
    }

    suspend fun unfollow(me: String, target: String) {
        val batch = CloudPaths.db.batch()
        batch.delete(CloudPaths.following(me).document(target))
        batch.set(CloudPaths.followerCount(target), mapOf("count" to FieldValue.increment(-1)), SetOptions.merge())
        batch.commit().awaitResult()
    }

    /** Un-follows everyone [me] follows (their own account deletion), so those counts go back down. */
    suspend fun unfollowAll(me: String) {
        CloudPaths.following(me).get().awaitResult().documents.forEach { doc ->
            runCatching { unfollow(me, doc.id) }.onFailure { doc.reference.delete().awaitResult() }
        }
    }
}
