package com.example.liftingapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.liftingapp.data.AuthRepository
import com.example.liftingapp.databinding.ActivityLoginBinding
import com.example.liftingapp.ui.AppTheme
import com.google.android.material.snackbar.Snackbar

/**
 * Launcher screen. Already signed in -> straight to MainActivity.
 * Otherwise one form that toggles between "Sign in" and "Create account".
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = AuthRepository()
    private var isSignUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            openMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSignUp = savedInstanceState?.getBoolean(KEY_SIGN_UP) ?: false
        updateMode()

        binding.submitButton.setOnClickListener { submit() }
        binding.toggleModeButton.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }
        binding.forgotPasswordButton.setOnClickListener { resetPassword() }
    }

    private fun updateMode() {
        binding.nameLayout.visibility = if (isSignUp) View.VISIBLE else View.GONE
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

        var valid = true
        if (isSignUp && name.isEmpty()) { binding.nameLayout.error = "Enter your name"; valid = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.emailLayout.error = "Enter a valid email"; valid = false }
        if (password.length < 6) { binding.passwordLayout.error = "At least 6 characters"; valid = false }
        if (!valid) return

        setLoading(true)
        val onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit = { openMain() }
        val onError: (String) -> Unit = { message ->
            setLoading(false)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }

        if (isSignUp) auth.signUp(name, email, password, onSuccess, onError)
        else auth.signIn(email, password, onSuccess, onError)
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
        binding.toggleModeButton.isEnabled = !loading
    }

    private fun clearErrors() {
        binding.nameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()   // so Back from MainActivity doesn't return to the login screen
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SIGN_UP, isSignUp)
    }

    private companion object {
        const val KEY_SIGN_UP = "is_sign_up"
    }
}