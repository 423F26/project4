package com.example.liftingapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.FirebaseTooManyRequestsException

/**
 * Proving you own your email. New email-and-password accounts get a verification link at sign-up
 * and can't go past the "Verify your email" screen (VerifyEmailActivity) until they've opened it.
 *  - Google sign-ins are already verified by Google.
 *  - Accounts made before [REQUIRED_SINCE_MS] (0.13) aren't locked out; they can keep using the app.
 * This check is in the app only. firestore.rules doesn't require request.auth.token.email_verified
 * yet, because that would lock out the older unverified accounts too.
 */
object EmailVerification {

    /** 2026-11-16 00:00 UTC. Email accounts created from then on must verify. */
    const val REQUIRED_SINCE_MS = 1_794_787_200_000L

    fun needsVerification(user: FirebaseUser?): Boolean {
        if (user == null || user.isEmailVerified) return false
        val usesPassword = user.providerData.any { it.providerId == "password" }
        val created = user.metadata?.creationTimestamp ?: 0L
        return usesPassword && created >= REQUIRED_SINCE_MS
    }

    /** Sends (or re-sends) the verification link. Returns a message to show. */
    suspend fun send(user: FirebaseUser): String = try {
        user.sendEmailVerification().awaitResult()
        "Verification email sent to ${user.email}"
    } catch (_: FirebaseTooManyRequestsException) {
        "Too many emails sent. Wait a few minutes, then try again."
    } catch (e: Exception) {
        e.localizedMessage ?: "Couldn't send the email. Check your connection."
    }

    /**
     * Asks Firebase whether the link was opened. On success also refreshes the sign-in token, so the
     * cloud sees email_verified = true right away. Returns true when verified.
     */
    suspend fun refresh(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        try {
            user.reload().awaitResult()
        } catch (_: FirebaseAuthException) {
            return false
        }
        val fresh = FirebaseAuth.getInstance().currentUser ?: return false
        if (!fresh.isEmailVerified) return false
        runCatching { fresh.getIdToken(true).awaitResult() }
        return true
    }
}
