package com.example.liftingapp.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.SPORT_SECTIONS
import com.example.liftingapp.data.Season
import com.example.liftingapp.data.SportDay
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.databinding.ItemWorkoutBinding
import com.example.liftingapp.databinding.TabWorkoutsBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Sport sections at the top of the Workouts tab: General | Football.
 *  - General shows the regular workout library (WorkoutsTab, unchanged).
 *  - A sport shows its plan instead: Off-season / In-season, a week of the 4-week cycle, and one
 *    card per training day. Cards use the same layout as the library and can be sent to an athlete
 *    with the same Send dialog; they're read-only (no edit/delete).
 *
 * Kept separate from WorkoutsTab so the library code doesn't change. Wired up in MainActivity.
 */
class SportSections(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel
) {
    private val prefs = activity.getSharedPreferences("sport_sections", Context.MODE_PRIVATE)
    private val sport = SPORT_SECTIONS.first()   // Football; more sports become more toggle buttons
    private var athletes: List<Athlete> = emptyList()
    private val adapter = SportDayAdapter(::send)

    private val seasonButtons = listOf(R.id.seasonOff, R.id.seasonIn)
    private val weekButtons = listOf(R.id.week1, R.id.week2, R.id.week3, R.id.week4)

    private var seasonIndex: Int
        get() = prefs.getInt(KEY_SEASON, 0).coerceIn(0, sport.seasons.lastIndex)
        set(value) = prefs.edit { putInt(KEY_SEASON, value) }

    private var weekIndex: Int
        get() = prefs.getInt(KEY_WEEK, 0).coerceIn(0, weekButtons.lastIndex)
        set(value) = prefs.edit { putInt(KEY_WEEK, value) }

    init {
        binding.sportWorkoutList.adapter = adapter
        binding.sportSource.text = "Source: ${sport.source}"

        binding.seasonToggle.check(seasonButtons[seasonIndex])
        binding.seasonToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val index = seasonButtons.indexOf(checkedId)
            if (isChecked && index >= 0) { seasonIndex = index; render() }
        }

        binding.weekToggle.check(weekButtons[weekIndex])
        binding.weekToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val index = weekButtons.indexOf(checkedId)
            if (isChecked && index >= 0) { weekIndex = index; render() }
        }

        val showSport = prefs.getBoolean(KEY_SHOW_SPORT, false)
        binding.sportToggle.check(if (showSport) R.id.sportFootball else R.id.sportGeneral)
        binding.sportToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showSection(checkedId == R.id.sportFootball)
        }

        viewModel.athletes.observe(activity) { athletes = it }
        showSection(showSport)
    }

    /** Swaps between the library (General) and the sport plan. Only visibility changes. */
    private fun showSection(sportSelected: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_SPORT, sportSelected) }
        binding.sportSection.isVisible = sportSelected
        binding.filterToggle.isVisible = !sportSelected
        binding.workoutList.isVisible = !sportSelected
        binding.newWorkoutFab.isVisible = !sportSelected
        binding.workoutsEmptyText.isVisible = !sportSelected && (binding.workoutList.adapter?.itemCount ?: 0) == 0
        if (sportSelected) render()
    }

    private fun render() {
        val season = sport.seasons[seasonIndex]
        val week = weekIndex
        binding.seasonAbout.text = "${season.about}\nWeek ${week + 1} main lifts: ${season.cycle[week].label}"
        adapter.submit(season, week)
    }

    private fun send(item: WorkoutWithExercises) {
        showSendWorkoutDialog(
            context = activity,
            item = item,
            athletes = athletes,
            onAthleteChanged = viewModel::updateAthlete,
            onProblem = { message -> Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show() }
        )
    }

    private companion object {
        const val KEY_SHOW_SPORT = "show_sport"
        const val KEY_SEASON = "season"
        const val KEY_WEEK = "week"
    }
}

/** One card per training day, drawn with the library's item_workout layout. */
private class SportDayAdapter(
    private val onSend: (WorkoutWithExercises) -> Unit
) : RecyclerView.Adapter<SportDayAdapter.Holder>() {

    class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<WorkoutWithExercises> = emptyList()
    private var days: List<SportDay> = emptyList()

    @android.annotation.SuppressLint("NotifyDataSetChanged")   // 5 cards; they all change with the week
    fun submit(season: Season, weekIndex: Int) {
        days = season.days
        items = season.days.map { season.workoutFor(it, weekIndex) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        val b = holder.binding
        b.editButton.isVisible = false     // plan days are read-only
        b.deleteButton.isVisible = false
        b.sendButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onSend(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.name.text = days[position].name
        b.premadeBadge.isVisible = false
        val count = item.exercises.size
        b.meta.text = if (count == 1) "1 exercise" else "$count exercises"
        b.notes.text = item.workout.notes
        b.notes.isVisible = item.workout.notes.isNotBlank()
        // Every line, not a preview: a day is short and the athlete needs all of it.
        b.exercises.text = item.exercises.joinToString("\n") { WorkoutShare.exerciseLine(it, times = " × ") }
        b.exercises.isVisible = item.exercises.isNotEmpty()
        b.sendButton.contentDescription = "Send ${item.workout.name}"
    }
}
