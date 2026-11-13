package com.example.liftingapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * Team workouts and in-app sending, shared through Firestore so every phone on the team sees them
 * (Android and iPhone use the same documents):
 *
 *   teams/{teamId}/workouts/{id}  a workout anyone on the team made; everyone on the team sees it.
 *                                 Its maker or a coach can edit or delete it.
 *   teams/{teamId}/sent/{id}      a workout sent in the app, to the whole team (to = []) or to
 *                                 one teammate's account (to = [uid]). Shown on the Workouts tab.
 *
 * Exercises are stored inline as maps: name, sets, reps, load, notes (same as a logged workout).
 */

/** A workout on a team's shared list. */
data class TeamWorkout(
    val id: String,
    val name: String,
    val notes: String,
    val exercises: List<WorkoutExercise>,
    val createdBy: String,
    val createdByName: String,
    val updatedAt: Long,
    /** Account workouts only: the team it was first made on, if it's a copy of a team workout. */
    val fromTeamId: String? = null
) {
    /** As the Workouts tab's cards, the Send dialog and the workout log expect it. */
    fun asItem(): WorkoutWithExercises =
        WorkoutWithExercises(Workout(name = name, notes = notes, createdAt = updatedAt, updatedAt = updatedAt), exercises)
}

/** A workout someone sent in the app. */
data class SentWorkout(
    val id: String,
    val name: String,
    val notes: String,
    val exercises: List<WorkoutExercise>,
    val fromUid: String,
    val fromName: String,
    /** Account uids it's for; empty = the whole team. */
    val to: List<String>,
    val toName: String,
    val sentAt: Long
) {
    fun asItem(): WorkoutWithExercises =
        WorkoutWithExercises(Workout(name = name, notes = notes, createdAt = sentAt, updatedAt = sentAt), exercises)

    fun isFor(uid: String): Boolean = to.isEmpty() || uid in to || fromUid == uid
}

internal fun exercisesToMaps(exercises: List<WorkoutExercise>): List<Map<String, Any>> =
    exercises.mapIndexed { i, e ->
        mapOf("name" to e.name.trim(), "sets" to e.sets.toLong(), "reps" to e.reps.trim(),
              "load" to e.load.trim(), "notes" to e.notes.trim(), "position" to i.toLong())
    }

private fun DocumentSnapshot.exercises(): List<WorkoutExercise> =
    (get("exercises") as? List<*>).orEmpty().mapIndexedNotNull { i, raw ->
        val m = raw as? Map<*, *> ?: return@mapIndexedNotNull null
        WorkoutExercise(
            position = i,
            name = (m["name"] as? String).orEmpty(),
            sets = m["sets"].asInt(1),
            reps = (m["reps"] as? String).orEmpty(),
            load = (m["load"] as? String).orEmpty(),
            notes = (m["notes"] as? String).orEmpty()
        )
    }

fun DocumentSnapshot.toTeamWorkout() = TeamWorkout(
    id = id,
    name = getString("name").orEmpty(),
    notes = getString("notes").orEmpty(),
    exercises = exercises(),
    createdBy = getString("createdBy").orEmpty(),
    createdByName = getString("createdByName").orEmpty(),
    updatedAt = get("updatedAt").asLong(),
    fromTeamId = getString("fromTeamId")
)

fun DocumentSnapshot.toSentWorkout() = SentWorkout(
    id = id,
    name = getString("name").orEmpty(),
    notes = getString("notes").orEmpty(),
    exercises = exercises(),
    fromUid = getString("fromUid").orEmpty(),
    fromName = getString("fromName").orEmpty(),
    to = (get("to") as? List<*>).orEmpty().filterIsInstance<String>(),
    toName = getString("toName").orEmpty(),
    sentAt = get("sentAt").asLong()
)

/** Writes a workout doc (team or account): set with the maker when new, update otherwise. */
internal suspend fun saveWorkoutDoc(
    ref: com.google.firebase.firestore.DocumentReference, isNew: Boolean,
    name: String, notes: String, exercises: List<WorkoutExercise>, uid: String, userName: String,
    extra: Map<String, Any> = emptyMap()
) {
    val now = System.currentTimeMillis()
    val data = mutableMapOf<String, Any>(
        "name" to name.trim(),
        "notes" to notes.trim(),
        "exercises" to exercisesToMaps(exercises),
        "updatedAt" to now,
        "updatedBy" to uid
    ) + extra
    if (isNew) {
        ref.set(data + mapOf("createdBy" to uid, "createdByName" to userName, "createdAt" to now)).awaitResult()
    } else {
        ref.update(data).awaitResult()
    }
}

object TeamWorkouts {
    /** The team's shared workouts, most recently saved first. */
    fun list(teamId: String): Flow<List<TeamWorkout>> =
        CloudPaths.teamWorkouts(teamId).changes().map { snapshot ->
            snapshot.documents.map { it.toTeamWorkout() }.sortedByDescending { it.updatedAt }
        }

    suspend fun get(teamId: String, id: String): TeamWorkout? {
        val doc = CloudPaths.teamWorkouts(teamId).document(id).get().awaitResult()
        return if (doc.exists()) doc.toTeamWorkout() else null
    }

    /**
     * Saves a new ([id] null) or edited workout; returns its id. The maker stays the maker on edits.
     * A workout this account made also goes into the account's own workouts (same id), so it comes
     * along to every other team and General use, and stays after leaving this team.
     */
    suspend fun save(
        teamId: String, id: String?, name: String, notes: String, exercises: List<WorkoutExercise>,
        uid: String, userName: String
    ): String {
        val ref = if (id == null) CloudPaths.teamWorkouts(teamId).document() else CloudPaths.teamWorkouts(teamId).document(id)
        saveWorkoutDoc(ref, isNew = id == null, name, notes, exercises, uid, userName)
        val createdBy = if (id == null) uid else get(teamId, ref.id)?.createdBy
        if (createdBy == uid) {
            runCatching { AccountWorkouts.saveCopy(uid, ref.id, name, notes, exercises, userName, fromTeamId = teamId) }
                .onFailure { CloudErrors.report("add it to your workouts", it) }
        }
        return ref.id
    }

    suspend fun delete(teamId: String, id: String) {
        CloudPaths.teamWorkouts(teamId).document(id).delete().awaitResult()
    }
}

object TeamInbox {
    /** Everything sent on the team that's for [uid] (or the whole team, or that [uid] sent), newest first. */
    fun list(teamId: String, uid: String): Flow<List<SentWorkout>> =
        CloudPaths.teamSent(teamId).changes().map { snapshot ->
            snapshot.documents.map { it.toSentWorkout() }.filter { it.isFor(uid) }.sortedByDescending { it.sentAt }
        }

    /** Sends [item] in the app to [toUid]'s account, or to the whole team when null. */
    suspend fun send(teamId: String, item: WorkoutWithExercises, fromUid: String, fromName: String, toUid: String?, toName: String) {
        CloudPaths.teamSent(teamId).document().set(
            mapOf(
                "name" to item.workout.name.trim(),
                "notes" to item.workout.notes.trim(),
                "exercises" to exercisesToMaps(item.exercises.sortedBy { it.position }),
                "fromUid" to fromUid,
                "fromName" to fromName,
                "to" to listOfNotNull(toUid),
                "toName" to toName,
                "sentAt" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).awaitResult()
    }

    suspend fun delete(teamId: String, id: String) {
        CloudPaths.teamSent(teamId).document(id).delete().awaitResult()
    }
}
