package com.example.liftingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.CloudErrors
import com.example.liftingapp.data.DAY_MS
import com.example.liftingapp.data.SessionExercise
import com.example.liftingapp.data.WorkoutExercise
import com.example.liftingapp.data.WorkoutSession
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.databinding.ActivityWorkoutSessionBinding
import com.example.liftingapp.databinding.ItemSessionExerciseBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.firstName
import com.example.liftingapp.ui.showExerciseDialog
import com.example.liftingapp.ui.showLogSetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * A logged workout's checklist: tap an exercise to check it off, or Log set to record the weight
 * (which also goes on the Log tab's chart, dated on the workout's day). The date can be changed,
 * exercises added, and the whole thing marked done or deleted. Opened from the Log tab's workout
 * log and right after logging a workout.
 */
class WorkoutSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutSessionBinding
    private lateinit var viewModel: WorkoutSessionViewModel
    private var athlete: Athlete? = null
    private var session: WorkoutSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val athleteId = intent.getStringExtra(EXTRA_ATHLETE_ID)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (athleteId == null || sessionId == null) {
            finish()
            return
        }
        viewModel = ViewModelProvider(this)[WorkoutSessionViewModel::class.java]
        viewModel.start(athleteId, sessionId)

        TooltipCompat.setTooltipText(binding.backButton, binding.backButton.contentDescription)
        binding.backButton.setOnClickListener { finish() }
        binding.moreButton.setOnClickListener { showMenu(it) }
        binding.dateButton.setOnClickListener { pickDay() }
        binding.addExerciseButton.setOnClickListener { addExercise() }
        binding.markAllButton.setOnClickListener {
            val current = session ?: return@setOnClickListener
            viewModel.markAll(!current.isComplete)
        }

        viewModel.athlete.observe(this) { loaded ->
            athlete = loaded
            render()
        }
        viewModel.profile.observe(this) { render() }
        viewModel.session.observe(this) { loaded ->
            if (loaded == null) return@observe   // still loading
            val current = loaded.session
            if (current == null) {
                Toast.makeText(this, "That workout was deleted.", Toast.LENGTH_SHORT).show()
                finish()
                return@observe
            }
            session = current
            render()
        }
        CloudErrors.messages.observe(this) { event -> event.take()?.let(::showMessage) }
    }

    private fun render() {
        val current = session ?: return
        val canEdit = viewModel.canEdit
        binding.loadingText.isVisible = false
        binding.workoutName.text = current.workoutName
        binding.athleteText.text = athlete?.let { "${it.name} · ${viewModel.scope.title}" } ?: viewModel.scope.title
        binding.dateButton.text = dayFormat("EEE, MMM d, yyyy").format(Date(current.day * DAY_MS))
        binding.dateButton.isEnabled = canEdit
        binding.sourceText.text = current.source.takeIf { it.isNotBlank() }?.let { "From $it" }.orEmpty()

        val total = current.exercises.size
        binding.progressText.text = when {
            total == 0 -> "No exercises yet"
            current.isComplete -> "All $total done"
            else -> "${current.doneCount} of $total done"
        }
        binding.progressBar.setProgressCompat(if (total == 0) 0 else (current.doneCount * 100f / total).roundToInt(), true)
        binding.notesText.text = current.notes
        binding.notesText.isVisible = current.notes.isNotBlank()

        binding.editButtons.isVisible = canEdit
        binding.markAllButton.text = if (current.isComplete) "Clear checks" else "Mark all done"
        binding.moreButton.isVisible = canEdit
        binding.readOnlyNote.isVisible = !canEdit
        binding.readOnlyNote.text = athlete?.let { "Only coaches and ${it.name.firstName()} can check off this workout." }
            ?: "Only coaches and the athlete can check off this workout."

        renderExercises(current, canEdit)
    }

    /** Rebuilds the checklist. Workouts are short, so redrawing every row is fine. */
    private fun renderExercises(current: WorkoutSession, canEdit: Boolean) {
        val list = binding.exerciseList
        list.removeAllViews()
        current.exercises.forEachIndexed { index, exercise ->
            val row = ItemSessionExerciseBinding.inflate(layoutInflater, list, false)
            row.name.text = exercise.name
            row.prescription.text = WorkoutShare.prescription(exercise.asWorkoutExercise(), times = " × ")
            row.cue.text = exercise.notes
            row.cue.isVisible = exercise.notes.isNotBlank()
            row.result.text = "Logged: ${exercise.result}"
            row.result.isVisible = exercise.result.isNotBlank()
            row.check.isChecked = exercise.done
            row.check.isEnabled = canEdit
            row.check.contentDescription = "${exercise.name}, ${if (exercise.done) "done" else "not done"}"
            row.name.alpha = if (exercise.done) 0.6f else 1f
            row.prescription.alpha = row.name.alpha
            row.logSetButton.isVisible = canEdit
            row.logSetButton.contentDescription = "Log a set of ${exercise.name}"
            if (canEdit) {
                // The checkbox and the whole row toggle; the listener is set after isChecked so binding doesn't fire it.
                row.check.setOnClickListener { viewModel.toggle(index) }
                row.root.setOnClickListener { viewModel.toggle(index) }
                row.root.setOnLongClickListener { confirmRemove(index, exercise); true }
                row.logSetButton.setOnClickListener { logSet(index, exercise) }
            }
            list.addView(row.root)
        }
    }

    private fun logSet(index: Int, exercise: SessionExercise) {
        val who = athlete ?: return
        val prescription = WorkoutShare.prescription(exercise.asWorkoutExercise(), times = " × ")
        showLogSetDialog(this, who, exercise.name, subtitle = "Today's plan: $prescription") { weight, reps ->
            viewModel.logSet(index, weight, reps)
            showMessage("Logged ${exercise.name} ${weight.formatWeight()} × $reps for ${who.name.firstName()}")
        }
    }

    private fun addExercise() {
        showExerciseDialog(this, suggestions = viewModel.catalog) { exercise ->
            viewModel.addExercise(
                SessionExercise(name = exercise.name, sets = exercise.sets, reps = exercise.reps, load = exercise.load, notes = exercise.notes)
            )
        }
    }

    private fun confirmRemove(index: Int, exercise: SessionExercise) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${exercise.name}?")
            .setMessage("It comes off this day's checklist only. Sets already logged stay on the chart.")
            .setPositiveButton("Remove") { _, _ -> viewModel.removeExercise(index) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDay() {
        val current = session ?: return
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Workout date")
            .setSelection(current.day * DAY_MS)
            .build()
        picker.addOnPositiveButtonClickListener { selection -> viewModel.changeDay(Math.floorDiv(selection, DAY_MS)) }
        picker.show(supportFragmentManager, "session_day")
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_DATE, 0, "Change date")
            menu.add(0, MENU_DELETE, 1, "Delete workout")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_DATE -> pickDay()
                    MENU_DELETE -> confirmDelete()
                }
                true
            }
        }.show()
    }

    private fun confirmDelete() {
        val current = session ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this workout?")
            .setMessage("${current.workoutName} comes off the workout log. Sets logged from it stay on the chart.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete()
                Toast.makeText(this, "Deleted ${current.workoutName}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun SessionExercise.asWorkoutExercise() = WorkoutExercise(name = name, sets = sets, reps = reps, load = load, notes = notes)

    private fun Float.formatWeight(): String = if (this % 1f == 0f) toInt().toString() else String.format(Locale.US, "%.1f", this)

    private fun dayFormat(pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }

    companion object {
        private const val EXTRA_ATHLETE_ID = "athlete_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val MENU_DATE = 1
        private const val MENU_DELETE = 2

        fun intent(context: Context, athleteId: String, sessionId: String): Intent =
            Intent(context, WorkoutSessionActivity::class.java)
                .putExtra(EXTRA_ATHLETE_ID, athleteId)
                .putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
