package com.example.liftingapp.ui

import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.TeamSelectActivity
import com.example.liftingapp.data.AccountDeletion
import com.example.liftingapp.data.AuthRepository
import com.example.liftingapp.data.Avatars
import com.example.liftingapp.data.CloudErrors
import com.example.liftingapp.data.CloudPaths
import com.example.liftingapp.data.GeneralBoard
import com.example.liftingapp.data.LiftRepository
import com.example.liftingapp.data.ProfileRepository
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.TeamException
import com.example.liftingapp.data.UserProfile
import com.example.liftingapp.data.athleteDetails
import com.example.liftingapp.data.awaitResult
import com.example.liftingapp.data.inBackground
import com.example.liftingapp.databinding.ItemSettingActionBinding
import com.example.liftingapp.databinding.ItemSettingSwitchBinding
import com.example.liftingapp.databinding.ContentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Account & settings (SettingsActivity, opened from the profile picture in the app's header):
 *  - Profile: picture (pick from the phone's photos; no permission needed), name, role, and the
 *    athlete details from sign-up (position, body weight, phone), which feed this person's own
 *    athlete profile on every team.
 *  - Team: switch team, the join code (its creator only), members (coaches), leave the team.
 *  - Appearance: System / Light / Dark, and whether to choose a team every time the app opens.
 *  - Privacy & visibility: General leaderboard (on/off, initials), profile picture, and whether
 *    teammates who aren't coaches can see your progress.
 *  - Exercises & data: your custom exercises; copying lifts saved on this phone before teams.
 *  - Account: change password, sign out, delete account.
 */
class SettingsPage(
    private val activity: AppCompatActivity,
    private val binding: ContentSettingsBinding,
    private val viewModel: LiftViewModel
) {
    private val uid = viewModel.uid
    private val profiles: ProfileRepository = viewModel.profileRepository
    private var profile: UserProfile? = null

    /** Registered during SettingsActivity.onCreate, so a picked photo still arrives after a rotation. */
    private val pickPhoto = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadPhoto(uri)
    }

    init {
        setupProfile()
        setupTeam()
        setupAppearance()
        setupPrivacy()
        setupExercisesAndData()
        setupAccount()
        binding.aboutText.text = "CatPack ${versionName()} · ${viewModel.scope.title}"

        viewModel.profile.observe(activity) { p ->
            profile = p
            showProfile(p)
        }
    }

    // --- Profile ---

    private fun setupProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        binding.nameText.text = user?.displayName.orEmpty()
        binding.emailText.text = user?.email.orEmpty()
        binding.avatarFrame.setOnClickListener { choosePhoto() }
        binding.changePhotoButton.setOnClickListener { choosePhoto() }
        binding.removePhotoButton.setOnClickListener { confirmRemovePhoto() }
        binding.editNameButton.setOnClickListener { editProfile() }
        refreshAvatar()
    }

    private fun showProfile(p: UserProfile) {
        binding.nameText.text = p.name.ifBlank { "Add your name" }
        binding.emailText.text = p.email
        binding.athleteDetailsText.text = listOfNotNull(
            p.positionGroup.takeIf { it.isNotBlank() },
            if (p.bodyWeightLb > 0f) "${p.bodyWeightLb.lb()} lb" else null,
            p.phone
        ).joinToString(" · ").ifEmpty { "Add your position and body weight" }
        binding.roleBadge.text = if (p.isCoach) "Coach account" else "Athlete account"
        bindSwitch(binding.showOnGeneralRow, p.showOnGeneral) { setFlag(ProfileRepository.FIELD_SHOW_ON_GENERAL, it) }
        bindSwitch(binding.initialsRow, p.initialsOnGeneral) { setFlag(ProfileRepository.FIELD_INITIALS_ON_GENERAL, it) }
        binding.initialsRow.root.alpha = if (p.showOnGeneral) 1f else 0.5f
        bindSwitch(binding.showPhotoRow, p.showPhoto) { setFlag(ProfileRepository.FIELD_SHOW_PHOTO, it) }
        bindSwitch(binding.shareProgressRow, p.shareProgress) { setFlag(ProfileRepository.FIELD_SHARE_PROGRESS, it) }
        binding.myExercisesRow.summary.text = when (p.customExercises.size) {
            0 -> "None yet. Type a new name in any exercise picker to add one."
            1 -> "1 added. Shows up in every exercise list."
            else -> "${p.customExercises.size} added. They show up in every exercise list."
        }
        binding.myExercisesRow.summary.isVisible = true
        refreshTeamRows(p)
        refreshAvatar()
    }

    private fun refreshAvatar() {
        val name = profile?.name ?: FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
        binding.avatar.showAvatar(activity, uid, name)
        activity.lifecycleScope.launch {
            binding.removePhotoButton.isVisible = Avatars.load(uid) != null
        }
    }

    private fun choosePhoto() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun uploadPhoto(uri: android.net.Uri) {
        binding.photoProgress.isVisible = true
        activity.lifecycleScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) { AvatarImage.prepare(activity, uri) }
                if (jpeg == null) {
                    showMessage("Couldn't read that picture. Try another one.")
                } else {
                    Avatars.upload(uid, jpeg, visible = profile?.showPhoto != false)
                    refreshAvatar()
                    showMessage("Profile picture updated")
                }
            } catch (e: Exception) {
                showMessage(CloudErrors.describe("save your picture", e))
            } finally {
                binding.photoProgress.isVisible = false
            }
        }
    }

    private fun confirmRemovePhoto() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Remove your profile picture?")
            .setMessage("People will see your initials instead.")
            .setPositiveButton("Remove") { _, _ ->
                activity.lifecycleScope.launch {
                    try {
                        Avatars.remove(uid)
                        refreshAvatar()
                    } catch (e: Exception) {
                        showMessage(CloudErrors.describe("remove your picture", e))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Name, position, body weight and phone: saved to the account and this person's athlete profile on every team. */
    private fun editProfile() {
        val p = profile ?: return
        showAthleteDialog(
            context = activity,
            title = "Your profile",
            initial = p.athleteDetails(),
            intro = "Used for your athlete profile on every team and in General use.",
            showEmail = false
        ) { details ->
            viewModel.updateMyProfile(details)
            showMessage("Profile saved")
        }
    }

    // --- Team ---

    private fun setupTeam() {
        val scope = viewModel.scope
        binding.teamTitle.text = scope.title
        action(binding.switchTeamRow, R.drawable.ic_swap, "Switch team", "Pick another team, or General use") {
            activity.startActivity(TeamSelectActivity.intent(activity, switching = true))
        }
        if (scope is Scope.Team) {
            action(binding.teamCodeRow, R.drawable.ic_lock, "Team code", "Only you can see it. Share it so athletes can join.") {
                showCode(scope)
            }
            action(binding.membersRow, R.drawable.ic_group, "Members", "Everyone who has joined ${scope.name}") { showMembers(scope) }
            action(binding.leaveTeamRow, R.drawable.ic_logout, "Leave ${scope.name}", null) { confirmLeave(scope) }
        }
        refreshTeamRows(null)
    }

    private fun refreshTeamRows(p: UserProfile?) {
        val scope = viewModel.scope
        val isCoach = p?.isCoach == true
        binding.teamMeta.text = when (scope) {
            is Scope.Team -> listOfNotNull(
                scope.sport.takeIf { it.isNotBlank() },
                when {
                    scope.isOwner -> "You created this team"
                    isCoach -> "You're a coach here"
                    else -> "You're an athlete here"
                }
            ).joinToString(" · ")
            Scope.General -> "Just you. What you log here is private; the General leaderboard shows only what you allow below."
        }
        binding.teamCodeRow.root.isVisible = scope is Scope.Team && scope.isOwner
        binding.membersRow.root.isVisible = scope is Scope.Team && isCoach
        binding.leaveTeamRow.root.isVisible = scope is Scope.Team && !scope.isOwner
    }

    private fun showCode(team: Scope.Team) {
        activity.lifecycleScope.launch {
            try {
                val code = viewModel.teamRepository.joinCode(team.id)
                if (code == null) {
                    showMessage("This team has no code yet.")
                    return@launch
                }
                showTeamCodeDialog(activity, team.name, code, justCreated = false, onReset = { done ->
                    activity.lifecycleScope.launch {
                        try {
                            done(viewModel.teamRepository.resetJoinCode(team))
                            showMessage("New code ready. The old one no longer works.")
                        } catch (e: Exception) {
                            done(null)
                            showMessage(e.message ?: "Couldn't reset the code.")
                        }
                    }
                })
            } catch (e: TeamException) {
                showMessage(e.message.orEmpty())
            }
        }
    }

    private fun showMembers(team: Scope.Team) {
        activity.lifecycleScope.launch {
            try {
                showMembersDialog(activity, team.name, viewModel.teamRepository.members(team.id))
            } catch (e: TeamException) {
                showMessage(e.message.orEmpty())
            }
        }
    }

    private fun confirmLeave(team: Scope.Team) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Leave ${team.name}?")
            .setMessage("You'll stop seeing the team. Your athlete profile and lifts stay with the team, and you can rejoin with the code.")
            .setPositiveButton("Leave") { _, _ ->
                activity.lifecycleScope.launch {
                    try {
                        viewModel.teamRepository.leaveTeam(team.id)
                        ScopeStore.clear(activity, uid)
                        // Closes the team's screens too, so Back doesn't return to a team you've left.
                        activity.startActivity(
                            TeamSelectActivity.intent(activity, switching = false)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    } catch (e: TeamException) {
                        showMessage(e.message.orEmpty())
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Appearance ---

    private fun setupAppearance() {
        val buttons = mapOf(R.id.themeSystem to AppTheme.SYSTEM, R.id.themeLight to AppTheme.LIGHT, R.id.themeDark to AppTheme.DARK)
        val current = AppTheme.current(activity)
        binding.themeToggle.check(buttons.entries.first { it.value == current }.key)
        binding.themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val chosen = buttons[checkedId]
            if (isChecked && chosen != null && chosen != AppTheme.current(activity)) {
                val wasDark = AppTheme.current(activity).isDark(activity)
                AppTheme.save(activity, chosen)
                if (chosen.isDark(activity) != wasDark) activity.recreate()   // ViewModel and the selected tab survive this
            }
        }

        binding.askTeamRow.title.text = "Choose a team every time I open the app"
        binding.askTeamRow.summary.text = "Otherwise the app opens on the last team you picked."
        bindSwitch(binding.askTeamRow, ScopeStore.askEveryLaunch(activity)) { ScopeStore.setAskEveryLaunch(activity, it) }
    }

    // --- Privacy ---

    private fun setupPrivacy() {
        binding.showOnGeneralRow.title.text = "Show me on the General leaderboard"
        binding.showOnGeneralRow.summary.text = "Your best lifts from General use, for everyone using the app on their own to see."
        binding.initialsRow.title.text = "Use my initials there"
        binding.initialsRow.summary.text = "Show “J. S.” instead of your full name on the General leaderboard."
        binding.showPhotoRow.title.text = "Show my profile picture to others"
        binding.showPhotoRow.summary.text = "When this is off, everyone else sees your initials."
        binding.shareProgressRow.title.text = "Teammates can see my progress"
        binding.shareProgressRow.summary.text = "Your chart and workout log on each team. Coaches always can. You stay on the team leaderboard."
    }

    /** Saves one privacy switch, then updates whatever depends on it. */
    private fun setFlag(field: String, value: Boolean) {
        val before = profile ?: return
        val after = when (field) {
            ProfileRepository.FIELD_SHOW_ON_GENERAL -> before.copy(showOnGeneral = value)
            ProfileRepository.FIELD_INITIALS_ON_GENERAL -> before.copy(initialsOnGeneral = value)
            ProfileRepository.FIELD_SHOW_PHOTO -> before.copy(showPhoto = value)
            else -> before.copy(shareProgress = value)
        }
        profile = after
        inBackground("save your privacy setting") {
            profiles.setFlag(field, value)
            when (field) {
                ProfileRepository.FIELD_SHARE_PROGRESS ->
                    CloudPaths.myTeams(uid).get().awaitResult().documents.forEach { team ->
                        LiftRepository(Scope.Team(team.id, team.getString("teamName").orEmpty()), uid).setOwnProgressHidden(!value)
                    }
                else -> {
                    if (field == ProfileRepository.FIELD_SHOW_PHOTO) Avatars.setVisible(uid, value)
                    GeneralBoard.syncAll(uid, after)
                }
            }
        }
    }

    // --- Exercises and old data ---

    private fun setupExercisesAndData() {
        action(binding.myExercisesRow, R.drawable.ic_checklist, "Your exercises", null) { manageExercises() }
        viewModel.legacySummary { summary ->
            if (summary == null) return@legacySummary
            val sets = if (summary.setCount == 1) "1 set" else "${summary.setCount} sets"
            action(binding.importRow, R.drawable.ic_refresh, "Copy lifts saved on this phone", "$sets from before teams, into ${viewModel.scope.title}") {
                startLegacyImport(activity, viewModel, summary, ::showMessage)
            }
            binding.importRow.root.isVisible = true
        }
    }

    private fun manageExercises() {
        val custom = profile?.customExercises.orEmpty().sortedBy { it.lowercase() }
        if (custom.isEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Your exercises")
                .setMessage("To add one, open the exercise picker on the Log tab and type a name that isn't listed.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val remove = BooleanArray(custom.size)
        MaterialAlertDialogBuilder(activity)
            .setTitle("Remove exercises")
            .setMultiChoiceItems(custom.toTypedArray(), remove) { _, which, checked -> remove[which] = checked }
            .setPositiveButton("Remove checked") { _, _ ->
                custom.filterIndexed { i, _ -> remove[i] }.forEach(viewModel::removeCustomExercise)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Account ---

    private fun setupAccount() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        val hasPassword = user?.providerData?.any { it.providerId == "password" } == true
        if (hasPassword && email != null) {
            action(binding.resetPasswordRow, R.drawable.ic_lock, "Change password", "Email a reset link to $email") {
                AuthRepository().sendPasswordReset(email) { message -> showMessage(message) }
            }
            binding.resetPasswordRow.root.isVisible = true
        }
        action(binding.signOutRow, R.drawable.ic_logout, "Sign out", email) { activity.confirmSignOut() }
        action(binding.deleteAccountRow, R.drawable.ic_delete, "Delete account", "Removes your profile, picture and lifts") { confirmDeleteAccount() }
        val error = binding.root.themeColor(AppCompatR.attr.colorError)
        binding.deleteAccountRow.title.setTextColor(error)
        binding.deleteAccountRow.icon.setColorFilter(error)
        binding.deleteAccountRow.chevron.isVisible = false
    }

    private fun confirmDeleteAccount() {
        if (AccountDeletion.needsFreshSignIn()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Sign in again first")
                .setMessage("For your security, deleting an account needs a fresh sign-in. Sign out, sign back in, then come back here within 5 minutes.")
                .setPositiveButton("Sign out") { _, _ -> activity.signOutNow() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Delete your account?")
            .setMessage(
                "This permanently deletes your profile and picture, everything in General use, your General leaderboard rows, " +
                    "and your own athlete profile on each team. Athletes a coach added stay with their teams. This can't be undone."
            )
            .setPositiveButton("Delete everything") { _, _ ->
                showMessage("Deleting your account…")
                activity.lifecycleScope.launch {
                    try {
                        AccountDeletion.deleteEverything()
                        ScopeStore.clear(activity, uid)
                        activity.signOutNow()
                    } catch (e: TeamException) {
                        showMessage(e.message.orEmpty())
                    } catch (e: Exception) {
                        showMessage(CloudErrors.describe("delete your account", e))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Helpers ---

    private fun action(row: ItemSettingActionBinding, @DrawableRes icon: Int, title: String, summary: String?, onClick: () -> Unit) {
        row.icon.setImageResource(icon)
        row.title.text = title
        row.summary.text = summary
        row.summary.isVisible = !summary.isNullOrBlank()
        row.root.setOnClickListener { onClick() }
    }

    /** Sets a switch without triggering its listener, then listens for the person's changes. */
    private fun bindSwitch(row: ItemSettingSwitchBinding, checked: Boolean, onChange: (Boolean) -> Unit) {
        row.toggle.setOnCheckedChangeListener(null)
        row.toggle.isChecked = checked
        row.toggle.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        row.root.setOnClickListener { row.toggle.toggle() }
        row.toggle.contentDescription = row.title.text
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun versionName(): String = runCatching {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
