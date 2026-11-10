package com.example.liftingapp.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.SportDayActivity
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.BASKETBALL
import com.example.liftingapp.data.FOOTBALL
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.Season
import com.example.liftingapp.data.SportDay
import com.example.liftingapp.data.SportSection
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.data.sportSectionFor
import com.example.liftingapp.databinding.ItemWorkoutBinding
import com.example.liftingapp.databinding.TabWorkoutsBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Which workouts the Workouts tab shows depends on where you are (picked on the team select screen):
 *  - General use: the general workout library (WorkoutsTab).
 *  - A team: a dropdown with the team's sport plan (if its sport has one, e.g. Football), Team
 *    workouts (made by anyone on the team and shared with everyone, plus workouts sent in the app;
 *    see TeamWorkoutsSection), and General workouts (the library).
 *  - A sport's plan has a season dropdown (Off-season / In-season), a week dropdown
 *    for sports that rotate weekly (football), and one card per training day. Cards use the same
 *    layout as the library and can be sent to an athlete with the same Send dialog or logged to the
 *    Log tab's workout log; they're read-only (no edit/delete). Tapping a card opens the whole day
 *    full screen (SportDayActivity), whose Log/Send buttons come back here.
 *
 * Kept separate from WorkoutsTab so the library code doesn't change. Wired up in MainActivity.
 * To add a sport: add its SportSection in data/ and add it to [sports].
 */
class SportSections(
    private val activity: AppCompatActivity,
    private val binding: TabWorkoutsBinding,
    private val viewModel: LiftViewModel,
    private val logger: WorkoutLogger,
    private val workoutsTab: WorkoutsTab
) {
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var athletes: List<Athlete> = emptyList()
    private val adapter = SportDayAdapter(onOpen = ::openDay, onSend = ::send, onLog = ::log)

    /**
     * The full-screen day view (SportDayActivity). Its Log and Send buttons come back here, so they
     * run exactly like this card's buttons. Registered here (during MainActivity.onCreate) so the
     * result still arrives after a rotation, same as WorkoutsTab's editor.
     */
    private val dayScreen = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val section = sports.firstOrNull { it.sport == data.getStringExtra(SportDayActivity.EXTRA_SPORT) }
            ?: return@registerForActivityResult
        val season = section.seasons.getOrNull(data.getIntExtra(SportDayActivity.EXTRA_SEASON, 0))
            ?: return@registerForActivityResult
        val day = season.days.getOrNull(data.getIntExtra(SportDayActivity.EXTRA_DAY, 0))
            ?: return@registerForActivityResult
        val week = data.getIntExtra(SportDayActivity.EXTRA_WEEK, 0)
        val item = season.workoutFor(day, week)
        when (data.getStringExtra(SportDayActivity.EXTRA_ACTION)) {
            SportDayActivity.ACTION_SEND -> send(item)
            SportDayActivity.ACTION_LOG -> logger.log(item, sourceOf(section, season, week))
        }
    }

    /** Every sport plan, for matching the full-screen day view's result. */
    private val sports: List<SportSection> = listOf(FOOTBALL, BASKETBALL)
    private val seasonAdapter = spinnerAdapter(activity, emptyList())
    private val weekAdapter = spinnerAdapter(activity, emptyList())

    /** This team's sport plan, or null (General use, or a sport with no plan yet). */
    private val sport: SportSection? = sportSectionFor(viewModel.scope)

    /** What the Workouts tab is showing. */
    private enum class Mode { PLAN, TEAM, GENERAL }

    /** The dropdown's choices: on a team, its plan (if any), Team workouts, General workouts. */
    private val modes: List<Mode> = when {
        viewModel.scope !is Scope.Team -> listOf(Mode.GENERAL)
        sport != null -> listOf(Mode.PLAN, Mode.TEAM, Mode.GENERAL)
        else -> listOf(Mode.TEAM, Mode.GENERAL)
    }
    private val modeKey = "mode_" + ((viewModel.scope as? Scope.Team)?.id ?: "general")
    private var mode: Mode
        get() = prefs.getString(modeKey, null)?.let { saved -> modes.firstOrNull { it.name == saved } } ?: modes.first()
        set(value) = prefs.edit { putString(modeKey, value.name) }

    private val team = TeamWorkoutsSection(activity, binding, viewModel, logger, ::send)

    // Season and week are remembered per sport.
    private fun seasonIndex(s: SportSection) = prefs.getInt("${s.sport}_season", 0).coerceIn(0, s.seasons.lastIndex)
    private fun weekIndex(s: SportSection, season: Season) = weekIndex(activity, s, season)

    init {
        binding.sportWorkoutList.adapter = adapter

        // Teams pick between their plan, team workouts and the general library; General use has just the library.
        binding.sportSpinner.isVisible = modes.size > 1
        binding.sportSpinner.adapter = spinnerAdapter(activity, modes.map { m ->
            when (m) {
                Mode.PLAN -> "${sport?.sport} plan"
                Mode.TEAM -> "Team workouts"
                Mode.GENERAL -> "General workouts"
            }
        })
        binding.sportSpinner.setSelection(modes.indexOf(mode), false)
        binding.sportSpinner.onItemSelected { position ->
            val picked = modes.getOrNull(position) ?: return@onItemSelected
            if (picked != mode) { mode = picked; showSection() }
        }
        // New workout: a team workout in Team workouts, otherwise one in the general library.
        binding.newWorkoutFab.setOnClickListener { if (mode == Mode.TEAM) team.newWorkout() else workoutsTab.newWorkout() }

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

    /** Swaps between the plan, team workouts and the library. Only visibility changes. */
    private fun showSection() {
        val current = mode
        val library = current == Mode.GENERAL
        binding.sportSection.isVisible = current == Mode.PLAN
        team.setVisible(current == Mode.TEAM)
        binding.filterToggle.isVisible = library
        binding.workoutList.isVisible = library
        binding.newWorkoutFab.isVisible = current != Mode.PLAN
        binding.newWorkoutFab.text = if (current == Mode.TEAM) "New team workout" else "New workout"
        binding.workoutsEmptyText.isVisible = library && (binding.workoutList.adapter?.itemCount ?: 0) == 0
        val s = sport
        if (current != Mode.PLAN || s == null) return

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
            weekAdapter.addAll((0 until season.totalWeeks).map(season::weekLabel))
            binding.weekSpinner.setSelection(week, false)
        }
        binding.seasonAbout.text = season.about
        adapter.submit(season, week)
    }

    fun send(item: WorkoutWithExercises) {
        showSendWorkoutDialog(
            context = activity,
            item = item,
            athletes = athletes,
            canSaveContact = viewModel::canEdit,
            onAthleteChanged = viewModel::updateAthlete,
            onProblem = { message -> Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show() },
            onSendInApp = if (viewModel.scope is Scope.Team) { athlete ->
                viewModel.sendInApp(item, athlete) { message -> Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show() }
            } else null
        )
    }

    /** Logs a plan day; the source says which plan and week it came from. */
    private fun log(item: WorkoutWithExercises) {
        val s = sport ?: return
        val season = s.seasons[seasonIndex(s)]
        logger.log(item, sourceOf(s, season, weekIndex(s, season)))
    }

    /** "Football Off-season, week 2" or "Basketball In-season". */
    private fun sourceOf(section: SportSection, season: Season, week: Int): String = buildString {
        append(section.sport).append(' ').append(season.name)
        if (season.hasWeeks) append(", week ").append(week + 1)
    }

    /** Tapping a day card: the full-screen view of that day for the season and week picked above. */
    private fun openDay(dayIndex: Int) {
        val s = sport ?: return
        val seasonIdx = seasonIndex(s)
        dayScreen.launch(
            SportDayActivity.intent(activity, s.sport, seasonIdx, weekIndex(s, s.seasons[seasonIdx]), dayIndex)
        )
    }

    companion object {
        private const val PREFS = "sport_sections"

        /** The week picked for [section]'s plan on the Workouts tab (0-based; always 0 without weekly rotation). */
        fun weekIndex(context: Context, section: SportSection, season: Season): Int =
            if (!season.hasWeeks) 0
            else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("${section.sport}_week", 0).coerceIn(0, season.totalWeeks - 1)
    }
}

/** Exercise lines a sport card shows before "+N more" (same as the library cards). */
private const val PREVIEW_LINES = 5

/** One card per training day, drawn with the library's item_workout layout. */
private class SportDayAdapter(
    private val onOpen: (dayIndex: Int) -> Unit,
    private val onSend: (WorkoutWithExercises) -> Unit,
    private val onLog: (WorkoutWithExercises) -> Unit
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
        b.logButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onLog(items[position])
        }
        // Tap anywhere else on the card for the full-screen view, like the library cards open the editor.
        b.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onOpen(position)
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
        // A preview like the library cards; tapping the card opens every line full screen.
        val shown = if (item.exercises.size > PREVIEW_LINES) item.exercises.take(PREVIEW_LINES - 1) else item.exercises
        val more = item.exercises.size - shown.size
        b.exercises.text = buildString {
            append(shown.joinToString("\n") { WorkoutShare.exerciseLine(it, times = " × ") })
            if (more > 0) append("\n+").append(more).append(" more · tap to see all")
        }
        b.exercises.isVisible = item.exercises.isNotEmpty()
        b.sendButton.contentDescription = "Send ${item.workout.name}"
        b.logButton.contentDescription = "Log ${item.workout.name}"
        b.root.contentDescription = "Open ${item.workout.name}"
    }
}
