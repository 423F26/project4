package com.example.liftingapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.liftingapp.R
import com.example.liftingapp.data.JoinCode
import com.example.liftingapp.data.TeamMember
import com.example.liftingapp.data.UserProfile
import com.example.liftingapp.databinding.DialogCreateTeamBinding
import com.example.liftingapp.databinding.DialogJoinTeamBinding
import com.example.liftingapp.databinding.DialogTeamCodeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Sports offered when creating a team (anything else can be typed). */
private val SPORTS = listOf("Football", "Basketball", "Baseball", "Softball", "Soccer", "Volleyball", "Track & Field", "Wrestling", "Other")

/**
 * Join a team with a code. [onJoin] gets the code as typed and should call [done] with null on
 * success (the dialog closes) or an error message to show under the field.
 */
fun showJoinTeamDialog(context: Context, onJoin: (code: String, done: (error: String?) -> Unit) -> Unit) {
    val b = DialogJoinTeamBinding.inflate(LayoutInflater.from(context))
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Join a team")
        .setView(b.root)
        .setPositiveButton("Join", null)   // handled below so a wrong code keeps the dialog open
        .setNegativeButton("Cancel", null)
        .show()
    val join = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun refresh() {
        b.codeLayout.error = null
        join.isEnabled = JoinCode.normalize(b.codeInput.text?.toString().orEmpty()) != null
    }
    b.codeInput.doAfterTextChanged { refresh() }
    join.setOnClickListener {
        join.isEnabled = false
        b.codeLayout.helperText = "Checking…"
        onJoin(b.codeInput.text?.toString().orEmpty()) { error ->
            if (error == null) {
                dialog.dismiss()
            } else {
                b.codeLayout.helperText = null
                b.codeLayout.error = error
                join.isEnabled = true
            }
        }
    }
    refresh()
}

/** Coaches: name and sport for a new team. [onCreate] works like showJoinTeamDialog's onJoin. */
fun showCreateTeamDialog(context: Context, onCreate: (name: String, sport: String, done: (error: String?) -> Unit) -> Unit) {
    val b = DialogCreateTeamBinding.inflate(LayoutInflater.from(context))
    b.sportInput.setAdapter(ArrayAdapter(context, R.layout.item_spinner_dropdown, SPORTS.toMutableList()))
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Create a team")
        .setView(b.root)
        .setPositiveButton("Create", null)
        .setNegativeButton("Cancel", null)
        .show()
    val create = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun name() = b.nameInput.text?.toString()?.trim().orEmpty()
    fun refresh() {
        b.nameLayout.error = null
        create.isEnabled = name().isNotEmpty()
    }
    b.nameInput.doAfterTextChanged { refresh() }
    create.setOnClickListener {
        create.isEnabled = false
        onCreate(name(), b.sportInput.text?.toString()?.trim().orEmpty()) { error ->
            if (error == null) {
                dialog.dismiss()
            } else {
                b.nameLayout.error = error
                create.isEnabled = true
            }
        }
    }
    refresh()
}

/**
 * The team's join code, for its creator: copy, share, and (if [onReset] is given) reset it.
 * [onReset] should call back with the new code, or null if it failed.
 */
fun showTeamCodeDialog(
    context: Context,
    teamName: String,
    code: String,
    justCreated: Boolean,
    onReset: ((done: (newCode: String?) -> Unit) -> Unit)?,
    positiveLabel: String = "Done",
    onPositive: () -> Unit = {}
) {
    val b = DialogTeamCodeBinding.inflate(LayoutInflater.from(context))
    var current = code
    b.intro.text = if (justCreated) "$teamName is ready. Share this code with your athletes so they can join."
    else "Athletes join $teamName with this code."
    b.codeText.text = JoinCode.display(current)

    b.copyButton.setOnClickListener {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Team code", JoinCode.display(current)))
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
    }
    b.shareButton.setOnClickListener {
        val text = "Join $teamName on CatPack: open the app, tap Join a team and enter ${JoinCode.display(current)}"
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(share, "Share team code"))
    }
    b.resetButton.isVisible = onReset != null
    b.resetButton.setOnClickListener {
        val reset = onReset ?: return@setOnClickListener
        MaterialAlertDialogBuilder(context)
            .setTitle("Reset the code?")
            .setMessage("${JoinCode.display(current)} will stop working. Anyone already on $teamName stays on it.")
            .setPositiveButton("Reset") { _, _ ->
                b.resetButton.isEnabled = false
                reset { newCode ->
                    b.resetButton.isEnabled = true
                    if (newCode != null) {
                        current = newCode
                        b.codeText.text = JoinCode.display(newCode)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    MaterialAlertDialogBuilder(context)
        .setTitle(if (justCreated) "Team created" else "Team code")
        .setView(b.root)
        .setPositiveButton(positiveLabel) { _, _ -> onPositive() }
        .setCancelable(!justCreated)
        .show()
}

/** Coaches: who's on the team. */
fun showMembersDialog(context: Context, teamName: String, members: List<TeamMember>) {
    val coaches = members.count { it.role == UserProfile.ROLE_COACH }
    val lines = members.map { member ->
        val tag = when {
            member.isOwner -> " · Created the team"
            member.role == UserProfile.ROLE_COACH -> " · Coach"
            else -> ""
        }
        member.name + tag
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("$teamName · ${members.size} ${if (members.size == 1) "member" else "members"}")
        .setMessage(
            if (members.isEmpty()) "No one has joined yet."
            else lines.joinToString("\n") + "\n\n$coaches ${if (coaches == 1) "coach" else "coaches"}, ${members.size - coaches} ${if (members.size - coaches == 1) "athlete" else "athletes"}"
        )
        .setPositiveButton("Close", null)
        .show()
}
