package com.example.liftingapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldValue

/**
 * Admin and developer accounts (users/{uid}.staffRole, set by hand in the Firebase console) can
 * delete other people's accounts from Account & settings → Admin → Manage accounts.
 *
 * The app can't remove someone else's Firebase sign-in (that needs the Admin SDK on a server), so
 * deleting works in two steps:
 *  1. The admin's phone deletes the same data the person's own "Delete account" would (their
 *     General athletes and leaderboard rows, their own athlete profile and membership on each team,
 *     who they follow, their follower count, picture and profile) and writes removedAccounts/{uid}.
 *  2. The next time that person opens the app, [finishIfRemoved] sees the marker, removes their
 *     sign-in and signs them out, so they can't use the account again.
 * Staff accounts can't be deleted this way (the rules refuse it too); remove staffRole in the
 * console first.
 */
object AdminAccounts {

    /** Every account, sorted by name, for the Manage accounts list. Only staff are allowed to read this. */
    suspend fun all(): List<AccountSummary> =
        CloudPaths.users().get().awaitResult().documents
            .map { it.toAccountSummary() }
            .sortedWith(compareBy({ it.name.ifBlank { it.email }.lowercase() }, { it.uid }))

    /** Throws [TeamException] with a message to show if it can't finish. */
    suspend fun delete(target: AccountSummary, adminUid: String) {
        if (target.uid == adminUid) throw TeamException("To delete your own account, use Delete account under Account.")
        if (target.isStaff) throw TeamException("${target.label()} is a staff account. Remove its staffRole in the Firebase console first.")
        val uid = target.uid

        // The marker goes first, so even if a later step fails the person is stopped at next sign-in.
        CloudPaths.removedAccount(uid).set(
            mapOf("removedBy" to adminUid, "removedAt" to FieldValue.serverTimestamp(), "email" to target.email)
        ).awaitResult()

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
            runCatching { CloudPaths.members(team.id).document(uid).delete().awaitResult() }
            teamDoc.reference.delete().awaitResult()
        }

        // Who they followed. The counts of the people they followed stay as they are (the rules only let
        // a follower change a count together with their own follow), which is at most off by one each.
        CloudPaths.following(uid).get().awaitResult().documents.forEach { it.reference.delete().awaitResult() }
        AccountWorkouts.deleteAll(uid)
        FriendInbox.deleteAll(uid)
        runCatching { CloudPaths.followerCount(uid).delete().awaitResult() }
        runCatching { CloudPaths.avatar(uid).delete().awaitResult() }
        CloudPaths.user(uid).delete().awaitResult()
        Avatars.forget(uid)
    }

    /**
     * Called when the app opens. If an admin deleted this account, removes the sign-in (Firebase
     * allows that right after signing in) and returns true; the caller then signs out and says why.
     * Returns false for everyone else, and when it can't check (offline), so nobody is locked out by mistake.
     */
    suspend fun finishIfRemoved(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        if (!isRemoved(user.uid)) return false
        try {
            user.delete().awaitResult()
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            // Signed in too long ago; the sign-in is removed the next time they sign in.
        } catch (_: Exception) {
            // Offline or similar: same, they're signed out and it's tried again next time.
        }
        return true
    }

    /**
     * True if an admin deleted [uid]. Checked before the app re-creates a missing profile or athlete,
     * so a deleted account's data doesn't come back in the moment before it's signed out.
     */
    suspend fun isRemoved(uid: String): Boolean =
        runCatching { CloudPaths.removedAccount(uid).get().awaitResult().exists() }.getOrDefault(false)

    fun AccountSummary.label(): String = name.ifBlank { email.ifBlank { "This account" } }
}
