package com.example.liftingapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The signed-in person's profile at users/{uid}: name, role (coach or athlete, set by an admin in
 * the Firebase console), privacy switches and custom exercises. Writes use merge so a profile that
 * failed to save at sign-up gets created on the first change instead of failing.
 */
class ProfileRepository(val uid: String) {

    private val auth = FirebaseAuth.getInstance()
    private val ref = CloudPaths.user(uid)

    private val fallbackName: String get() = auth.currentUser?.displayName.orEmpty()
    private val fallbackEmail: String get() = auth.currentUser?.email.orEmpty()

    fun profile(): Flow<UserProfile> = ref.changes().map { it.toProfile(uid, fallbackName, fallbackEmail) }

    /** Creates users/{uid} if sign-up didn't manage to (it lets people in even when that write fails). */
    suspend fun ensureProfile(): UserProfile {
        val snapshot = ref.get().awaitResult()
        if (!snapshot.exists()) {
            ref.set(
                mapOf(
                    "name" to fallbackName,
                    "email" to fallbackEmail,
                    "role" to UserProfile.ROLE_ATHLETE,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        }
        return snapshot.toProfile(uid, fallbackName, fallbackEmail)
    }

    /** Name plus the optional athlete details (position, body weight, phone). */
    suspend fun updateDetails(details: AthleteDetails) {
        ref.set(
            mapOf(
                "name" to details.name,
                "positionGroup" to details.positionGroup,
                "bodyWeightLb" to details.bodyWeightLb.toDouble(),
                "phone" to details.phone
            ),
            SetOptions.merge()
        ).awaitResult()
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(details.name).build())?.awaitResult()
    }

    /** One privacy switch: showOnGeneral, initialsOnGeneral, showPhoto or shareProgress. */
    suspend fun setFlag(field: String, value: Boolean) {
        ref.set(mapOf(field to value), SetOptions.merge()).awaitResult()
    }

    suspend fun addCustomExercise(name: String) {
        ref.set(mapOf("customExercises" to FieldValue.arrayUnion(name)), SetOptions.merge()).awaitResult()
    }

    suspend fun removeCustomExercise(name: String) {
        ref.set(mapOf("customExercises" to FieldValue.arrayRemove(name)), SetOptions.merge()).awaitResult()
    }

    companion object {
        const val FIELD_SHOW_ON_GENERAL = "showOnGeneral"
        const val FIELD_INITIALS_ON_GENERAL = "initialsOnGeneral"
        const val FIELD_SHOW_PHOTO = "showPhoto"
        const val FIELD_SHARE_PROGRESS = "shareProgress"
    }
}
