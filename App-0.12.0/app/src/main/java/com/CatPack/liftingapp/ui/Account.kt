package com.example.liftingapp.ui

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.LoginActivity
import com.example.liftingapp.data.AdminAccounts
import com.example.liftingapp.data.AuthRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** "Sign out?" then signs out of Firebase, forgets the Google account choice and goes to the login screen. */
fun AppCompatActivity.confirmSignOut() {
    val auth = AuthRepository()
    MaterialAlertDialogBuilder(this)
        .setTitle("Sign out?")
        .setMessage(auth.currentUser?.email?.let { "You're signed in as $it." })
        .setPositiveButton("Sign out") { _, _ -> signOutNow() }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * If a CatPack admin deleted this account, finishes removing it, says so and goes to the login
 * screen. Called when the app's first screens open. Does nothing for everyone else.
 */
fun AppCompatActivity.signOutIfRemoved() {
    lifecycleScope.launch {
        if (AdminAccounts.finishIfRemoved()) {
            Toast.makeText(this@signOutIfRemoved, "This account was deleted by a CatPack admin.", Toast.LENGTH_LONG).show()
            signOutNow()
        }
    }
}

/** Signs out without asking (e.g. after deleting the account). */
fun AppCompatActivity.signOutNow() {
    AuthRepository().signOut()
    lifecycleScope.launch {
        // Forget the Google account choice so the picker shows again next time.
        runCatching { CredentialManager.create(this@signOutNow).clearCredentialState(ClearCredentialStateRequest()) }
        startActivity(Intent(this@signOutNow, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
