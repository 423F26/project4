package com.example.liftingapp.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The roster, logged sets and workout log for one scope (a team, or the signed-in person's General
 * space). Lives in Cloud Firestore, so everyone on a team sees the same data.
 *
 * Writes don't wait for the server: Firestore applies them to its local cache at once (the screen
 * updates immediately, even with no signal) and syncs them when it can. Failures, such as a
 * permission the security rules refuse, are reported through [CloudErrors].
 *
 * Every set that's saved or deleted updates the athlete's stored best for that exercise (see
 * [Athlete.bests]), so leaderboards need one read per athlete instead of every set.
 */
class LiftRepository(val scope: Scope, val uid: String) {

    /** The signed-in person's privacy settings, for General leaderboard rows. Kept current by the ViewModel. */
    @Volatile var profile: UserProfile? = null

    /** One best-set update at a time, so two quick sets can't overwrite each other's result. */
    private val bestsLock = Mutex()

    // --- Reads ---

    /** The roster, A to Z. In General that's only this person's athletes. */
    fun athletes(): Flow<List<Athlete>> =
        CloudPaths.athletes(scope, uid).changes().map { snapshot ->
            snapshot.documents.map { it.toAthlete() }.sortedBy { it.name.lowercase() }
        }

    /** One athlete's sets for one exercise, oldest first (sorted here so no composite index is needed). */
    fun lifts(athleteId: String, exercise: String): Flow<List<LiftEntry>> =
        CloudPaths.lifts(scope, uid, athleteId).whereEqualTo("exercise", exercise).changes().map { snapshot ->
            snapshot.documents.mapNotNull { it.toLift(athleteId) }.sortedBy { it.date }
        }

    /** One athlete's logged workouts (sorting and grouping happen in WorkoutLog.kt). */
    fun sessions(athleteId: String): Flow<List<WorkoutSession>> =
        CloudPaths.sessions(scope, uid, athleteId).changes().map { snapshot ->
            snapshot.documents.map { it.toSession(athleteId) }
        }

    fun session(athleteId: String, sessionId: String): Flow<WorkoutSession?> =
        CloudPaths.sessions(scope, uid, athleteId).document(sessionId).changes().map { snapshot ->
            if (snapshot.exists()) snapshot.toSession(athleteId) else null
        }

    fun athlete(athleteId: String): Flow<Athlete?> =
        CloudPaths.athlete(scope, uid, athleteId).changes().map { if (it.exists()) it.toAthlete() else null }

    // --- Athletes ---

    /**
     * The id for a new athlete. An athlete account's own profile uses their uid (that's how the
     * security rules limit them to one); coaches' athletes get a random id.
     */
    fun newAthleteId(isOwnProfile: Boolean): String =
        if (isOwnProfile) uid else CloudPaths.athletes(scope, uid).document().id

    fun addAthlete(athlete: Athlete) {
        CloudPaths.athlete(scope, uid, athlete.id).set(athlete.toMap()).orReport("add ${athlete.name}")
        syncBoardEntry(athlete.id)
    }

    /**
     * Saves name, position, body weight and contact details. Body weight changes every
     * pound-for-pound number, so the stored bests are recalculated with it.
     */
    fun updateAthlete(athlete: Athlete) {
        val bests = athlete.bests.mapValues { (_, best) ->
            best.copy(p4p = Strength.poundForPound(best.e1rm, athlete.bodyWeightLb)).toMap()
        }
        CloudPaths.athlete(scope, uid, athlete.id).update(
            mapOf(
                "name" to athlete.name,
                "positionGroup" to athlete.positionGroup,
                "bodyWeightLb" to athlete.bodyWeightLb.toDouble(),
                "phone" to athlete.phone,
                "email" to athlete.email,
                "bests" to bests
            )
        ).orReport("save ${athlete.name}")
        syncBoardEntry(athlete.id)
    }

    /** Deletes the athlete with every set and logged workout. Can't be undone. */
    suspend fun deleteAthlete(athleteId: String) {
        val ref = CloudPaths.athlete(scope, uid, athleteId)
        val children = CloudPaths.lifts(scope, uid, athleteId).get().awaitResult().documents.map { it.reference } +
            CloudPaths.sessions(scope, uid, athleteId).get().awaitResult().documents.map { it.reference }
        deleteAll(children + ref)
        if (scope == Scope.General) CloudPaths.boardEntry(uid, athleteId).delete().orReport("update the General leaderboard")
    }

    /**
     * Makes this person's own athlete profile here from their account details, unless it already
     * exists. Called when a team or General use is opened, so accounts never have to add themselves.
     * Checks the server first (when online) so an existing profile is never overwritten; if that
     * can't be checked right now it does nothing and tries again next time.
     * Returns true if it made one.
     */
    suspend fun ensureOwnAthlete(profile: UserProfile): Boolean {
        val snapshot = CloudPaths.athlete(scope, uid, uid).get().awaitResult()
        if (snapshot.exists()) return false
        if (AdminAccounts.isRemoved(uid)) return false   // deleted by an admin; signed out in a moment
        val details = profile.athleteDetails()
        addAthlete(
            Athlete(
                id = uid,
                name = details.name.ifBlank { profile.email.substringBefore('@').ifBlank { "Me" } },
                positionGroup = details.positionGroup,
                bodyWeightLb = details.bodyWeightLb,
                phone = details.phone,
                email = details.email,
                ownerUid = uid,
                linkedUid = uid,
                hideProgress = !profile.shareProgress
            )
        )
        return true
    }

    /**
     * Copies name, position, body weight and phone onto this person's own athlete here, if there is
     * one (they're one person on every team). Contact email stays as it is.
     */
    suspend fun updateOwnDetails(details: AthleteDetails) {
        val snapshot = CloudPaths.athlete(scope, uid, uid).get().awaitResult()
        if (!snapshot.exists()) return
        val athlete = snapshot.toAthlete()
        updateAthlete(
            athlete.copy(
                name = details.name,
                positionGroup = details.positionGroup,
                bodyWeightLb = details.bodyWeightLb,
                phone = details.phone
            )
        )
    }

    /** Sets the "hide my progress from teammates" flag on this person's own athlete, if they have one here. */
    suspend fun setOwnProgressHidden(hidden: Boolean) {
        val ref = CloudPaths.athlete(scope, uid, uid)
        if (ref.get().awaitResult().exists()) ref.update("hideProgress", hidden).orReport("save your privacy setting")
    }

    // --- Sets ---

    /**
     * Adds a set, saves a corrected one ([previous] = the set before the change), or puts a deleted
     * one back with its old id and date. Returns the set's id.
     */
    fun saveLift(entry: LiftEntry, previous: LiftEntry? = null): String {
        val lifts = CloudPaths.lifts(scope, uid, entry.athleteId)
        val ref = if (entry.id.isEmpty()) lifts.document() else lifts.document(entry.id)
        ref.set(entry.toMap(loggedBy = uid)).orReport("save the set")
        val saved = entry.copy(id = ref.id)
        inBackground("update the best set") {
            if (previous == null) raiseBest(saved) else refreshBest(entry.athleteId, entry.exercise)
        }
        return ref.id
    }

    fun deleteLift(entry: LiftEntry) {
        CloudPaths.lifts(scope, uid, entry.athleteId).document(entry.id).delete().orReport("delete the set")
        inBackground("update the best set") { refreshBest(entry.athleteId, entry.exercise) }
    }

    /** A new set can only raise the best, so it's compared with the stored best instead of re-reading every set. */
    private suspend fun raiseBest(entry: LiftEntry) = bestsLock.withLock {
        val athleteRef = CloudPaths.athlete(scope, uid, entry.athleteId)
        val snapshot = athleteRef.get().awaitResult()
        if (!snapshot.exists()) return@withLock
        val athlete = snapshot.toAthlete()
        val candidate = Strength.bestOf(listOf(entry), athlete.bodyWeightLb) ?: return@withLock
        val current = athlete.bests[entry.exercise]
        if (current == null || candidate.e1rm > current.e1rm) {
            athleteRef.update(FieldPath.of("bests", entry.exercise), candidate.toMap()).orReport("update the best set")
            syncBoardEntry(entry.athleteId)
        }
    }

    /** Recalculates the athlete's best [exercise] set from all their sets and stores it (or clears it). */
    suspend fun refreshBest(athleteId: String, exercise: String) = bestsLock.withLock {
        val athleteRef = CloudPaths.athlete(scope, uid, athleteId)
        val snapshot = athleteRef.get().awaitResult()
        if (!snapshot.exists()) return@withLock
        val sets = CloudPaths.lifts(scope, uid, athleteId).whereEqualTo("exercise", exercise).get().awaitResult()
            .documents.mapNotNull { it.toLift(athleteId) }
        val best = Strength.bestOf(sets, snapshot.get("bodyWeightLb").asFloat())
        athleteRef.update(FieldPath.of("bests", exercise), best?.toMap() ?: FieldValue.delete()).orReport("update the best set")
        syncBoardEntry(athleteId)
    }

    // --- Workout log ---

    /** Returns the new log entry's id. */
    fun addSession(session: WorkoutSession): String {
        val ref = CloudPaths.sessions(scope, uid, session.athleteId).document()
        ref.set(session.toMap(createdBy = uid)).orReport("log the workout")
        return ref.id
    }

    /** Saves check-offs, results, notes and the date. */
    fun saveSession(session: WorkoutSession) {
        CloudPaths.sessions(scope, uid, session.athleteId).document(session.id).update(
            mapOf(
                "exercises" to session.exercises.map { it.toMap() },
                "day" to session.day,
                "notes" to session.notes,
                "updatedAt" to System.currentTimeMillis()
            )
        ).orReport("save the workout")
    }

    /** Puts a deleted log entry back exactly as it was (Undo). */
    fun restoreSession(session: WorkoutSession) {
        CloudPaths.sessions(scope, uid, session.athleteId).document(session.id)
            .set(session.toMap(createdBy = uid)).orReport("restore the workout")
    }

    fun deleteSession(session: WorkoutSession) {
        CloudPaths.sessions(scope, uid, session.athleteId).document(session.id).delete().orReport("delete the workout")
    }

    // --- General leaderboard ---

    /** In General, publishes (or withdraws) the athlete's leaderboard row to match the privacy settings. */
    private fun syncBoardEntry(athleteId: String) {
        if (scope != Scope.General) return
        val settings = profile
        inBackground("update the General leaderboard") { GeneralBoard.syncOne(uid, athleteId, settings) }
    }

    private suspend fun deleteAll(refs: List<DocumentReference>) {
        refs.chunked(MAX_BATCH).forEach { chunk ->
            val batch = CloudPaths.db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().awaitResult()
        }
    }

    companion object {
        const val MAX_BATCH = 450   // Firestore allows 500 writes per batch
    }
}
