package com.example.liftingapp.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import androidx.core.widget.doAfterTextChanged
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.MAX_REPS
import com.example.liftingapp.data.Strength
import com.example.liftingapp.databinding.DialogAddAthleteBinding
import com.example.liftingapp.databinding.DialogLogSetBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Replaces project4's showAddWeightDialog(): same FAB -> dialog flow, but asks for reps too
 * (needed for e1RM) and shows the estimate live. Save stays disabled until the input is valid.
 */
fun showLogSetDialog(context: Context, athlete: Athlete, exercise: String, onSave: (weight: Float, reps: Int) -> Unit) {
    val b = DialogLogSetBinding.inflate(LayoutInflater.from(context))
    b.repsLayout.helperText = "1–$MAX_REPS"

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Log $exercise for ${athlete.name}")
        .setView(b.root)
        .setPositiveButton("Save", null)   // click handled below so invalid input doesn't close the dialog
        .setNegativeButton("Cancel", null)
        .show()
    val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun parsed(): Pair<Float, Int>? {
        val weight = b.weightInput.text?.toString()?.toFloatOrNull()?.takeIf { it > 0f }
        val reps = b.repsInput.text?.toString()?.toIntOrNull()?.takeIf { it in 1..MAX_REPS }
        return if (weight != null && reps != null) weight to reps else null
    }

    fun refresh() {
        val input = parsed()
        save.isEnabled = input != null
        val repsText = b.repsInput.text?.toString().orEmpty()
        val repsOk = repsText.toIntOrNull()?.let { it in 1..MAX_REPS } == true
        b.repsLayout.error = if (repsText.isNotEmpty() && !repsOk) "Enter 1–$MAX_REPS reps" else null
        b.e1rmPreview.text = input?.let { (w, r) -> "Estimated 1RM: ${Strength.estimatedOneRepMax(w, r).wholeLb()} lb" } ?: ""
    }

    b.weightInput.doAfterTextChanged { refresh() }
    b.repsInput.doAfterTextChanged { refresh() }
    save.setOnClickListener {
        parsed()?.let { (weight, reps) ->
            onSave(weight, reps)
            dialog.dismiss()
        }
    }
    refresh()
}

fun showAddAthleteDialog(context: Context, viewModel: LiftViewModel, onAdded: (Long) -> Unit) {
    val b = DialogAddAthleteBinding.inflate(LayoutInflater.from(context))

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Add athlete")
        .setView(b.root)
        .setPositiveButton("Add", null)
        .setNegativeButton("Cancel", null)
        .show()
    val add = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun bodyWeight() = b.bodyWeightInput.text?.toString()?.toFloatOrNull()?.takeIf { it > 0f }
    fun refresh() {
        add.isEnabled = !b.nameInput.text.isNullOrBlank() && !b.positionInput.text.isNullOrBlank() && bodyWeight() != null
    }

    b.nameInput.doAfterTextChanged { refresh() }
    b.positionInput.doAfterTextChanged { refresh() }
    b.bodyWeightInput.doAfterTextChanged { refresh() }
    add.setOnClickListener {
        val weight = bodyWeight() ?: return@setOnClickListener
        viewModel.addAthlete(b.nameInput.text.toString(), b.positionInput.text.toString(), weight, onAdded)
        dialog.dismiss()
    }
    refresh()
}
