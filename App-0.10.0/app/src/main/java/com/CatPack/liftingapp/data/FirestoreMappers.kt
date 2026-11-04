package com.example.liftingapp.data

import com.google.firebase.firestore.DocumentSnapshot

/*
 * Firestore <-> model conversions, written out by hand (no reflection) so the field names are
 * easy to find and missing or old fields fall back to sensible defaults.
 */

// --- Athletes ---

fun DocumentSnapshot.toAthlete(): Athlete = Athlete(
    id = id,
    name = getString("name").orEmpty(),
    positionGroup = getString("positionGroup").orEmpty(),
    bodyWeightLb = get("bodyWeightLb").asFloat(),
    phone = get("phone").asStringOrNull(),
    email = get("email").asStringOrNull(),
    ownerUid = getString("ownerUid").orEmpty(),
    linkedUid = get("linkedUid").asStringOrNull(),
    hideProgress = get("hideProgress").asBool(false),
    bests = get("bests").toBests()
)

/** Everything except [Athlete.id] (that's the document id). */
fun Athlete.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "positionGroup" to positionGroup,
    "bodyWeightLb" to bodyWeightLb.toDouble(),
    "phone" to phone,
    "email" to email,
    "ownerUid" to ownerUid,
    "linkedUid" to linkedUid,
    "hideProgress" to hideProgress,
    "bests" to bests.mapValues { it.value.toMap() }
)

fun BestSet.toMap(): Map<String, Any> = mapOf(
    "weight" to weight.toDouble(),
    "reps" to reps.toLong(),
    "e1rm" to e1rm.toDouble(),
    "p4p" to p4p.toDouble(),
    "date" to date
)

fun Any?.toBestSet(): BestSet? {
    val map = this as? Map<*, *> ?: return null
    val reps = map["reps"].asInt()
    if (reps < 1) return null
    return BestSet(
        weight = map["weight"].asFloat(),
        reps = reps,
        e1rm = map["e1rm"].asFloat(),
        p4p = map["p4p"].asFloat(),
        date = map["date"].asLong()
    )
}

fun Any?.toBests(): Map<String, BestSet> {
    val map = this as? Map<*, *> ?: return emptyMap()
    return map.entries.mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        value.toBestSet()?.let { name to it }
    }.toMap()
}

// --- Lifts ---

fun DocumentSnapshot.toLift(athleteId: String): LiftEntry? {
    val reps = get("reps").asInt()
    val exercise = getString("exercise") ?: return null
    if (reps < 1) return null
    return LiftEntry(
        id = id,
        athleteId = athleteId,
        exercise = exercise,
        weight = get("weight").asFloat(),
        reps = reps,
        date = get("date").asLong()
    )
}

fun LiftEntry.toMap(loggedBy: String): Map<String, Any> = mapOf(
    "exercise" to exercise,
    "weight" to weight.toDouble(),
    "reps" to reps.toLong(),
    "date" to date,
    "loggedBy" to loggedBy
)

// --- Workout log ---

fun DocumentSnapshot.toSession(athleteId: String): WorkoutSession {
    val exercises = (get("exercises") as? List<*>).orEmpty().mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        SessionExercise(
            name = (map["name"] as? String).orEmpty(),
            sets = map["sets"].asInt(1),
            reps = (map["reps"] as? String).orEmpty(),
            load = (map["load"] as? String).orEmpty(),
            notes = (map["notes"] as? String).orEmpty(),
            done = map["done"].asBool(false),
            result = (map["result"] as? String).orEmpty()
        )
    }
    val created = get("createdAt").asLong()
    return WorkoutSession(
        id = id,
        athleteId = athleteId,
        workoutName = getString("workoutName").orEmpty(),
        notes = getString("notes").orEmpty(),
        source = getString("source").orEmpty(),
        day = get("day").asLong(),
        exercises = exercises,
        createdAt = created,
        updatedAt = get("updatedAt").asLong(created)
    )
}

fun WorkoutSession.toMap(createdBy: String): Map<String, Any> = mapOf(
    "workoutName" to workoutName,
    "notes" to notes,
    "source" to source,
    "day" to day,
    "exercises" to exercises.map { it.toMap() },
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "createdBy" to createdBy
)

fun SessionExercise.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "sets" to sets.toLong(),
    "reps" to reps,
    "load" to load,
    "notes" to notes,
    "done" to done,
    "result" to result
)

// --- People and teams ---

fun DocumentSnapshot.toProfile(uid: String, fallbackName: String, fallbackEmail: String): UserProfile {
    val custom = (get("customExercises") as? List<*>).orEmpty().filterIsInstance<String>()
    return UserProfile(
        uid = uid,
        name = getString("name")?.takeIf { it.isNotBlank() } ?: fallbackName,
        email = getString("email")?.takeIf { it.isNotBlank() } ?: fallbackEmail,
        role = getString("role") ?: UserProfile.ROLE_ATHLETE,
        positionGroup = getString("positionGroup").orEmpty(),
        bodyWeightLb = get("bodyWeightLb").asFloat(),
        phone = get("phone").asStringOrNull(),
        customExercises = custom,
        showOnGeneral = get("showOnGeneral").asBool(true),
        initialsOnGeneral = get("initialsOnGeneral").asBool(false),
        showPhoto = get("showPhoto").asBool(true),
        shareProgress = get("shareProgress").asBool(true)
    )
}

fun DocumentSnapshot.toMembership(): TeamMembership = TeamMembership(
    teamId = id,
    teamName = getString("teamName").orEmpty().ifEmpty { "Team" },
    sport = getString("sport").orEmpty(),
    isOwner = get("owner").asBool(false),
    joinedAt = get("joinedAt").asLong()
)

fun DocumentSnapshot.toMember(): TeamMember = TeamMember(
    uid = id,
    name = getString("name").orEmpty().ifEmpty { "Team member" },
    role = getString("role") ?: UserProfile.ROLE_ATHLETE,
    isOwner = get("owner").asBool(false),
    joinedAt = get("joinedAt").asLong()
)

fun DocumentSnapshot.toBoardEntry(): BoardEntry = BoardEntry(
    id = id,
    ownerUid = getString("ownerUid").orEmpty(),
    athleteId = getString("athleteId").orEmpty(),
    name = getString("name").orEmpty().ifEmpty { "Athlete" },
    positionGroup = getString("positionGroup").orEmpty(),
    bodyWeightLb = get("bodyWeightLb").asFloat(),
    photoUid = get("photoUid").asStringOrNull(),
    bests = get("bests").toBests()
)
