package com.example.liftingapp.ui

import androidx.appcompat.app.AppCompatActivity
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.data.LegacySummary
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Copies athletes and sets saved on this phone before 0.9 into the open team or General space.
 * Coaches copy every athlete. Athlete accounts can only have one athlete (themselves), so they pick
 * which one is them, and its sets go onto their own profile (merged if it already exists).
 */
fun startLegacyImport(
    activity: AppCompatActivity,
    viewModel: LiftViewModel,
    summary: LegacySummary,
    showMessage: (String) -> Unit
) {
    val target = viewModel.scope.title
    fun run(chosen: List<com.example.liftingapp.data.LocalAthlete>) {
        showMessage("Copying to $target…")
        viewModel.importLegacy(chosen) { copied ->
            showMessage(if (copied == 1) "Copied 1 set to $target" else "Copied $copied sets to $target")
        }
    }

    if (viewModel.isCoach) {
        run(summary.athletes)
        return
    }
    val merge = viewModel.ownAthlete != null
    if (summary.athletes.size == 1) {
        val only = summary.athletes.single()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Is ${only.name} you?")
            .setMessage(
                if (merge) "Their sets will be added to your athlete profile on $target."
                else "${only.name} becomes your athlete profile on $target, with all their sets."
            )
            .setPositiveButton("Yes, copy") { _, _ -> run(listOf(only)) }
            .setNegativeButton("Cancel", null)
            .show()
        return
    }
    val names = summary.athletes.map { it.label() }.toTypedArray()
    MaterialAlertDialogBuilder(activity)
        .setTitle("Which one is you?")
        .setItems(names) { _, which -> run(listOf(summary.athletes[which])) }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun com.example.liftingapp.data.LocalAthlete.label(): String = "$name ($positionGroup)"
