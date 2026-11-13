package com.example.liftingapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

/**
 * Account & settings → Delete account. Removes everything that belongs to the person, then the sign-in itself:
 *  - their General athletes, sets and workout log, and their General leaderboard rows
 *  - on each team: their own athlete profile (with its sets and log) and their membership
 *  - who they follow (so those follower counts go back down) and their own follower count
 *  - their own workouts and the workouts friends sent them (team workouts they made stay with the team)
 *  - their profile picture and profile
 * Athletes a coach added to a team stay with the team, as do teams they created (so teammates keep
 * their data), though without the creator no one can see those teams' join codes any more.
 */
object AccountDeletion {

    /** Firebase only deletes a sign-in that's a few minutes old. Older ones need signing in again first. */
    private const val RECENT_SIGN_IN_MS = 5 * 60 * 1000L

    fun needsFreshSignIn(): Boolean {
        val lastSignIn = FirebaseAuth.getInstance().currentUser?.metadata?.lastSignInTimestamp ?: return true
        return System.currentTimeMillis() - lastSignIn > RECENT_SIGN_IN_MS
    }

    /** Throws [TeamException] with a message to show if it can't finish. */
    suspend fun deleteEverything() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid

        GeneralBoard.removeAll(uid)
        val general = LiftRepository(Scope.General, uid)
        CloudPaths.athletes(Scope.General, uid).get().awaitResult().documents.forEach { general.deleteAthlete(it.id) }

        CloudPaths.myTeams(uid).get().awaitResult().documents.forEach { teamDoc ->
            val team = Scope.Team(teamDoc.id, teamDoc.getString("teamName").orEmpty())
            val own = CloudPaths.athlete(team, uid, uid)
            if (runCatching { own.get().awaitResult().exists() }.getOrDefault(false)) {
                LiftRepository(team, uid).deleteAthlete(uid)
            }
            runCatching { TeamPeople.remove(team.id, uid) }
            CloudPaths.members(team.id).document(uid).delete().awaitResult()
            teamDoc.reference.delete().awaitResult()
        }

        Follows.unfollowAll(uid)
        AccountWorkouts.deleteAll(uid)
        FriendInbox.deleteAll(uid)
        runCatching { CloudPaths.followerCount(uid).delete().awaitResult() }
        runCatching { CloudPaths.avatar(uid).delete().awaitResult() }
        CloudPaths.user(uid).delete().awaitResult()
        Avatars.forget(uid)

        try {
            user.delete().awaitResult()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw TeamException("Your data was deleted, but Firebase needs a fresh sign-in to remove the account. Sign out, sign back in and choose Delete account again.")
        }
    }
}
