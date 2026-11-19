package com.example.liftingapp.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.Recipient
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.data.dialablePhone
import com.example.liftingapp.data.isValidEmail
import com.example.liftingapp.data.isValidPhone
import com.example.liftingapp.data.orNullIfBlank
import com.example.liftingapp.databinding.DialogSendWorkoutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Send a workout to an athlete. The coach picks who it's for (or no one in particular), checks
 * the phone/email (filled in from the athlete and saved back to them), then chooses:
 *  - Text: opens the texting app with the message ready and the number filled in
 *  - Email: opens the email app with subject, message and address filled in
 *  - Send with another app: the system share sheet (GroupMe, WhatsApp, copy, ...)
 * Nothing is sent until the coach hits send in that app. There's also Send in CatPack, which opens
 * [showInAppSendPicker]: anyone on the team (or the whole team), and mutual friends.
 *
 * [onAthleteChanged] saves new contact details (only for athletes [canSaveContact] allows: coaches,
 * or the athlete themselves); [onProblem] shows a message if no app can send.
 */
fun showSendWorkoutDialog(
    context: Context,
    item: WorkoutWithExercises,
    athletes: List<Athlete>,
    canSaveContact: (Athlete) -> Boolean,
    onAthleteChanged: (Athlete) -> Unit,
    onProblem: (String) -> Unit,
    /** Opens the in-app recipient picker. */
    onSendInApp: (() -> Unit)? = null
) {
    val b = DialogSendWorkoutBinding.inflate(LayoutInflater.from(context))
    val workout = item.workout
    var athlete: Athlete? = null

    b.athleteSpinner.adapter = spinnerAdapter(context, listOf("No one in particular") + athletes.map { it.label() })

    fun phone() = b.phoneInput.text?.toString().orEmpty()
    fun email() = b.emailInput.text?.toString().orEmpty()
    fun message() = WorkoutShare.message(workout, item.exercises, athlete?.name)

    fun refresh() {
        val phoneOk = isValidPhone(phone())
        val emailOk = isValidEmail(email())
        b.phoneLayout.error = if (phoneOk) null else "Enter a phone number, e.g. 406-555-0100"
        b.emailLayout.error = if (emailOk) null else "Enter an email address, e.g. name@example.com"
        b.textButton.isEnabled = phoneOk
        b.emailButton.isEnabled = emailOk
        b.preview.text = message()
        b.contactNote.isVisible = athlete?.let(canSaveContact) == true
        b.contactNote.text = athlete?.let { "Phone and email are saved to ${it.name} for next time." }.orEmpty()
    }

    b.athleteSpinner.onItemSelected { position ->
        athlete = athletes.getOrNull(position - 1)   // position 0 is "No one in particular"
        b.phoneInput.setText(athlete?.phone.orEmpty())
        b.emailInput.setText(athlete?.email.orEmpty())
        refresh()
    }
    b.phoneInput.doAfterTextChanged { refresh() }
    b.emailInput.doAfterTextChanged { refresh() }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Send “${workout.name}”")
        .setView(b.root)
        .setNegativeButton("Cancel", null)
        .show()

    /** Saves whichever of phone/email the coach typed or changed for the picked athlete (valid ones only). */
    fun rememberContact() {
        val current = athlete?.takeIf(canSaveContact) ?: return
        val newPhone = if (isValidPhone(phone())) phone().orNullIfBlank() else current.phone
        val newEmail = if (isValidEmail(email())) email().orNullIfBlank() else current.email
        if (newPhone != current.phone || newEmail != current.email) {
            onAthleteChanged(current.copy(phone = newPhone, email = newEmail))
        }
    }

    b.inAppButton.isVisible = onSendInApp != null
    b.inAppButton.setOnClickListener {
        onSendInApp?.invoke()
        dialog.dismiss()
    }
    b.textButton.setOnClickListener {
        rememberContact()
        val number = phone().orNullIfBlank()?.let(::dialablePhone).orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))   // only texting apps handle smsto:
            .putExtra("sms_body", message())
        if (!context.tryStart(intent)) onProblem("No texting app found on this phone. Try Send with another app.")
        dialog.dismiss()
    }

    b.emailButton.setOnClickListener {
        rememberContact()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {   // only email apps handle mailto:
            email().orNullIfBlank()?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            putExtra(Intent.EXTRA_SUBJECT, WorkoutShare.subject(workout))
            putExtra(Intent.EXTRA_TEXT, message())
        }
        if (!context.tryStart(intent)) onProblem("No email app found on this phone. Try Send with another app.")
        dialog.dismiss()
    }

    b.otherAppButton.setOnClickListener {
        rememberContact()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, WorkoutShare.subject(workout))
            putExtra(Intent.EXTRA_TEXT, message())
        }
        context.tryStart(Intent.createChooser(share, "Send workout with"))
        dialog.dismiss()
    }

    refresh()
}

/**
 * The Send dialog as every Workouts screen opens it: text, email or another app, plus Send in
 * CatPack (anyone on the team, the whole team, or a mutual friend).
 */
fun AppCompatActivity.sendWorkout(viewModel: LiftViewModel, item: WorkoutWithExercises, athletes: List<Athlete>, showMessage: (String) -> Unit) {
    showSendWorkoutDialog(
        context = this,
        item = item,
        athletes = athletes,
        canSaveContact = viewModel::canEdit,
        onAthleteChanged = viewModel::updateAthlete,
        onProblem = showMessage,
        onSendInApp = { showInAppSendPicker(viewModel, item, showMessage) }
    )
}

/** What a row of the in-app picker sends to. */
private sealed interface InAppTarget {
    data object WholeTeam : InAppTarget
    data class Teammate(val person: Recipient) : InAppTarget
    data class Friend(val person: Recipient) : InAppTarget
}

/**
 * Who to send [item] to inside CatPack: on a team, everyone on it and each person (athletes and
 * coaches); anywhere, mutual friends (people who follow each other from the leaderboard).
 */
fun AppCompatActivity.showInAppSendPicker(viewModel: LiftViewModel, item: WorkoutWithExercises, showMessage: (String) -> Unit) {
    val team = viewModel.scope as? Scope.Team
    val teammates = viewModel.teamPeople.value.orEmpty()
    viewModel.loadFriends { friends ->
        if (isDestroyed) return@loadFriends
        val teammateIds = teammates.map { it.uid }.toSet()
        val sections = buildList<PickerSection<InAppTarget>> {
            if (team != null) {
                add(PickerSection<InAppTarget>(team.name, listOf(PickerItem<InAppTarget>("Everyone on ${team.name}", "Shows on everyone's Workouts tab", InAppTarget.WholeTeam)) +
                    teammates.map { PickerItem<InAppTarget>(it.name, if (it.isCoach) "Coach" else "Teammate", InAppTarget.Teammate(it)) }))
            }
            val otherFriends = friends.filter { it.uid !in teammateIds }
            if (otherFriends.isNotEmpty()) {
                add(PickerSection<InAppTarget>("Friends", otherFriends.map { PickerItem<InAppTarget>(it.name, "You follow each other", InAppTarget.Friend(it)) }))
            }
        }
        if (sections.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("No one to send to yet")
                .setMessage("Friends are people you follow on the leaderboard who follow you back. Once you follow each other, you can send them workouts here.")
                .setPositiveButton("OK", null)
                .show()
            return@loadFriends
        }
        showPickerDialog(
            context = this,
            title = "Send “${item.workout.name}” in CatPack",
            searchHint = "Search people",
            sections = sections,
            emptyMessage = "No one matches that.",
            onPick = { target ->
                when (target) {
                    InAppTarget.WholeTeam -> viewModel.sendToTeam(item, null, showMessage)
                    is InAppTarget.Teammate -> viewModel.sendToTeam(item, target.person, showMessage)
                    is InAppTarget.Friend -> viewModel.sendToFriend(item, target.person, showMessage)
                }
            }
        )
    }
}

/** startActivity that reports "no app can handle this" instead of crashing. */
private fun Context.tryStart(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
