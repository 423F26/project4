package com.example.liftingapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.databinding.ActivityMainBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.LeaderboardTab
import com.example.liftingapp.ui.LogTab
import com.example.liftingapp.ui.SportSections
import com.example.liftingapp.ui.WorkoutLogger
import com.example.liftingapp.ui.WorkoutsTab
import com.example.liftingapp.ui.showAvatar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth

/**
 * The app for one team (or General use): Log, Workouts and Leaderboard tabs. Each tab's wiring
 * lives in ui/ to keep this file short. The header shows the open team; its Switch team button goes
 * back to the team select screen, and the profile picture opens Account & settings (SettingsActivity).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LiftViewModel

    /** Whether this screen was drawn in dark mode, so a change made in Account & settings can redraw it. */
    private var drawnDark = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)   // must happen before any views are inflated
        drawnDark = AppTheme.current(this).isDark(this)
        super.onCreate(savedInstanceState)

        // Not signed in (e.g. session expired) -> back to the login screen.
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        // No team picked yet on this phone -> pick one first.
        if (ScopeStore.load(this, user.uid) == null) {
            startActivity(TeamSelectActivity.intent(this, switching = false))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LiftViewModel::class.java]
        val scope = viewModel.scope

        val logger = WorkoutLogger(this, viewModel, ::showMessage)
        val logTab = LogTab(this, binding.logTab, viewModel, logger)
        WorkoutsTab(this, binding.workoutsTab, viewModel, logger)
        SportSections(this, binding.workoutsTab, viewModel, logger)   // General / Football / Basketball sections above the library
        LeaderboardTab(this, binding.leaderboardTab, viewModel)

        setupHeader(scope)
        setupTabs(savedInstanceState?.getInt(KEY_TAB) ?: TAB_LOG)

        viewModel.cloudMessages.observe(this) { event -> event.take()?.let(::showMessage) }
        viewModel.profile.observe(this) { showHeaderAvatar() }
        viewModel.checkMembership(::removedFromTeam)
        if (savedInstanceState == null) viewModel.checkLegacyData(logTab::offerLegacyImport)
    }

    private fun setupHeader(scope: Scope) {
        binding.switchTeamButton.text = scope.title
        binding.switchTeamButton.contentDescription = "${scope.title}. ${getString(R.string.switch_team)}"
        TooltipCompat.setTooltipText(binding.switchTeamButton, getString(R.string.switch_team))
        binding.switchTeamButton.setOnClickListener { switchTeam() }
        TooltipCompat.setTooltipText(binding.avatarButton, getString(R.string.open_settings))
        binding.avatarButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        showHeaderAvatar()
    }

    /** Back from Account & settings: pick up a new theme or profile picture. */
    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (AppTheme.current(this).isDark(this) != drawnDark) recreate() else showHeaderAvatar()
    }

    private fun showHeaderAvatar() {
        val name = viewModel.currentProfile?.name?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
        binding.avatarButton.showAvatar(this, viewModel.uid, name)
    }

    private fun setupTabs(initialTab: Int) {
        val pages: List<View> = listOf(binding.logTab.root, binding.workoutsTab.root, binding.leaderboardTab.root)
        listOf("Log", "Workouts", "Leaderboard").forEach { binding.tabs.addTab(binding.tabs.newTab().setText(it)) }

        fun show(index: Int) = pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        val start = initialTab.coerceIn(0, pages.lastIndex)
        binding.tabs.getTabAt(start)?.select()
        show(start)
    }

    /** Opens the team select screen. Picking a team there replaces this screen; Back returns here. */
    private fun switchTeam() {
        startActivity(TeamSelectActivity.intent(this, switching = true))
    }

    /** This person left the open team on another phone, or a coach removed them. */
    private fun removedFromTeam() {
        if (isFinishing) return
        ScopeStore.clear(this, viewModel.uid)
        MaterialAlertDialogBuilder(this)
            .setTitle("You're not on ${viewModel.scope.title} any more")
            .setMessage("Pick another team, or use the app on your own.")
            .setCancelable(false)
            .setPositiveButton("Choose a team") { _, _ ->
                startActivity(TeamSelectActivity.intent(this, switching = false))
                finish()
            }
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) outState.putInt(KEY_TAB, binding.tabs.selectedTabPosition)
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
        const val TAB_LOG = 0
    }
}
