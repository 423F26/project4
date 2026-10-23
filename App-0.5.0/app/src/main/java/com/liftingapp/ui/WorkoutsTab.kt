package com.example.liftingapp.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.WorkoutEditorActivity
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.WorkoutFilter
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.databinding.ItemWorkoutBinding
import com.example.liftingapp.databinding.TabWorkoutsBinding
import com.google.android.material.snackbar.Snackbar

/**
 * The coach's workout library: premade workouts plus the ones they made, filtered by the
 * All / Premade / Mine toggle. Each card can be sent (text, email or another app), edited
 * or deleted (with Undo). New workout and Edit open WorkoutEditorActivity.
 */
class WorkoutsTab(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel
) {
    private var athletes: List<Athlete> = emptyList()

    private val adapter = WorkoutAdapter(
        onEdit = { openEditor(it.workout.id) },
        onSend = ::send,
        onDelete = ::delete
    )

    private val filterButtons = mapOf(
        R.id.filterAll to WorkoutFilter.ALL,
        R.id.filterPremade to WorkoutFilter.PREMADE,
        R.id.filterMine to WorkoutFilter.MINE
    )

    /**
     * Opens WorkoutEditorActivity. Registered here (during MainActivity.onCreate) so the result
     * still arrives after a rotation. After a save, the snackbar offers Send so a coach can make
     * a workout and send it in one go.
     */
    private val editor = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val id = data.getLongExtra(WorkoutEditorActivity.EXTRA_SAVED_ID, 0L)
        val name = data.getStringExtra(WorkoutEditorActivity.EXTRA_SAVED_NAME).orEmpty()
        val wasNew = data.getBooleanExtra(WorkoutEditorActivity.EXTRA_WAS_NEW, false)
        // A new workout is never premade, so don't leave the coach looking at a list it isn't in.
        if (wasNew && viewModel.currentWorkoutFilter == WorkoutFilter.PREMADE) binding.filterToggle.check(R.id.filterAll)
        Snackbar.make(binding.root, "Saved “$name”", Snackbar.LENGTH_LONG)
            .setAnchorView(binding.newWorkoutFab)
            .setAction("Send") { viewModel.loadWorkout(id) { item -> if (!activity.isDestroyed) send(item) } }
            .show()
    }

    init {
        binding.workoutList.adapter = adapter
        setupFilter()
        binding.newWorkoutFab.setOnClickListener { openEditor(null) }

        viewModel.athletes.observe(activity) { athletes = it }
        viewModel.workouts.observe(activity) { state ->
            adapter.submitList(state.workouts)
            binding.workoutsEmptyText.text = when (state.filter) {
                WorkoutFilter.ALL -> "No workouts yet. Tap New workout to make one."
                WorkoutFilter.PREMADE -> "No premade workouts left. Workouts you make are under Mine."
                WorkoutFilter.MINE -> "You haven't made a workout yet. Tap New workout to build one."
            }
            binding.workoutsEmptyText.isVisible = state.workouts.isEmpty()
        }
    }

    private fun setupFilter() {
        val selected = filterButtons.entries.firstOrNull { it.value == viewModel.currentWorkoutFilter }?.key ?: R.id.filterAll
        binding.filterToggle.check(selected)
        binding.filterToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val filter = filterButtons[checkedId]
            if (isChecked && filter != null) viewModel.setWorkoutFilter(filter)
        }
    }

    /** [workoutId] null = new workout. */
    private fun openEditor(workoutId: Long?) {
        editor.launch(WorkoutEditorActivity.intent(activity, workoutId))
    }

    private fun send(item: WorkoutWithExercises) {
        showSendWorkoutDialog(
            context = activity,
            item = item,
            athletes = athletes,
            onAthleteChanged = viewModel::updateAthlete,
            onProblem = ::showMessage
        )
    }

    private fun delete(item: WorkoutWithExercises) {
        viewModel.deleteWorkout(item)
        Snackbar.make(binding.root, "Deleted “${item.workout.name}”", Snackbar.LENGTH_LONG)
            .setAnchorView(binding.newWorkoutFab)   // keeps the snackbar above the New workout button
            .setAction("Undo") { viewModel.restoreWorkout(item) }
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.newWorkoutFab)
            .show()
    }
}

/** How many exercise lines a card shows before the last line becomes "+N more". */
private const val PREVIEW_LINES = 5

/** Same workout = same id; redraw the card when anything in it changed. Lets deletes and Undo animate. */
private object WorkoutDiff : DiffUtil.ItemCallback<WorkoutWithExercises>() {
    override fun areItemsTheSame(oldItem: WorkoutWithExercises, newItem: WorkoutWithExercises) =
        oldItem.workout.id == newItem.workout.id

    override fun areContentsTheSame(oldItem: WorkoutWithExercises, newItem: WorkoutWithExercises) =
        oldItem == newItem
}

private class WorkoutAdapter(
    private val onEdit: (WorkoutWithExercises) -> Unit,
    private val onSend: (WorkoutWithExercises) -> Unit,
    private val onDelete: (WorkoutWithExercises) -> Unit
) : ListAdapter<WorkoutWithExercises, WorkoutAdapter.Holder>(WorkoutDiff) {

    class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        val b = holder.binding
        TooltipCompat.setTooltipText(b.editButton, b.editButton.contentDescription)
        TooltipCompat.setTooltipText(b.deleteButton, b.deleteButton.contentDescription)

        fun View.onItemClick(action: (WorkoutWithExercises) -> Unit) = setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) action(getItem(position))
        }
        b.root.onItemClick(onEdit)
        b.editButton.onItemClick(onEdit)
        b.sendButton.onItemClick(onSend)
        b.deleteButton.onItemClick(onDelete)
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val workout = item.workout
        val exercises = item.exercises
        val b = holder.binding

        b.name.text = workout.name
        b.premadeBadge.isVisible = workout.isPremade

        val count = if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises"
        b.meta.text = if (workout.isPremade) count else "$count · Updated ${workout.updatedAt.monthDay()}"

        b.notes.text = workout.notes
        b.notes.isVisible = workout.notes.isNotBlank()

        val shown = if (exercises.size > PREVIEW_LINES) exercises.take(PREVIEW_LINES - 1) else exercises
        val more = exercises.size - shown.size
        b.exercises.text = buildString {
            append(shown.joinToString("\n") { WorkoutShare.exerciseLine(it, times = " × ") })
            if (more > 0) append("\n+").append(more).append(" more")
        }
        b.exercises.isVisible = exercises.isNotEmpty()

        // Say which workout each button acts on, for TalkBack.
        b.sendButton.contentDescription = "Send ${workout.name}"
        b.editButton.contentDescription = "Edit ${workout.name}"
        b.deleteButton.contentDescription = "Delete ${workout.name}"
    }
}
