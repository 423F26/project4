package com.example.liftingapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.data.AuthRepository
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.isValidPhone
import com.example.liftingapp.data.orNullIfBlank
import com.example.liftingapp.databinding.ActivityLoginBinding
import com.example.liftingapp.ui.AppTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.example.liftingapp.data.UserProfile
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Launcher screen. Already signed in -> back to the last team (or the team select screen if none
 * was picked yet, or if Account & settings asks to choose every time). Otherwise one form that toggles
 * between "Sign in" and "Create account", plus "Continue with Google". Signing in always leads to
 * the team select screen.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = AuthRepository()
    private var isSignUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)

        val signedIn = auth.currentUser
        if (signedIn != null) {
            val lastScope = ScopeStore.load(this, signedIn.uid)
            if (lastScope != null && !ScopeStore.askEveryLaunch(this)) openMain() else openTeamSelect()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSignUp = savedInstanceState?.getBoolean(KEY_SIGN_UP) ?: false
        updateMode()

        binding.submitButton.setOnClickListener { submit() }
        binding.googleButton.setOnClickListener { signInWithGoogle() }
        binding.roleToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) binding.coachNote.visibility = if (checkedId == R.id.roleCoach) View.VISIBLE else View.GONE
        }
        binding.toggleModeButton.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }
        binding.forgotPasswordButton.setOnClickListener { resetPassword() }
    }

    private fun updateMode() {
        binding.nameLayout.visibility = if (isSignUp) View.VISIBLE else View.GONE
        binding.signUpExtras.visibility = if (isSignUp) View.VISIBLE else View.GONE
        binding.roleSection.visibility = if (isSignUp) View.VISIBLE else View.GONE
        binding.forgotPasswordButton.visibility = if (isSignUp) View.GONE else View.VISIBLE
        binding.submitButton.text = if (isSignUp) "Create account" else "Sign in"
        binding.toggleModeButton.text =
            if (isSignUp) "Already have an account? Sign in" else "New here? Create an account"
        clearErrors()
    }

    private fun submit() {
        clearErrors()
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        // Optional athlete details (create account only).
        val position = binding.positionInput.text.toString().trim().uppercase()
        val phone = binding.phoneInput.text.toString().trim()
        val weightText = binding.bodyWeightInput.text.toString().trim()
        val bodyWeight = if (weightText.isEmpty()) 0f else weightText.toFloatOrNull()?.takeIf { it > 0f }

        var valid = true
        if (isSignUp && name.isEmpty()) { binding.nameLayout.error = "Enter your name"; valid = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.emailLayout.error = "Enter a valid email"; valid = false }
        if (password.length < 6) { binding.passwordLayout.error = "At least 6 characters"; valid = false }
        if (isSignUp && bodyWeight == null) { binding.bodyWeightLayout.error = "Enter pounds, e.g. 185"; valid = false }
        if (isSignUp && !isValidPhone(phone)) { binding.phoneLayout.error = "Enter a phone number, e.g. 406-555-0100"; valid = false }
        if (!valid) return

        setLoading(true)
        val onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit = { openTeamSelect() }
        val onError: (String) -> Unit = { message ->
            setLoading(false)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }

        val requestedRole = if (binding.roleToggle.checkedButtonId == R.id.roleCoach) UserProfile.ROLE_COACH else UserProfile.ROLE_ATHLETE
        if (isSignUp) auth.signUp(name, email, password, position, phone.orNullIfBlank(), bodyWeight ?: 0f, requestedRole, onSuccess, onError)
        else auth.signIn(email, password, onSuccess, onError)
    }

    /** Shows Google's account picker, then signs in to Firebase with the chosen account. */
    private fun signInWithGoogle() {
        val webClientId = getString(R.string.google_web_client_id)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val credential = CredentialManager.create(this@LoginActivity)
                    .getCredential(this@LoginActivity, request).credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    auth.signInWithGoogle(idToken, onSuccess = { openTeamSelect() }, onError = ::showError)
                } else {
                    showError("Google sign-in failed. Try again.")
                }
            } catch (e: GetCredentialCancellationException) {
                setLoading(false)   // user closed the account picker; nothing to report
            } catch (e: NoCredentialException) {
                showError("No Google account on this phone. Add one in Settings → Passwords & accounts, then try again.")
            } catch (e: GetCredentialException) {
                val detail = e.message.orEmpty()
                // 28444 / 10 / "Developer console": this build's signing key (SHA-1) isn't registered in
                // Firebase for com.CatPack.liftingapp. See README → Google sign-in.
                val notSetUp = "28444" in detail || "eveloper console" in detail || "[10]" in detail
                showError(
                    if (notSetUp) "Google sign-in isn't set up for this build yet (its SHA-1 isn't in Firebase). See README → Google sign-in."
                    else "Google sign-in failed: ${detail.ifBlank { "no Google account available" }}"
                )
            } catch (e: GoogleIdTokenParsingException) {
                showError("Google sign-in failed. Try again.")
            }
        }
    }

    private fun showError(message: String) {
        setLoading(false)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun resetPassword() {
        val email = binding.emailInput.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Enter your email first"
            return
        }
        auth.sendPasswordReset(email) { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.submitButton.isEnabled = !loading
        binding.googleButton.isEnabled = !loading
        binding.toggleModeButton.isEnabled = !loading
    }

    private fun clearErrors() {
        binding.nameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.bodyWeightLayout.error = null
        binding.phoneLayout.error = null
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()   // so Back from MainActivity doesn't return to the login screen
    }

    /** After signing in: pick a team (or General use) first. */
    private fun openTeamSelect() {
        startActivity(TeamSelectActivity.intent(this, switching = false))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SIGN_UP, isSignUp)
    }

    private companion object {
        const val KEY_SIGN_UP = "is_sign_up"
    }
}