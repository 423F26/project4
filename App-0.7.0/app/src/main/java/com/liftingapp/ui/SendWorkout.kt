package com.example.liftingapp.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.liftingapp.data.Athlete
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
 * Nothing is sent until the coach hits send in that app.
 *
 * [onAthleteChanged] saves new contact details; [onProblem] shows a message if no app can send.
 */
fun showSendWorkoutDialog(
    context: Context,
    item: WorkoutWithExercises,
    athletes: List<Athlete>,
    onAthleteChanged: (Athlete) -> Unit,
    onProblem: (String) -> Unit
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
        b.contactNote.isVisible = athlete != null
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
        val current = athlete ?: return
        val newPhone = if (isValidPhone(phone())) phone().orNullIfBlank() else current.phone
        val newEmail = if (isValidEmail(email())) email().orNullIfBlank() else current.email
        if (newPhone != current.phone || newEmail != current.email) {
            onAthleteChanged(current.copy(phone = newPhone, email = newEmail))
        }
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

/** startActivity that reports "no app can handle this" instead of crashing. */
private fun Context.tryStart(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
