package com.example.liftingapp.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/** A problem worth showing as-is ("No team uses that code"). */
class TeamException(message: String) : Exception(message)

/**
 * Teams: creating one (coaches), joining with a code, leaving, and the creator's join code.
 *
 * Joining works without being able to list or read any team: the code is looked up directly at
 * joinCodes/{code}, and the security rules only accept the new member if the code they send
 * matches the team's secret code. See firestore.rules.
 */
class TeamRepository(private val uid: String) {

    private val db get() = CloudPaths.db

    /** The teams this person is on, A to Z. */
    fun myTeams(): Flow<List<TeamMembership>> =
        CloudPaths.myTeams(uid).changes().map { snapshot ->
            snapshot.documents.map { it.toMembership() }.sortedBy { it.teamName.lowercase() }
        }

    /**
     * Coaches only (the rules check the role in users/{uid}). Creates the team, its secret code and
     * the code lookup, and adds the creator as its first member, all in one write.
     * Returns the new team and its join code.
     */
    suspend fun createTeam(name: String, sport: String, ownerName: String): Pair<Scope.Team, String> {
        val teamRef = CloudPaths.teams().document()
        val teamId = teamRef.id
        val code = JoinCode.generate()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(teamRef, mapOf("name" to name, "sport" to sport, "ownerUid" to uid, "ownerName" to ownerName, "createdAt" to now))
        batch.set(CloudPaths.teamSecret(teamId), mapOf("code" to code, "ownerUid" to uid))
        batch.set(CloudPaths.joinCode(code), mapOf("teamId" to teamId, "teamName" to name, "sport" to sport, "ownerUid" to uid))
        batch.set(
            CloudPaths.members(teamId).document(uid),
            mapOf("name" to ownerName, "role" to UserProfile.ROLE_COACH, "owner" to true, "joinedAt" to now)
        )
        batch.set(CloudPaths.myTeams(uid).document(teamId), mapOf("teamName" to name, "sport" to sport, "owner" to true, "joinedAt" to now))
        commit("create the team") { batch.commit().awaitResult() }
        return Scope.Team(teamId, name, sport, isOwner = true) to code
    }

    /** Looks up [rawCode] and joins that team. Already on it? Just returns it. */
    suspend fun joinTeam(rawCode: String, memberName: String, role: String): Scope.Team {
        val code = JoinCode.normalize(rawCode)
            ?: throw TeamException("Team codes are ${JoinCode.LENGTH} letters and numbers, like K7QX-M4RT.")
        val lookup = commit("look up that code") { CloudPaths.joinCode(code).get().awaitResult() }
        if (!lookup.exists()) throw TeamException("No team uses that code. Check it with your coach; codes can be reset.")
        val teamId = lookup.getString("teamId") ?: throw TeamException("That code isn't set up right. Ask your coach for a new one.")
        val teamName = lookup.getString("teamName").orEmpty().ifEmpty { "Team" }
        val sport = lookup.getString("sport").orEmpty()

        val mine = CloudPaths.myTeams(uid).document(teamId)
        val existing = runCatching { mine.get().awaitResult() }.getOrNull()
        if (existing?.exists() == true) return Scope.Team(teamId, teamName, sport, existing.getBoolean("owner") == true)

        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            CloudPaths.members(teamId).document(uid),
            mapOf("name" to memberName, "role" to role, "owner" to false, "joinedAt" to now, "code" to code)
        )
        batch.set(mine, mapOf("teamName" to teamName, "sport" to sport, "owner" to false, "joinedAt" to now))
        commit("join the team") { batch.commit().awaitResult() }
        return Scope.Team(teamId, teamName, sport, isOwner = false)
    }

    /** Leaves the team. The athlete profile and lifts stay with the team (a coach can delete them). */
    suspend fun leaveTeam(teamId: String) {
        val batch = db.batch()
        batch.delete(CloudPaths.teamPeople(teamId).document(uid))   // off the in-app Send list
        batch.delete(CloudPaths.members(teamId).document(uid))
        batch.delete(CloudPaths.myTeams(uid).document(teamId))
        commit("leave the team") { batch.commit().awaitResult() }
    }

    /** The team's join code. Only its creator can read it. */
    suspend fun joinCode(teamId: String): String? =
        commit("load the team code") { CloudPaths.teamSecret(teamId).get().awaitResult() }.getString("code")

    /** Makes a new code and retires the old one, e.g. after a code was shared too widely. */
    suspend fun resetJoinCode(team: Scope.Team): String {
        val old = joinCode(team.id)
        val code = JoinCode.generate()
        val batch = db.batch()
        if (old != null) batch.delete(CloudPaths.joinCode(old))
        batch.set(CloudPaths.joinCode(code), mapOf("teamId" to team.id, "teamName" to team.name, "sport" to team.sport, "ownerUid" to uid))
        batch.set(CloudPaths.teamSecret(team.id), mapOf("code" to code, "ownerUid" to uid), SetOptions.merge())
        commit("reset the code") { batch.commit().awaitResult() }
        return code
    }

    /** Everyone on the team: the creator first, then coaches, then athletes A to Z. Coaches only. */
    suspend fun members(teamId: String): List<TeamMember> =
        commit("load the members") { CloudPaths.members(teamId).get().awaitResult() }
            .documents.map { it.toMember() }
            .sortedWith(
                compareByDescending<TeamMember> { it.isOwner }
                    .thenByDescending { it.role == UserProfile.ROLE_COACH }
                    .thenBy { it.name.lowercase() }
            )

    /**
     * Whether this person is still on the team (they may have left on another phone, or been
     * removed). Null when it can't be checked right now, e.g. offline.
     */
    suspend fun isStillMember(teamId: String): Boolean? =
        runCatching { CloudPaths.members(teamId).document(uid).get().awaitResult().exists() }.getOrNull()

    /**
     * Runs a network step with a time limit and turns Firebase errors into messages people can act on.
     * Writes made offline are still queued by Firestore and go through once the phone reconnects.
     */
    private suspend fun <T> commit(what: String, block: suspend () -> T): T =
        try {
            withTimeout(NETWORK_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            throw TeamException("Couldn't $what. Check your connection and try again.")
        } catch (e: FirebaseFirestoreException) {
            throw TeamException(
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "You don't have permission to $what. Only coaches can create teams; codes only work if they're current."
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "Couldn't $what. Check your connection and try again."
                    else -> "Couldn't $what: ${e.localizedMessage ?: e.code.name}"
                }
            )
        }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
