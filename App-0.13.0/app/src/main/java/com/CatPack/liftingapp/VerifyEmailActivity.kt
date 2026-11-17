package com.example.liftingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.data.EmailVerification
import com.example.liftingapp.databinding.ActivityVerifyEmailBinding
import com.example.liftingapp.ui.AppTheme
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Verify your email": new email-and-password accounts land here after signing up or in, until they
 * open the link Firebase emailed them (see data/EmailVerification.kt). "I've verified" checks with
 * Firebase and moves on to the team select screen; "Resend email" sends another link.
 */
class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyEmailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.verifyMessage.text =
            "We sent a link to ${user.email}. Open it to confirm this email is yours, then come back and tap I've verified."

        binding.verifiedButton.setOnClickListener { checkVerified(quiet = false) }
        binding.resendButton.setOnClickListener { resend() }
        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    /** Coming back from the mail app: check without bothering them if it's not done yet. */
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) checkVerified(quiet = true)
    }

    private fun checkVerified(quiet: Boolean) {
        setLoading(true)
        lifecycleScope.launch {
            val verified = EmailVerification.refresh()
            setLoading(false)
            if (verified) {
                startActivity(TeamSelectActivity.intent(this@VerifyEmailActivity, switching = false))
                finish()
            } else if (!quiet) {
                Snackbar.make(binding.root, "Not verified yet. Open the link in the email, then try again.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun resend() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        setLoading(true)
        lifecycleScope.launch {
            val message = EmailVerification.send(user)
            setLoading(false)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            // Firebase limits how often it sends; a short pause stops repeated taps.
            coolingDown = true
            binding.resendButton.isEnabled = false
            delay(RESEND_COOLDOWN_MS)
            coolingDown = false
            binding.resendButton.isEnabled = binding.progress.visibility != View.VISIBLE
        }
    }

    private var coolingDown = false

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.verifiedButton.isEnabled = !loading
        binding.resendButton.isEnabled = !loading && !coolingDown
    }

    companion object {
        private const val RESEND_COOLDOWN_MS = 30_000L

        fun intent(context: Context) = Intent(context, VerifyEmailActivity::class.java)
    }
}
