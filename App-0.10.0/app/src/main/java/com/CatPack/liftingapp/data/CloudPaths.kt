package com.example.liftingapp.data

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Where everything lives in Cloud Firestore. firestore.rules (repo root) mirrors this layout;
 * change both together.
 *
 *   users/{uid}                                   profile, privacy settings, custom exercises
 *   users/{uid}/teams/{teamId}                    the teams this person is on (their list only)
 *   avatars/{uid}                                 profile picture (small JPEG, base64)
 *   teams/{teamId}                                name, sport, owner
 *   teams/{teamId}/members/{uid}                  who's on the team (the rules check this)
 *   teams/{teamId}/athletes/{athleteId}           the team's roster, with each athlete's bests
 *   teams/{teamId}/athletes/{athleteId}/lifts     logged sets
 *   teams/{teamId}/athletes/{athleteId}/sessions  logged workouts (with check-offs)
 *   teamSecrets/{teamId}                          the join code; only the team's creator can read it
 *   joinCodes/{code}                              code -> team, for joining (lookup by exact code only)
 *   general/{uid}/athletes/...                    same as a team's athletes, for General use
 *   generalBoard/{uid}_{athleteId}                public General leaderboard rows
 */
object CloudPaths {
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun user(uid: String): DocumentReference = db.collection("users").document(uid)
    fun myTeams(uid: String): CollectionReference = user(uid).collection("teams")
    fun avatar(uid: String): DocumentReference = db.collection("avatars").document(uid)

    fun teams(): CollectionReference = db.collection("teams")
    fun team(teamId: String): DocumentReference = teams().document(teamId)
    fun members(teamId: String): CollectionReference = team(teamId).collection("members")
    fun teamSecret(teamId: String): DocumentReference = db.collection("teamSecrets").document(teamId)
    fun joinCode(code: String): DocumentReference = db.collection("joinCodes").document(code)

    fun board(): CollectionReference = db.collection("generalBoard")
    fun boardEntry(ownerUid: String, athleteId: String): DocumentReference = board().document("${ownerUid}_$athleteId")

    /** The document a scope's athletes hang off: the team, or general/{uid}. */
    fun root(scope: Scope, uid: String): DocumentReference = when (scope) {
        is Scope.Team -> team(scope.id)
        Scope.General -> db.collection("general").document(uid)
    }

    fun athletes(scope: Scope, uid: String): CollectionReference = root(scope, uid).collection("athletes")
    fun athlete(scope: Scope, uid: String, athleteId: String): DocumentReference = athletes(scope, uid).document(athleteId)
    fun lifts(scope: Scope, uid: String, athleteId: String): CollectionReference = athlete(scope, uid, athleteId).collection("lifts")
    fun sessions(scope: Scope, uid: String, athleteId: String): CollectionReference =
        athlete(scope, uid, athleteId).collection("sessions")
}
