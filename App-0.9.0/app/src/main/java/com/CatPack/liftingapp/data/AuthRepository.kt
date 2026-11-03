package com.example.liftingapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * All Firebase Auth calls live here so activities stay thin (same split as LiftRepository).
 * Callbacks: onSuccess gets the signed-in user, onError gets a message ready to show the user.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun signIn(email: String, password: String, onSuccess: (FirebaseUser) -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result -> result.user?.let(onSuccess) ?: onError("Sign-in failed. Try again.") }
            .addOnFailureListener { onError(friendlyMessage(it)) }
    }

    /** Creates the account, then writes a profile doc at users/{uid}. */
    fun signUp(name: String, email: String, password: String, onSuccess: (FirebaseUser) -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener onError("Sign-up failed. Try again.")
                val profile = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to "athlete",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(user.uid).set(profile)
                    .addOnSuccessListener { onSuccess(user) }
                    // The account exists even if the profile write fails, so let them in anyway.
                    .addOnFailureListener { onSuccess(user) }
            }
            .addOnFailureListener { onError(friendlyMessage(it)) }
    }

    /**
     * Signs in to Firebase with the ID token from Google's account picker. First-time Google users
     * get the same users/{uid} profile that email sign-up creates; returning users keep theirs.
     */
    fun signInWithGoogle(idToken: String, onSuccess: (FirebaseUser) -> Unit, onError: (String) -> Unit) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener onError("Google sign-in failed. Try again.")
                val doc = db.collection("users").document(user.uid)
                doc.get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) return@addOnSuccessListener onSuccess(user)
                        val profile = hashMapOf(
                            "name" to user.displayName.orEmpty(),
                            "email" to user.email.orEmpty(),
                            "role" to "athlete",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        doc.set(profile)
                            .addOnSuccessListener { onSuccess(user) }
                            .addOnFailureListener { onSuccess(user) }   // signed in either way, same as email sign-up
                    }
                    .addOnFailureListener { onSuccess(user) }
            }
            .addOnFailureListener { onError(friendlyMessage(it)) }
    }

    fun sendPasswordReset(email: String, onDone: (String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onDone("Reset link sent to $email") }
            .addOnFailureListener { onDone(friendlyMessage(it)) }
    }

    fun signOut() = auth.signOut()

    private fun friendlyMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
        is FirebaseAuthUserCollisionException -> "An account with that email already exists. Try signing in another way."
        is FirebaseAuthInvalidUserException -> "No account found with that email."
        is FirebaseAuthInvalidCredentialsException -> "Email or password is incorrect."
        else -> e.localizedMessage ?: "Something went wrong. Check your connection."
    }
}