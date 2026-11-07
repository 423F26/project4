package com.example.liftingapp.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.WorkoutEditorActivity
import com.example.liftingapp.data.SentWorkout
import com.example.liftingapp.data.TeamWorkout
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.databinding.ItemWorkoutBinding
import com.example.liftingapp.databinding.TabWorkoutsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * The Workouts tab's "Team workouts" view (teams only): workouts sent in the app to you or the
 * whole team, then workouts anyone on the team made (shared through Firestore, so every phone on
 * the team sees them, iPhone too). Anyone can make one; its maker or a coach can edit or delete it.
 * Cards use the library's item_workout layout with Send and Log. Wired up by SportSections.
 */
class TeamWorkoutsSection(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel,
    private val logger: WorkoutLogger,
    private val send: (WorkoutWithExercises) -> Unit
) {
    private var sent: List<SentWorkout> = emptyList()
    private var shared: List<TeamWorkout> = emptyList()
    private val adapter = TeamCardAdapter()

    /** The workout editor in team mode. Registered here (during MainActivity.onCreate) so results survive rotation. */
    private val editor = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val name = data.getStringExtra(WorkoutEditorActivity.EXTRA_SAVED_NAME).orEmpty()
        val id = data.getStringExtra(WorkoutEditorActivity.EXTRA_TEAM_SAVED_ID)
        Snackbar.make(binding.root, "Saved “$name” for the team", Snackbar.LENGTH_LONG)
            .setAnchorView(binding.newWorkoutFab)
            .setAction("Send") { shared.firstOrNull { it.id == id }?.let { send(it.asItem()) } }
            .show()
    }

    init {
        binding.teamWorkoutList.adapter = adapter
        viewModel.teamWorkouts.observe(activity) { shared = it; refresh() }
        viewModel.inbox.observe(activity) { sent = it; refresh() }
    }

    fun setVisible(visible: Boolean) {
        binding.teamSection.isVisible = visible
    }

    fun newWorkout() {
        editor.launch(WorkoutEditorActivity.teamIntent(activity, null))
    }

    private fun refresh() {
        adapter.cards = sent.map { Card.Sent(it) } + shared.map { Card.Shared(it) }
        binding.teamEmptyText.isVisible = adapter.cards.isEmpty()
    }

    private fun open(card: Card) {
        when (card) {
            is Card.Shared ->
                if (viewModel.canEditTeamWorkout(card.workout.createdBy)) {
                    editor.launch(WorkoutEditorActivity.teamIntent(activity, card.workout.id))
                } else {
                    message("Only ${card.workout.createdByName.ifBlank { "its maker" }} or a coach can edit this workout.")
                }
            is Card.Sent -> send(card.sent.asItem())
        }
    }

    private fun delete(card: Card) {
        val (title, action) = when (card) {
            is Card.Shared -> "Delete “${card.workout.name}” for everyone on the team?" to { viewModel.deleteTeamWorkout(card.workout) }
            is Card.Sent -> "Remove “${card.sent.name}” from the sent list for everyone?" to { viewModel.deleteSent(card.sent) }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setPositiveButton("Delete") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun message(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).setAnchorView(binding.newWorkoutFab).show()
    }

    private sealed interface Card {
        val item: WorkoutWithExercises
        data class Sent(val sent: SentWorkout) : Card { override val item get() = sent.asItem() }
        data class Shared(val workout: TeamWorkout) : Card { override val item get() = workout.asItem() }
    }

    private inner class TeamCardAdapter : RecyclerView.Adapter<TeamCardAdapter.Holder>() {
        inner class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

        var cards: List<Card> = emptyList()
            @android.annotation.SuppressLint("NotifyDataSetChanged")   // small list from a live query
            set(value) { field = value; notifyDataSetChanged() }

        override fun getItemCount() = cards.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val holder = Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            val b = holder.binding
            fun android.view.View.onCard(action: (Card) -> Unit) = setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) action(cards[position])
            }
            b.root.onCard(::open)
            b.editButton.onCard(::open)
            b.sendButton.onCard { send(it.item) }
            b.logButton.onCard { card ->
                val source = when (card) {
                    is Card.Sent -> "Sent by ${card.sent.fromName.ifBlank { "a teammate" }}"
                    is Card.Shared -> "Team workout"
                }
                logger.log(card.item, source)
            }
            b.deleteButton.onCard(::delete)
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val card = cards[position]
            val item = card.item
            val b = holder.binding
            val exercises = item.exercises
            b.name.text = item.workout.name
            b.premadeBadge.isVisible = true
            val count = if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises"
            when (card) {
                is Card.Sent -> {
                    val s = card.sent
                    b.premadeBadge.text = when {
                        s.fromUid == viewModel.uid -> "You sent"
                        s.to.isEmpty() -> "Sent to team"
                        else -> "Sent to you"
                    }
                    b.meta.text = if (s.fromUid == viewModel.uid) "$count · To ${s.toName} · ${s.sentAt.monthDay()}"
                                  else "$count · From ${s.fromName.ifBlank { "a teammate" }} · ${s.sentAt.monthDay()}"
                    b.editButton.isVisible = false
                    b.deleteButton.isVisible = s.fromUid == viewModel.uid || viewModel.isCoach
                }
                is Card.Shared -> {
                    val w = card.workout
                    val mine = viewModel.canEditTeamWorkout(w.createdBy)
                    b.premadeBadge.text = "Team"
                    b.meta.text = "$count · By ${w.createdByName.ifBlank { "a teammate" }} · Updated ${w.updatedAt.monthDay()}"
                    b.editButton.isVisible = mine
                    b.deleteButton.isVisible = mine
                }
            }
            b.notes.text = item.workout.notes
            b.notes.isVisible = item.workout.notes.isNotBlank()
            val shown = if (exercises.size > 5) exercises.take(4) else exercises
            val more = exercises.size - shown.size
            b.exercises.text = buildString {
                append(shown.joinToString("\n") { WorkoutShare.exerciseLine(it, times = " × ") })
                if (more > 0) append("\n+").append(more).append(" more")
            }
            b.exercises.isVisible = exercises.isNotEmpty()
            b.sendButton.contentDescription = "Send ${item.workout.name}"
            b.logButton.contentDescription = "Log ${item.workout.name}"
            b.editButton.contentDescription = "Edit ${item.workout.name}"
            b.deleteButton.contentDescription = "Delete ${item.workout.name}"
        }
    }
}
