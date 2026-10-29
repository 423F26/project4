package com.example.liftingapp.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.MAX_REPS
import com.example.liftingapp.data.MAX_SETS
import com.example.liftingapp.data.Strength
import com.example.liftingapp.data.WORKOUT_EXERCISE_SUGGESTIONS
import com.example.liftingapp.data.WorkoutExercise
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.isValidEmail
import com.example.liftingapp.data.isValidPhone
import com.example.liftingapp.data.orNullIfBlank
import com.example.liftingapp.databinding.DialogAddAthleteBinding
import com.example.liftingapp.databinding.DialogLogSetBinding
import com.example.liftingapp.databinding.DialogWorkoutExerciseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Replaces project4's showAddWeightDialog(): same FAB -> dialog flow, but asks for reps too
 * (needed for e1RM) and shows the estimate live. Save stays disabled until the input is valid.
 *
 * Pass [initial] to edit an existing set instead: the fields start filled in with its weight and reps.
 */
fun showLogSetDialog(
    context: Context,
    athlete: Athlete,
    exercise: String,
    initial: LiftEntry? = null,
    onSave: (weight: Float, reps: Int) -> Unit
) {
    val b = DialogLogSetBinding.inflate(LayoutInflater.from(context))
    b.repsLayout.helperText = "1–$MAX_REPS"
    if (initial != null) {
        b.weightInput.setText(initial.weight.lb())
        b.repsInput.setText(initial.reps.toString())
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (initial == null) "Log $exercise for ${athlete.name}" else "Edit $exercise for ${athlete.name}")
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

/**
 * Add one exercise to a workout, or edit one ([initial]). Same pattern as showLogSetDialog:
 * the save button stays disabled until the exercise has a name, 1–[MAX_SETS] sets and reps,
 * and a live preview shows the line the athlete will get.
 */
fun showExerciseDialog(context: Context, initial: WorkoutExercise? = null, onSave: (WorkoutExercise) -> Unit) {
    val b = DialogWorkoutExerciseBinding.inflate(LayoutInflater.from(context))
    b.nameInput.setAdapter(ArrayAdapter(context, R.layout.item_spinner_dropdown, WORKOUT_EXERCISE_SUGGESTIONS.toMutableList()))
    b.setsLayout.helperText = "1–$MAX_SETS"
    if (initial != null) {
        b.nameInput.setText(initial.name, false)   // false: don't pop the suggestion list open
        b.setsInput.setText(initial.sets.toString())
        b.repsInput.setText(initial.reps)
        b.loadInput.setText(initial.load)
        b.notesInput.setText(initial.notes)
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (initial == null) "Add exercise" else "Edit exercise")
        .setView(b.root)
        .setPositiveButton(if (initial == null) "Add" else "Save", null)   // click handled below
        .setNegativeButton("Cancel", null)
        .show()
    val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun parsed(): WorkoutExercise? {
        val name = b.nameInput.text?.toString()?.trim().orEmpty()
        val sets = b.setsInput.text?.toString()?.toIntOrNull()?.takeIf { it in 1..MAX_SETS }
        val reps = b.repsInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty() || sets == null || reps.isEmpty()) return null
        val base = initial ?: WorkoutExercise(name = name, sets = sets, reps = reps)
        return base.copy(
            name = name,
            sets = sets,
            reps = reps,
            load = b.loadInput.text?.toString()?.trim().orEmpty(),
            notes = b.notesInput.text?.toString()?.trim().orEmpty()
        )
    }

    fun refresh() {
        val exercise = parsed()
        save.isEnabled = exercise != null
        val setsText = b.setsInput.text?.toString().orEmpty()
        val setsOk = setsText.toIntOrNull()?.let { it in 1..MAX_SETS } == true
        b.setsLayout.error = if (setsText.isNotEmpty() && !setsOk) "1–$MAX_SETS" else null
        b.preview.text = exercise?.let { WorkoutShare.exerciseLine(it, times = " × ") }.orEmpty()
    }

    b.nameInput.doAfterTextChanged { refresh() }
    b.setsInput.doAfterTextChanged { refresh() }
    b.repsInput.doAfterTextChanged { refresh() }
    b.loadInput.doAfterTextChanged { refresh() }
    b.notesInput.doAfterTextChanged { refresh() }
    save.setOnClickListener {
        parsed()?.let { exercise ->
            onSave(exercise)
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
    fun phone() = b.phoneInput.text?.toString().orEmpty()
    fun email() = b.emailInput.text?.toString().orEmpty()
    fun refresh() {
        // Phone and email are optional, but if one is typed it has to look real.
        val phoneOk = isValidPhone(phone())
        val emailOk = isValidEmail(email())
        b.phoneLayout.error = if (phoneOk) null else "Enter a phone number, e.g. 406-555-0100"
        b.emailLayout.error = if (emailOk) null else "Enter an email address, e.g. name@example.com"
        add.isEnabled = !b.nameInput.text.isNullOrBlank() && !b.positionInput.text.isNullOrBlank() &&
            bodyWeight() != null && phoneOk && emailOk
    }

    b.nameInput.doAfterTextChanged { refresh() }
    b.positionInput.doAfterTextChanged { refresh() }
    b.bodyWeightInput.doAfterTextChanged { refresh() }
    b.phoneInput.doAfterTextChanged { refresh() }
    b.emailInput.doAfterTextChanged { refresh() }
    add.setOnClickListener {
        val weight = bodyWeight() ?: return@setOnClickListener
        viewModel.addAthlete(
            name = b.nameInput.text.toString(),
            positionGroup = b.positionInput.text.toString(),
            bodyWeightLb = weight,
            phone = phone().orNullIfBlank(),
            email = email().orNullIfBlank(),
            onAdded = onAdded
        )
        dialog.dismiss()
    }
    refresh()
}
