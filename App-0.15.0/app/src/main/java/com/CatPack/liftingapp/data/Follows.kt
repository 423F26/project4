package com.example.liftingapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Someone who follows an account, as shown in a followers list. */
data class Follower(val uid: String, val name: String)

/**
 * Following people from the leaderboard. Followers are public to signed-in users:
 *  - users/{me}/following/{target}      who I follow; only I can read it.
 *  - followers/{target}/people/{me}     { name, followedAt }: the public list of who follows target.
 *  - followerCounts/{target}            { count }; any signed-in user can read it.
 * People who follow each other are friends and can send each other workouts (Friends in
 * InAppSending.kt). A follow, its followers entry and its +1 are written in one batch, and the
 * security rules only accept a count change or followers entry that matches the follower's own
 * following doc in the same batch, so neither can be faked or pumped up.
 */
object Follows {

    /** The accounts [uid] follows. */
    fun following(uid: String): Flow<Set<String>> =
        CloudPaths.following(uid).changes().map { snapshot -> snapshot.documents.map { it.id }.toSet() }

    /** How many people follow [uid]. Any signed-in user can read this. */
    fun followerCount(uid: String): Flow<Int> =
        CloudPaths.followerCount(uid).changes().map { maxOf(0, it.get("count").asInt()) }

    /** Who follows [uid], sorted by name. Any signed-in user can read this. */
    suspend fun followers(uid: String): List<Follower> =
        CloudPaths.followers(uid).get().awaitResult().documents
            .map { Follower(it.id, it.getString("name").orEmpty().ifBlank { "CatPack athlete" }) }
            .sortedBy { it.name.lowercase() }

    /**
     * [name] is how the target appears on the leaderboard; it labels them in your friends list when
     * sending. [myName] is how you appear in their followers list.
     */
    suspend fun follow(me: String, target: String, name: String, myName: String) {
        if (me == target) return
        val batch = CloudPaths.db.batch()
        batch.set(CloudPaths.following(me).document(target), mapOf("followedAt" to FieldValue.serverTimestamp(), "name" to name))
        batch.set(CloudPaths.followers(target).document(me), mapOf("followedAt" to FieldValue.serverTimestamp(), "name" to myName))
        batch.set(CloudPaths.followerCount(target), mapOf("count" to FieldValue.increment(1)), SetOptions.merge())
        batch.commit().awaitResult()
    }

    suspend fun unfollow(me: String, target: String) {
        val batch = CloudPaths.db.batch()
        batch.delete(CloudPaths.following(me).document(target))
        batch.delete(CloudPaths.followers(target).document(me))
        batch.set(CloudPaths.followerCount(target), mapOf("count" to FieldValue.increment(-1)), SetOptions.merge())
        batch.commit().awaitResult()
    }

    /** Un-follows everyone [me] follows (their own account deletion), so those counts go back down. */
    suspend fun unfollowAll(me: String) {
        CloudPaths.following(me).get().awaitResult().documents.forEach { doc ->
            runCatching { unfollow(me, doc.id) }.onFailure {
                runCatching { CloudPaths.followers(doc.id).document(me).delete().awaitResult() }
                doc.reference.delete().awaitResult()
            }
        }
    }

    /**
     * Makes sure everyone [me] follows lists them as a follower under [myName]. Follows made before
     * 0.13 have no followers entry, and a name change should show in those lists; run once per launch.
     */
    suspend fun syncMyFollowerEntries(me: String, myName: String) {
        CloudPaths.following(me).get().awaitResult().documents.forEach { doc ->
            CloudPaths.followers(doc.id).document(me)
                .set(mapOf("name" to myName), SetOptions.merge())
                .orReport("update your follower entries")
        }
    }

    /** Clears the list of who follows [uid] (deleting that account). */
    suspend fun clearFollowers(uid: String) {
        CloudPaths.followers(uid).get().awaitResult().documents.forEach { it.reference.delete().awaitResult() }
    }
}
