package com.example.liftingapp.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
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
 * Sport dropdown at the top of the Workouts tab: General, Football, Basketball.
 *  - General shows the regular workout library (WorkoutsTab, unchanged).
 *  - A sport shows its plan instead: a season dropdown (Off-season / In-season), a week dropdown
 *    for sports that rotate weekly (football), and one card per training day. Cards use the same
 *    layout as the library and can be sent to an athlete with the same Send dialog; they're
 *    read-only (no edit/delete).
 *
 * Kept separate from WorkoutsTab so the library code doesn't change. Wired up in MainActivity.
 * To add a sport: add its SportSection in data/ and add it to [sports].
 */
class SportSections(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel
) {
    private val prefs = activity.getSharedPreferences("sport_sections", Context.MODE_PRIVATE)
    private var athletes: List<Athlete> = emptyList()
    private val adapter = SportDayAdapter(::send)

    /** Dropdown order. Index 0 is General (the workout library); the rest are sport plans. */
    private val sports: List<SportSection> = listOf(FOOTBALL, BASKETBALL)
    private val sportAdapter = spinnerAdapter(activity, listOf("General") + sports.map { it.sport })
    private val seasonAdapter = spinnerAdapter(activity, emptyList())
    private val weekAdapter = spinnerAdapter(activity, emptyList())

    /** The selected sport, or null for General. */
    private var sport: SportSection?
        get() = prefs.getString(KEY_SPORT, null)?.let { name -> sports.firstOrNull { it.sport == name } }
        set(value) = prefs.edit { putString(KEY_SPORT, value?.sport) }

    // Season and week are remembered per sport.
    private fun seasonIndex(s: SportSection) = prefs.getInt("${s.sport}_season", 0).coerceIn(0, s.seasons.lastIndex)
    private fun weekIndex(s: SportSection, season: Season) =
        if (season.hasWeeks) prefs.getInt("${s.sport}_week", 0).coerceIn(0, season.cycle.lastIndex) else 0

    init {
        binding.sportWorkoutList.adapter = adapter

        binding.sportSpinner.adapter = sportAdapter
        binding.sportSpinner.setSelection(sport?.let { sports.indexOf(it) + 1 } ?: 0, false)
        binding.sportSpinner.onItemSelected { position ->
            val picked = sports.getOrNull(position - 1)
            if (picked != sport) { sport = picked; showSection() }
        }

        binding.seasonSpinner.adapter = seasonAdapter
        binding.seasonSpinner.onItemSelected { position ->
            val s = sport ?: return@onItemSelected
            if (position != seasonIndex(s) && position in s.seasons.indices) {
                prefs.edit { putInt("${s.sport}_season", position) }
                render()
            }
        }

        binding.weekSpinner.adapter = weekAdapter
        binding.weekSpinner.onItemSelected { position ->
            val s = sport ?: return@onItemSelected
            if (position != prefs.getInt("${s.sport}_week", 0)) {
                prefs.edit { putInt("${s.sport}_week", position) }
                render()
            }
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

        seasonAdapter.clear()
        seasonAdapter.addAll(s.seasons.map { it.name })
        binding.seasonSpinner.setSelection(seasonIndex(s), false)
        binding.sportSource.text = "Source: ${s.source}"
        render()
    }

    private fun render() {
        val s = sport ?: return
        val season = s.seasons[seasonIndex(s)]
        val week = weekIndex(s, season)

        // Week dropdown: "Week 2: 5 x 5". Only for sports whose main lifts rotate weekly.
        binding.weekSpinner.isVisible = season.hasWeeks
        if (season.hasWeeks) {
            weekAdapter.clear()
            weekAdapter.addAll(season.cycle.mapIndexed { i, scheme -> "Week ${i + 1}: ${scheme.label}" })
            binding.weekSpinner.setSelection(week, false)
        }
        binding.seasonAbout.text = season.about
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
