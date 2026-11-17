package com.example.liftingapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The account's own workouts ("Your workouts" on the Workouts tab), at users/{uid}/workouts/{id}.
 * They belong to the account, not the phone or a team: they show up on every team, in General use
 * and on any phone the person signs in on, until they delete them (or the account).
 *
 * Same document shape as a team workout (TeamWorkouts.kt), so iPhone reads them the same way.
 * A copy made from a team workout keeps that workout's id and has fromTeamId set.
 *
 * Premade workouts stay on the phone (Room, PremadeWorkouts.kt). Workouts made before 0.12 were
 * kept on the phone too; [importFromPhone] moves them into the first account that opens the app.
 */
object AccountWorkouts {

    /** Most recently saved first. */
    fun list(uid: String): Flow<List<TeamWorkout>> =
        CloudPaths.accountWorkouts(uid).changes().map { snapshot ->
            snapshot.documents.map { it.toTeamWorkout() }.sortedByDescending { it.updatedAt }
        }

    suspend fun get(uid: String, id: String): TeamWorkout? {
        val doc = CloudPaths.accountWorkouts(uid).document(id).get().awaitResult()
        return if (doc.exists()) doc.toTeamWorkout() else null
    }

    /** Saves a new ([id] null) or edited workout; returns its id. */
    suspend fun save(uid: String, id: String?, name: String, notes: String, exercises: List<WorkoutExercise>, userName: String): String {
        val collection = CloudPaths.accountWorkouts(uid)
        val ref = if (id == null) collection.document() else collection.document(id)
        saveWorkoutDoc(ref, isNew = id == null, name, notes, exercises, uid, userName)
        return ref.id
    }

    /** Writes [id] whether or not it exists yet (team workout copies, Undo, the phone import). */
    suspend fun saveCopy(
        uid: String, id: String, name: String, notes: String, exercises: List<WorkoutExercise>, userName: String,
        fromTeamId: String? = null, updatedAt: Long = System.currentTimeMillis()
    ) {
        val data = mutableMapOf<String, Any>(
            "name" to name.trim(),
            "notes" to notes.trim(),
            "exercises" to exercisesToMaps(exercises),
            "updatedAt" to updatedAt,
            "updatedBy" to uid,
            "createdBy" to uid,
            "createdByName" to userName
        )
        if (fromTeamId != null) data["fromTeamId"] = fromTeamId
        CloudPaths.accountWorkouts(uid).document(id).set(data, SetOptions.merge()).awaitResult()
    }

    suspend fun delete(uid: String, id: String) {
        CloudPaths.accountWorkouts(uid).document(id).delete().awaitResult()
    }

    /** Puts a deleted workout back with the same id (the Undo snackbar). */
    suspend fun restore(uid: String, workout: TeamWorkout) =
        saveCopy(uid, workout.id, workout.name, workout.notes, workout.exercises, workout.createdByName, updatedAt = workout.updatedAt)

    /**
     * Copies the team workouts this account made on its teams into its own workouts, once per team
     * (users/{uid}.workoutsCarriedFrom remembers which), so workouts made before this existed come
     * along too. Once copied, deleting the copy doesn't bring it back.
     */
    suspend fun carryFromTeams(uid: String, userName: String) {
        val done = (CloudPaths.user(uid).get().awaitResult().get("workoutsCarriedFrom") as? List<*>)
            .orEmpty().filterIsInstance<String>().toSet()
        CloudPaths.myTeams(uid).get().awaitResult().documents.map { it.id }.filter { it !in done }.forEach { teamId ->
            val mine = CloudPaths.teamWorkouts(teamId).whereEqualTo("createdBy", uid).get().awaitResult().documents
            mine.map { it.toTeamWorkout() }.forEach { w ->
                if (!CloudPaths.accountWorkouts(uid).document(w.id).get().awaitResult().exists()) {
                    saveCopy(uid, w.id, w.name, w.notes, w.exercises, w.createdByName.ifBlank { userName },
                             fromTeamId = teamId, updatedAt = w.updatedAt)
                }
            }
            CloudPaths.user(uid).set(mapOf("workoutsCarriedFrom" to FieldValue.arrayUnion(teamId)), SetOptions.merge()).awaitResult()
        }
    }

    /**
     * Moves workouts made on this phone before they were kept on the account (Room, not premade)
     * into [uid]'s workouts, then removes them from the phone. Ids are stable, so a retry after a
     * failure doesn't duplicate anything.
     */
    suspend fun importFromPhone(uid: String, userName: String, phone: WorkoutRepository, local: List<WorkoutWithExercises>) {
        local.filter { !it.workout.isPremade }.forEach { item ->
            val w = item.workout
            saveCopy(uid, "phone-${w.id}-${w.createdAt}", w.name, w.notes, item.exercises, userName, updatedAt = w.updatedAt)
            phone.delete(w)
        }
    }

    /** Every workout of [uid] (account deletion). */
    suspend fun deleteAll(uid: String) {
        CloudPaths.accountWorkouts(uid).get().awaitResult().documents.forEach { it.reference.delete().awaitResult() }
    }
}
