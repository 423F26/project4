package com.example.liftingapp.data

/*
 * The app's data as the screens see it. These live in Cloud Firestore (see CloudPaths.kt for
 * where, and FirestoreMappers.kt for how they're read and written). Plain Kotlin, no Android or
 * Firebase calls, so the logic around them can be unit tested directly.
 */

/**
 * One athlete on a team's roster (or in someone's General space). Every logged set belongs to an
 * athlete. An athlete whose [id] equals an account's uid is that person's own profile: athletes
 * (non-coach accounts) can only add that one, coaches can add as many as they like.
 */
data class Athlete(
    val id: String,
    val name: String,
    val positionGroup: String,     // e.g. "OL", "WR", "DB"
    val bodyWeightLb: Float,       // needed for pound-for-pound rankings
    val phone: String? = null,     // optional; fills in the "To" field when a workout is texted
    val email: String? = null,     // optional; fills in the "To" field when a workout is emailed
    val ownerUid: String = "",     // the account that added this athlete
    val linkedUid: String? = null, // the account this athlete *is* (their own profile), if any
    /** True when the linked account hid its progress chart and workout log from teammates. */
    val hideProgress: Boolean = false,
    /** Best set per exercise (by estimated max). Kept up to date on every write so leaderboards are one read. */
    val bests: Map<String, BestSet> = emptyMap()
)

/** One logged set. [date] is when it was done (epoch millis). */
data class LiftEntry(
    val id: String = "",
    val athleteId: String,
    val exercise: String,          // "Bench", "Squat", "Deadlift", ...
    val weight: Float,             // pounds
    val reps: Int,
    val date: Long = System.currentTimeMillis()
)

/** An athlete's best set for one exercise, plus the numbers leaderboards sort by. */
data class BestSet(
    val weight: Float,
    val reps: Int,
    val e1rm: Float,               // estimated one-rep max, pounds
    val p4p: Float,                // e1rm / body weight
    val date: Long
)

/** The signed-in person's profile (users/{uid}). [role] is set by an admin in the Firebase console. */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: String = ROLE_ATHLETE,
    /**
     * The athlete details given at sign-up (all optional). Each team and General use gets an athlete
     * profile made from these automatically; editing them updates every one.
     */
    val positionGroup: String = "",
    val bodyWeightLb: Float = 0f,
    val phone: String? = null,
    val customExercises: List<String> = emptyList(),
    /** Privacy: appear on the General leaderboard at all. */
    val showOnGeneral: Boolean = true,
    /** Privacy: on the General leaderboard, show "J. S." instead of "Jordan Smith". */
    val initialsOnGeneral: Boolean = false,
    /** Privacy: other people can see this account's profile picture. */
    val showPhoto: Boolean = true,
    /** Privacy: teammates who aren't coaches can open this person's progress chart and workout log. */
    val shareProgress: Boolean = true
) {
    val isCoach: Boolean get() = role == ROLE_COACH

    companion object {
        const val ROLE_COACH = "coach"
        const val ROLE_ATHLETE = "athlete"
    }
}

/** Name, position, body weight and contact details for an athlete (the athlete dialogs and sign-up). */
data class AthleteDetails(
    val name: String,
    val positionGroup: String = "",   // optional, stored upper-case
    val bodyWeightLb: Float = 0f,     // optional; 0 = not set (pound-for-pound needs it)
    val phone: String? = null,
    val email: String? = null
)

fun Athlete.details(): AthleteDetails = AthleteDetails(name, positionGroup, bodyWeightLb, phone, email)

fun UserProfile.athleteDetails(): AthleteDetails = AthleteDetails(name, positionGroup, bodyWeightLb, phone, email.ifBlank { null })

/** A team the signed-in person belongs to, as listed on the team select screen. */
data class TeamMembership(
    val teamId: String,
    val teamName: String,
    val sport: String,
    val isOwner: Boolean,
    val joinedAt: Long
)

/** Someone on a team, as a coach sees them in the member list. */
data class TeamMember(
    val uid: String,
    val name: String,
    val role: String,
    val isOwner: Boolean,
    val joinedAt: Long
)

/** One exercise inside a logged workout, with its check-off state. */
data class SessionExercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val load: String = "",
    val notes: String = "",
    val done: Boolean = false,
    /** Sets logged from this workout, e.g. "225 × 5, 235 × 3". Blank until one is logged. */
    val result: String = ""
)

/**
 * A workout an athlete did (or plans to do) on a given day, made from a workout on the Workouts
 * tab. [day] is a local calendar day number (see localDay() in Progress.kt).
 */
data class WorkoutSession(
    val id: String = "",
    val athleteId: String,
    val workoutName: String,
    val notes: String = "",
    /** Where it came from, e.g. "Premade" or "Football Off-season, week 2". */
    val source: String = "",
    val day: Long,
    val exercises: List<SessionExercise>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    val doneCount: Int get() = exercises.count { it.done }
    val isComplete: Boolean get() = exercises.isNotEmpty() && doneCount == exercises.size
}

/** A row on the General leaderboard, written by the athlete's owner (generalBoard/{ownerUid}_{athleteId}). */
data class BoardEntry(
    val id: String,
    val ownerUid: String,
    val athleteId: String,
    val name: String,
    val positionGroup: String,
    val bodyWeightLb: Float,
    val photoUid: String?,
    val bests: Map<String, BestSet>
)
