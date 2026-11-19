package com.example.liftingapp.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import com.example.liftingapp.R
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.AthleteDetails
import com.example.liftingapp.data.Cardio
import com.example.liftingapp.data.DistanceUnit
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
 * [subtitle] adds a line under the title, e.g. the workout's prescription when logging from a workout.
 */
fun showLogSetDialog(
    context: Context,
    athlete: Athlete,
    exercise: String,
    initial: LiftEntry? = null,
    subtitle: String? = null,
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
        .apply { if (!subtitle.isNullOrBlank()) setMessage(subtitle) }
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
 * The cardio version of showLogSetDialog (a run, ride, row or swim): distance and time instead of
 * weight and reps, with the average pace shown live. The Miles / Kilometers switch converts what's
 * typed and is remembered (DistanceUnits); [onSave] always gets meters and seconds, plus the unit
 * in use for messages.
 */
fun showLogCardioDialog(
    context: Context,
    athlete: Athlete,
    exercise: String,
    initial: LiftEntry? = null,
    subtitle: String? = null,
    onSave: (meters: Double, seconds: Long, unit: DistanceUnit) -> Unit
) {
    val b = DialogLogSetBinding.inflate(LayoutInflater.from(context))
    b.liftGroup.isVisible = false
    b.cardioGroup.isVisible = true
    var unit = DistanceUnits.current(context)
    val unitButtons = mapOf(R.id.unitMiles to DistanceUnit.MILES, R.id.unitKilometers to DistanceUnit.KILOMETERS)
    b.unitToggle.check(unitButtons.entries.first { it.value == unit }.key)
    b.distanceLayout.hint = "Distance (${unit.short})"
    if (initial != null && initial.isCardio) {
        b.distanceInput.setText(Cardio.distanceInput(initial.distanceMeters, unit))
        b.timeInput.setText(Cardio.formatDuration(initial.durationSeconds))
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (initial == null) "Log $exercise for ${athlete.name}" else "Edit $exercise for ${athlete.name}")
        .apply { if (!subtitle.isNullOrBlank()) setMessage(subtitle) }
        .setView(b.root)
        .setPositiveButton("Save", null)   // click handled below so invalid input doesn't close the dialog
        .setNegativeButton("Cancel", null)
        .show()
    val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun distanceText() = b.distanceInput.text?.toString().orEmpty()
    fun timeText() = b.timeInput.text?.toString().orEmpty()
    fun meters() = Cardio.parseDistance(distanceText(), unit)
    fun seconds() = Cardio.parseDuration(timeText())

    fun refresh() {
        val meters = meters()
        val seconds = seconds()
        save.isEnabled = meters != null && seconds != null
        b.distanceLayout.error = if (distanceText().isNotBlank() && meters == null) "Enter a distance in ${unit.long}, e.g. 3.1" else null
        b.timeLayout.error = if (timeText().isNotBlank() && seconds == null) "Enter a time like 24:30 or 1:05:00" else null
        b.e1rmPreview.text = if (meters != null && seconds != null) "Average pace: ${Cardio.formatPace(meters, seconds, unit)}" else ""
    }

    b.unitToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
        val chosen = unitButtons[checkedId]
        if (!isChecked || chosen == null || chosen == unit) return@addOnButtonCheckedListener
        // Keep the same distance: 5 (km) becomes 3.11 (mi), not 5 miles.
        val meters = meters()
        unit = chosen
        DistanceUnits.save(context, chosen)
        b.distanceLayout.hint = "Distance (${unit.short})"
        if (meters != null) b.distanceInput.setText(Cardio.distanceInput(meters, unit))
        refresh()
    }
    b.distanceInput.doAfterTextChanged { refresh() }
    b.timeInput.doAfterTextChanged { refresh() }
    save.setOnClickListener {
        val meters = meters() ?: return@setOnClickListener
        val seconds = seconds() ?: return@setOnClickListener
        onSave(meters, seconds, unit)
        dialog.dismiss()
    }
    refresh()
}

/** Asked when someone adds a new exercise: is it logged as weight × reps or as distance + time? */
fun showExerciseKindDialog(context: Context, name: String, onPick: (cardio: Boolean) -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setTitle("How do you log $name?")
        .setItems(arrayOf("Weight and reps (a lift)", "Distance and time (cardio)")) { _, which -> onPick(which == 1) }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Add one exercise to a workout, or edit one ([initial]). [suggestions] is the app's exercise list
 * (the same one the Log tab offers, including custom exercises). Same pattern as showLogSetDialog:
 * the save button stays disabled until the exercise has a name, 1–[MAX_SETS] sets and reps,
 * and a live preview shows the line the athlete will get.
 */
fun showExerciseDialog(
    context: Context,
    initial: WorkoutExercise? = null,
    suggestions: List<String> = WORKOUT_EXERCISE_SUGGESTIONS,
    onSave: (WorkoutExercise) -> Unit
) {
    val b = DialogWorkoutExerciseBinding.inflate(LayoutInflater.from(context))
    b.nameInput.setAdapter(ArrayAdapter(context, R.layout.item_spinner_dropdown, suggestions.toMutableList()))
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

/**
 * Add an athlete, or edit one (start from [initial]). Only the name is required; position and
 * body weight are optional (body weight is needed for pound-for-pound), and any phone or email
 * typed has to look real.
 *
 * [intro] adds a line of explanation at the top; [showEmail] = false hides the contact email
 * (editing your own profile: your account's email is used).
 */
fun showAthleteDialog(
    context: Context,
    title: String,
    initial: AthleteDetails? = null,
    intro: String? = null,
    showEmail: Boolean = true,
    saveLabel: String = if (initial == null) "Add" else "Save",
    onSave: (AthleteDetails) -> Unit
) {
    val b = DialogAddAthleteBinding.inflate(LayoutInflater.from(context))
    b.introText.text = intro
    b.introText.isVisible = !intro.isNullOrBlank()
    b.emailLayout.isVisible = showEmail
    if (initial != null) {
        b.nameInput.setText(initial.name)
        b.positionInput.setText(initial.positionGroup)
        if (initial.bodyWeightLb > 0f) b.bodyWeightInput.setText(initial.bodyWeightLb.lb())
        b.phoneInput.setText(initial.phone.orEmpty())
        b.emailInput.setText(initial.email.orEmpty())
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(b.root)
        .setPositiveButton(saveLabel, null)
        .setNegativeButton("Cancel", null)
        .show()
    val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    fun weightText() = b.bodyWeightInput.text?.toString()?.trim().orEmpty()
    /** Blank = not set (0); otherwise it must be a positive number. Null = invalid. */
    fun bodyWeight(): Float? = if (weightText().isEmpty()) 0f else weightText().toFloatOrNull()?.takeIf { it > 0f }
    fun phone() = b.phoneInput.text?.toString().orEmpty()
    fun email() = if (showEmail) b.emailInput.text?.toString().orEmpty() else ""
    fun refresh() {
        // Phone, email and body weight are optional, but if one is typed it has to look real.
        val phoneOk = isValidPhone(phone())
        val emailOk = isValidEmail(email())
        val weightOk = bodyWeight() != null
        b.phoneLayout.error = if (phoneOk) null else "Enter a phone number, e.g. 406-555-0100"
        b.emailLayout.error = if (emailOk) null else "Enter an email address, e.g. name@example.com"
        b.bodyWeightLayout.error = if (weightOk) null else "Enter a weight in pounds, e.g. 185"
        save.isEnabled = !b.nameInput.text.isNullOrBlank() && phoneOk && emailOk && weightOk
    }

    b.nameInput.doAfterTextChanged { refresh() }
    b.bodyWeightInput.doAfterTextChanged { refresh() }
    b.phoneInput.doAfterTextChanged { refresh() }
    b.emailInput.doAfterTextChanged { refresh() }
    save.setOnClickListener {
        val weight = bodyWeight() ?: return@setOnClickListener
        onSave(
            AthleteDetails(
                name = b.nameInput.text.toString().trim(),
                positionGroup = b.positionInput.text.toString().trim().uppercase(),
                bodyWeightLb = weight,
                phone = phone().orNullIfBlank(),
                email = if (showEmail) email().orNullIfBlank() else initial?.email
            )
        )
        dialog.dismiss()
    }
    refresh()
}
