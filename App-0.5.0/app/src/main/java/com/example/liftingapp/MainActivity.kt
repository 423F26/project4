package com.example.liftingapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.data.AuthRepository
import com.example.liftingapp.databinding.ActivityMainBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.HistoryTab
import com.example.liftingapp.ui.LeaderboardTab
import com.example.liftingapp.ui.LogTab
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Views + ViewBinding, like project4. The Log tab is Garrett's spinner/chart/FAB screen;
 * History and Leaderboard are separate tabs. Each tab's wiring lives in ui/ to keep this file short.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LiftViewModel
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)   // must happen before any views are inflated
        super.onCreate(savedInstanceState)

        // Not signed in (e.g. session expired) -> back to the login screen.
        if (auth.currentUser == null) {
            openLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LiftViewModel::class.java]

        LogTab(this, binding.logTab, viewModel)
        HistoryTab(this, binding.historyTab, viewModel)
        LeaderboardTab(this, binding.leaderboardTab, viewModel)

        setupTabs(savedInstanceState?.getInt(KEY_TAB) ?: 0)
        binding.themeButton.setOnClickListener { showThemePicker() }
        binding.signOutButton.setOnClickListener { confirmSignOut() }
    }

    private fun setupTabs(initialTab: Int) {
        val pages: List<View> = listOf(binding.logTab.root, binding.historyTab.root, binding.leaderboardTab.root)
        listOf("Log", "History", "Leaderboard").forEach { binding.tabs.addTab(binding.tabs.newTab().setText(it)) }

        fun show(index: Int) = pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.tabs.getTabAt(initialTab)?.select()
        show(initialTab)
    }

    /** Sign-out button (header, top right): confirm, sign out of Firebase, return to login. */
    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign out?")
            .setMessage(auth.currentUser?.email?.let { "You're signed in as $it." })
            .setPositiveButton("Sign out") { _, _ ->
                auth.signOut()
                lifecycleScope.launch {
                    // Forget the Google account choice so the picker shows again next time.
                    runCatching { CredentialManager.create(this@MainActivity).clearCredentialState(ClearCredentialStateRequest()) }
                    openLogin()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /** Palette button: pick a theme, save it, and rebuild the screen in the new colors. */
    private fun showThemePicker() {
        val themes = AppTheme.entries
        val current = AppTheme.current(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(themes.map { it.label }.toTypedArray(), themes.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                val chosen = themes[which]
                if (chosen != current) {
                    AppTheme.save(this, chosen)
                    recreate()   // ViewModel and the selected tab survive this
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) outState.putInt(KEY_TAB, binding.tabs.selectedTabPosition)
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
    }
}
