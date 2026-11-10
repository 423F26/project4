package com.example.liftingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.data.CloudErrors
import com.example.liftingapp.data.ProfileRepository
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.TeamException
import com.example.liftingapp.data.TeamMembership
import com.example.liftingapp.data.TeamRepository
import com.example.liftingapp.data.UserProfile
import com.example.liftingapp.databinding.ActivityTeamSelectBinding
import com.example.liftingapp.databinding.ItemTeamBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.confirmSignOut
import com.example.liftingapp.ui.signOutIfRemoved
import com.example.liftingapp.ui.showAvatar
import com.example.liftingapp.ui.showCreateTeamDialog
import com.example.liftingapp.ui.showJoinTeamDialog
import com.example.liftingapp.ui.showTeamCodeDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The team select screen: shown right after signing in, and from Switch team in the app's header.
 *  - Your teams: tap one to open it. You only see that team's people.
 *  - General use: just you; the leaderboard compares you with everyone else using General.
 *  - Join a team: enter the code a coach shared. Anyone can join.
 *  - Create a team: coaches only (the role in users/{uid}, set in the Firebase console). Shows the
 *    new team's code, which the creator can find again in Account & settings.
 */
class TeamSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamSelectBinding
    private lateinit var uid: String
    private lateinit var teams: TeamRepository
    private var profile: UserProfile? = null
    private var askedRole = false

    /** The open scope when this screen came from Switch team (marked "Current"), else null. */
    private var current: Scope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        uid = user.uid
        teams = TeamRepository(uid)
        val profiles = ProfileRepository(uid)
        val switching = intent.getBooleanExtra(EXTRA_SWITCHING, false)
        current = if (switching) ScopeStore.load(this, uid) else null

        binding = ActivityTeamSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty().substringBefore('@')
        showWho(displayName)
        binding.signedInAs.text = user.email?.let { "Signed in as $it" }.orEmpty()
        binding.avatar.showAvatar(this, uid, displayName)
        binding.backButton.isVisible = switching
        binding.backButton.setOnClickListener { finish() }

        binding.generalCard.badge.isVisible = false
        binding.generalCard.icon.isVisible = true
        binding.generalCard.name.text = Scope.General.title
        binding.generalCard.meta.text = "Just you. Compare on the General leaderboard."
        binding.generalCard.currentBadge.isVisible = current == Scope.General
        binding.generalCard.root.setOnClickListener { choose(Scope.General) }

        binding.joinButton.setOnClickListener { join() }
        binding.createButton.setOnClickListener { create() }
        binding.signOutButton.setOnClickListener { confirmSignOut() }

        signOutIfRemoved()
        lifecycleScope.launch { runCatching { profiles.ensureProfile() } }
        profiles.profile()
            .catch { CloudErrors.report("load your profile", it) }
            .asLiveData()
            .observe(this) { p ->
                profile = p
                showWho(p.name.ifBlank { displayName })
                binding.createButton.isVisible = p.isCoach
                binding.coachHint.isVisible = !p.isCoach
                binding.coachHint.text =
                    if (p.coachPending) "Coach request sent. You can create teams once a CatPack admin approves it."
                    else "Coach accounts can also create teams."
                // Accounts made before this question, or with Google: ask once.
                if (p.requestedRole.isBlank() && !p.isCoach && !askedRole) {
                    askedRole = true
                    askAthleteOrCoach(profiles)
                }
            }

        teams.myTeams()
            .catch { error ->
                showMessage(CloudErrors.describe("load your teams", error))
                emit(emptyList())
            }
            .asLiveData()
            .observe(this, ::renderTeams)

        CloudErrors.messages.observe(this) { event -> event.take()?.let(::showMessage) }
    }

    private fun showWho(name: String) {
        val first = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        binding.greeting.text = if (first.isEmpty()) "Hi there" else "Hi, $first"
    }

    private fun renderTeams(list: List<TeamMembership>) {
        binding.teamsProgress.isVisible = false
        binding.noTeamsText.isVisible = list.isEmpty()
        binding.teamList.removeAllViews()
        list.forEach { membership ->
            val row = ItemTeamBinding.inflate(layoutInflater, binding.teamList, false)
            row.badge.text = membership.teamName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
            row.name.text = membership.teamName
            row.meta.text = listOfNotNull(
                membership.sport.takeIf { it.isNotBlank() },
                if (membership.isOwner) "You created it" else null
            ).joinToString(" · ").ifEmpty { "Team" }
            row.currentBadge.isVisible = (current as? Scope.Team)?.id == membership.teamId
            row.root.setOnClickListener {
                choose(Scope.Team(membership.teamId, membership.teamName, membership.sport, membership.isOwner))
            }
            binding.teamList.addView(row.root)
        }
    }

    private fun join() {
        showJoinTeamDialog(this) { code, done ->
            lifecycleScope.launch {
                try {
                    val name = profile?.name?.takeIf { it.isNotBlank() } ?: FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
                    val team = teams.joinTeam(code, name, profile?.role ?: UserProfile.ROLE_ATHLETE)
                    done(null)
                    choose(team)
                } catch (e: TeamException) {
                    done(e.message)
                } catch (e: Exception) {
                    done(CloudErrors.describe("join the team", e))
                }
            }
        }
    }

    private fun create() {
        showCreateTeamDialog(this) { name, sport, done ->
            lifecycleScope.launch {
                try {
                    val ownerName = profile?.name?.takeIf { it.isNotBlank() } ?: "Coach"
                    val (team, code) = teams.createTeam(name, sport, ownerName)
                    done(null)
                    showTeamCodeDialog(
                        context = this@TeamSelectActivity,
                        teamName = team.name,
                        code = code,
                        justCreated = true,
                        onReset = null,
                        positiveLabel = "Open ${team.name}",
                        onPositive = { choose(team) }
                    )
                } catch (e: TeamException) {
                    done(e.message)
                } catch (e: Exception) {
                    done(CloudErrors.describe("create the team", e))
                }
            }
        }
    }

    /** Opens the app on [scope], replacing any MainActivity that was open on another team. */
    private fun choose(scope: Scope) {
        ScopeStore.save(this, uid, scope)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** "Are you an athlete or a coach?" Coach is a request; an admin approves it in the Firebase console. */
    private fun askAthleteOrCoach(profiles: ProfileRepository) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Are you an athlete or a coach?")
            .setMessage("Coach accounts are approved by your CatPack admin. Until then you can use the app as an athlete.")
            .setCancelable(false)
            .setPositiveButton("Athlete") { _, _ ->
                lifecycleScope.launch { runCatching { profiles.setRequestedRole(UserProfile.ROLE_ATHLETE) } }
            }
            .setNegativeButton("Coach") { _, _ ->
                lifecycleScope.launch {
                    runCatching { profiles.setRequestedRole(UserProfile.ROLE_COACH) }
                        .onSuccess { showMessage("Coach request sent. You'll be able to create teams once it's approved.") }
                }
            }
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val EXTRA_SWITCHING = "switching"

        /** [switching] = opened from inside the app (shows a close button and marks the current team). */
        fun intent(context: Context, switching: Boolean): Intent =
            Intent(context, TeamSelectActivity::class.java).putExtra(EXTRA_SWITCHING, switching)
    }
}
