package com.example.liftingapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.databinding.ActivitySettingsBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.SettingsPage
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

/**
 * Account & settings, opened by tapping the profile picture in MainActivity's header. The content
 * and everything it does live in ui/SettingsPage.kt. Changing the theme here redraws this screen;
 * MainActivity redraws itself when you come back (see MainActivity.onResume).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        if (ScopeStore.load(this, user.uid) == null) {
            startActivity(TeamSelectActivity.intent(this, switching = false))
            finish()
            return
        }

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        TooltipCompat.setTooltipText(binding.backButton, binding.backButton.contentDescription)
        binding.backButton.setOnClickListener { finish() }

        // Same ViewModel class as the main screen (for the open team), with its own instance.
        val viewModel = ViewModelProvider(this)[LiftViewModel::class.java]
        SettingsPage(this, binding.content, viewModel)

        viewModel.cloudMessages.observe(this) { event ->
            event.take()?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
        }
    }
}
