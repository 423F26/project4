package com.example.liftingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.databinding.ActivityWorkoutEditorBinding
import com.example.liftingapp.databinding.ItemWorkoutExerciseEditBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.showExerciseDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Create or edit a workout: name, notes for the athlete, and an ordered list of exercises.
 * Opened from the Workouts tab (New workout, the pencil, or tapping a card). Save writes the
 * workout and its exercises in one transaction and returns to the tab; leaving with unsaved
 * changes asks first.
 */
class WorkoutEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutEditorBinding
    private lateinit var viewModel: WorkoutEditorViewModel

    /** Whether this activity instance has filled in its views from the ViewModel yet. */
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)   // must happen before any views are inflated
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[WorkoutEditorViewModel::class.java]
        val workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, NO_ID).takeIf { it != NO_ID }
        viewModel.start(workoutId)

        binding.screenTitle.text = if (workoutId == null) "New workout" else "Edit workout"
        TooltipCompat.setTooltipText(binding.closeButton, binding.closeButton.contentDescription)
        binding.closeButton.setOnClickListener { leave() }
        binding.saveButton.setOnClickListener { save() }
        binding.addExerciseButton.setOnClickListener { editExercise(null) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        viewModel.ready.observe(this) { ready -> if (ready && !bound) bindDraft() }
        viewModel.saved.observe(this) { saved ->
            if (saved == null) return@observe
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_SAVED_ID, saved.id)
                    .putExtra(EXTRA_SAVED_NAME, saved.name)
                    .putExtra(EXTRA_WAS_NEW, saved.wasNew)
            )
            finish()
        }
    }

    /** Fills the fields from the draft once it has loaded, then keeps the draft in sync with typing. */
    private fun bindDraft() {
        bound = true
        if (viewModel.notFound) {
            Toast.makeText(this, "That workout was deleted.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.nameInput.setText(viewModel.name)
        binding.notesInput.setText(viewModel.notes)
        binding.nameInput.doAfterTextChanged {
            viewModel.name = it?.toString().orEmpty()
            binding.nameLayout.error = null
        }
        binding.notesInput.doAfterTextChanged { viewModel.notes = it?.toString().orEmpty() }
        binding.premadeNote.isVisible = viewModel.isPremade
        renderExercises()
    }

    /** Rebuilds the exercise rows from the draft. Workouts are short, so redrawing them all is fine. */
    private fun renderExercises() {
        val exercises = viewModel.exercises
        val list = binding.exerciseList
        list.removeAllViews()
        exercises.forEachIndexed { index, exercise ->
            val row = ItemWorkoutExerciseEditBinding.inflate(layoutInflater, list, false)
            row.number.text = "${index + 1}"
            row.title.text = exercise.name
            row.detail.text = WorkoutShare.prescription(exercise, times = " × ")
            row.cue.text = exercise.notes
            row.cue.isVisible = exercise.notes.isNotBlank()
            row.root.setOnClickListener { editExercise(index) }
            row.moreButton.contentDescription = "More options for ${exercise.name}"
            TooltipCompat.setTooltipText(row.moreButton, "More options")
            row.moreButton.setOnClickListener { showRowMenu(it, index) }
            list.addView(row.root)
        }
        binding.exercisesEmpty.isVisible = exercises.isEmpty()
        binding.exerciseCount.text = when (exercises.size) {
            0 -> ""
            1 -> "1 exercise"
            else -> "${exercises.size} exercises"
        }
    }

    /** [index] null = add a new exercise at the end. */
    private fun editExercise(index: Int?) {
        if (!bound) return
        val initial = index?.let { viewModel.exercises.getOrNull(it) }
        showExerciseDialog(this, initial) { exercise ->
            if (index == null) viewModel.addExercise(exercise) else viewModel.replaceExercise(index, exercise)
            renderExercises()
        }
    }

    private fun showRowMenu(anchor: View, index: Int) {
        val last = viewModel.exercises.lastIndex
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_EDIT, 0, "Edit")
            if (index > 0) menu.add(0, MENU_UP, 1, "Move up")
            if (index < last) menu.add(0, MENU_DOWN, 2, "Move down")
            menu.add(0, MENU_REMOVE, 3, "Remove")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> editExercise(index)
                    MENU_UP -> { viewModel.moveExercise(index, index - 1); renderExercises() }
                    MENU_DOWN -> { viewModel.moveExercise(index, index + 1); renderExercises() }
                    MENU_REMOVE -> removeExercise(index)
                }
                true
            }
        }.show()
    }

    private fun removeExercise(index: Int) {
        val removed = viewModel.removeExercise(index)
        renderExercises()
        Snackbar.make(binding.root, "Removed ${removed.name}", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                viewModel.insertExercise(index, removed)
                renderExercises()
            }
            .show()
    }

    private fun save() {
        if (!bound) return
        if (viewModel.name.isBlank()) {
            binding.nameLayout.error = "Give the workout a name"
            binding.nameInput.requestFocus()
            return
        }
        if (viewModel.exercises.isEmpty()) {
            Snackbar.make(binding.root, "Add at least one exercise before saving.", Snackbar.LENGTH_LONG).show()
            return
        }
        viewModel.save()
    }

    /** Close button and system Back: ask before throwing away unsaved changes. */
    private fun leave() {
        if (!bound || !viewModel.hasChanges()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard changes?")
            .setMessage(if (viewModel.isNew) "This workout hasn't been saved." else "Your changes to this workout won't be saved.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    companion object {
        private const val EXTRA_WORKOUT_ID = "workout_id"
        private const val NO_ID = 0L   // Room never gives a saved workout id 0

        /** Result extras for the Workouts tab. */
        const val EXTRA_SAVED_ID = "saved_id"
        const val EXTRA_SAVED_NAME = "saved_name"
        const val EXTRA_WAS_NEW = "was_new"

        private const val MENU_EDIT = 1
        private const val MENU_UP = 2
        private const val MENU_DOWN = 3
        private const val MENU_REMOVE = 4

        /** [workoutId] null = new workout. */
        fun intent(context: Context, workoutId: Long?): Intent =
            Intent(context, WorkoutEditorActivity::class.java).apply {
                if (workoutId != null) putExtra(EXTRA_WORKOUT_ID, workoutId)
            }
    }
}
