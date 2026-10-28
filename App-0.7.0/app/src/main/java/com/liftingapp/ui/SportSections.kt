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
import com.example.liftingapp.data.BASKETBALL
import com.example.liftingapp.data.FOOTBALL
import com.example.liftingapp.data.Season
import com.example.liftingapp.data.SportDay
import com.example.liftingapp.data.SportSection
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.databinding.ItemWorkoutBinding
import com.example.liftingapp.databinding.TabWorkoutsBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Sport sections at the top of the Workouts tab: General | Football | Basketball.
 *  - General shows the regular workout library (WorkoutsTab, unchanged).
 *  - A sport shows its plan instead: season toggle (Off-season / In-season), a week of the cycle
 *    for sports that rotate weekly (football), and one card per training day. Cards use the same
 *    layout as the library and can be sent to an athlete with the same Send dialog; they're
 *    read-only (no edit/delete).
 *
 * Kept separate from WorkoutsTab so the library code doesn't change. Wired up in MainActivity.
 * To add a sport: add its SportSection in data/, a button in tab_workouts.xml, and a line in [sportButtons].
 */
class SportSections(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel
) {
    private val prefs = activity.getSharedPreferences("sport_sections", Context.MODE_PRIVATE)
    private var athletes: List<Athlete> = emptyList()
    private val adapter = SportDayAdapter(::send)

    /** Sport toggle buttons -> their plans. General (the library) isn't in here. */
    private val sportButtons = mapOf(
        R.id.sportFootball to FOOTBALL,
        R.id.sportBasketball to BASKETBALL
    )
    private val seasonButtons = listOf(R.id.seasonOff, R.id.seasonIn)
    private val weekButtons = listOf(R.id.week1, R.id.week2, R.id.week3, R.id.week4)

    /** The selected sport, or null for General. */
    private var sport: SportSection?
        get() = prefs.getString(KEY_SPORT, null)?.let { name -> sportButtons.values.firstOrNull { it.sport == name } }
        set(value) = prefs.edit { putString(KEY_SPORT, value?.sport) }

    // Season and week are remembered per sport.
    private fun seasonIndex(s: SportSection) = prefs.getInt("${s.sport}_season", 0).coerceIn(0, s.seasons.lastIndex)
    private fun weekIndex(s: SportSection) = prefs.getInt("${s.sport}_week", 0).coerceIn(0, weekButtons.lastIndex)

    init {
        binding.sportWorkoutList.adapter = adapter

        binding.seasonToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val s = sport ?: return@addOnButtonCheckedListener
            val index = seasonButtons.indexOf(checkedId)
            if (isChecked && index in s.seasons.indices) {
                prefs.edit { putInt("${s.sport}_season", index) }
                render()
            }
        }
        binding.weekToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val s = sport ?: return@addOnButtonCheckedListener
            val index = weekButtons.indexOf(checkedId)
            if (isChecked && index >= 0) {
                prefs.edit { putInt("${s.sport}_week", index) }
                render()
            }
        }

        val selected = sport
        binding.sportToggle.check(sportButtons.entries.firstOrNull { it.value == selected }?.key ?: R.id.sportGeneral)
        binding.sportToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sport = sportButtons[checkedId]
            showSection()
        }

        viewModel.athletes.observe(activity) { athletes = it }
        showSection()
    }

    /** Swaps between the library (General) and a sport's plan. Only visibility changes. */
    private fun showSection() {
        val s = sport
        val sportSelected = s != null
        binding.sportSection.isVisible = sportSelected
        binding.filterToggle.isVisible = !sportSelected
        binding.workoutList.isVisible = !sportSelected
        binding.newWorkoutFab.isVisible = !sportSelected
        binding.workoutsEmptyText.isVisible = !sportSelected && (binding.workoutList.adapter?.itemCount ?: 0) == 0
        if (s == null) return

        // Point the season and week toggles at this sport's saved choices, then draw.
        listOf(binding.seasonOff, binding.seasonIn).zip(s.seasons).forEach { (button, season) -> button.text = season.name }
        binding.seasonToggle.check(seasonButtons[seasonIndex(s)])
        binding.weekToggle.check(weekButtons[weekIndex(s)])
        binding.sportSource.text = "Source: ${s.source}"
        render()
    }

    private fun render() {
        val s = sport ?: return
        val season = s.seasons[seasonIndex(s)]
        val week = weekIndex(s)
        binding.weekToggle.isVisible = season.hasWeeks
        binding.seasonAbout.text =
            if (season.hasWeeks) "${season.about}\nWeek ${week + 1} main lifts: ${season.cycle[week].label}"
            else season.about
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
        const val KEY_SPORT = "sport"
    }
}

/** One card per training day, drawn with the library's item_workout layout. */
private class SportDayAdapter(
    private val onSend: (WorkoutWithExercises) -> Unit
) : RecyclerView.Adapter<SportDayAdapter.Holder>() {

    class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<WorkoutWithExercises> = emptyList()
    private var days: List<SportDay> = emptyList()

    @android.annotation.SuppressLint("NotifyDataSetChanged")   // a handful of cards; they all change together
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
