package com.example.liftingapp.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.WorkoutLogState
import com.example.liftingapp.WorkoutSessionActivity
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.ChartRange
import com.example.liftingapp.data.DAY_MS
import com.example.liftingapp.data.DayBest
import com.example.liftingapp.data.LegacySummary
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.LogFilter
import com.example.liftingapp.data.LogItem
import com.example.liftingapp.data.LogSort
import com.example.liftingapp.data.Strength
import com.example.liftingapp.data.details
import com.example.liftingapp.data.localDay
import com.example.liftingapp.databinding.ItemLogMonthBinding
import com.example.liftingapp.databinding.ItemSessionBinding
import com.example.liftingapp.databinding.TabLogBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Log tab: pick an athlete and an exercise, see their progress chart, log sets, and under the
 * chart, their workout log (workouts logged from the Workouts tab, each with a date and a
 * checklist).
 *
 *  - The exercise picker has every exercise the Workouts tab knows, and the person's own; typing a
 *    name that isn't there adds it (ExerciseCatalog.kt).
 *  - The chart plots each training day's best estimated max on a real time axis, with a range
 *    selector underneath (1M / 3M / 6M / 1Y / All). See ProgressChart.kt.
 *  - Tapping a point on the chart shows an "Edit or delete" button, which runs delLog() so a
 *    typo'd set can be corrected or removed (with Undo).
 *  - On a team, everyone sees the whole roster, but only coaches (and each athlete for themselves)
 *    can log. Athlete accounts can add one athlete: themselves.
 */
class LogTab(
    private val activity: AppCompatActivity,
    private val binding: TabLogBinding,
    private val viewModel: LiftViewModel,
    private val logger: WorkoutLogger
) {
    private var athletes: List<Athlete> = emptyList()
    private var sets: List<LiftEntry> = emptyList()
    private val athleteAdapter = spinnerAdapter(activity, emptyList())

    /** The chart point (one training day) the user last tapped, or null when nothing is selected. */
    private var selectedDay: DayBest? = null

    /** How many workout log rows to show; "Show more" raises it. */
    private var logLimit = LOG_PAGE

    private var lastLog: WorkoutLogState? = null

    private val rangeButtons = mapOf(
        R.id.range1m to ChartRange.MONTH,
        R.id.range3m to ChartRange.THREE_MONTHS,
        R.id.range6m to ChartRange.SIX_MONTHS,
        R.id.range1y to ChartRange.YEAR,
        R.id.rangeAll to ChartRange.ALL
    )

    private val filterButtons = mapOf(
        R.id.logFilterAll to LogFilter.ALL,
        R.id.logFilterOpen to LogFilter.IN_PROGRESS,
        R.id.logFilterDone to LogFilter.DONE
    )

    init {
        setupExerciseField()
        setupAthleteSpinner()
        setupRangeToggle()
        setupWorkoutLog()
        binding.liftChart.setUpProgressChart()
        setupChartSelection()
        binding.addWeightFab.setOnClickListener { onAddSetClicked() }
        binding.editSetButton.setOnClickListener { delLog() }
        binding.athleteMenuButton.setOnClickListener { showAthleteMenu(it) }
        observeData()
    }

    // --- Pickers ---

    private fun setupExerciseField() {
        binding.exerciseField.text = viewModel.exercise
        binding.exerciseField.setOnClickListener {
            showExercisePicker(
                context = activity,
                catalog = viewModel.currentCatalog,
                custom = viewModel.customExercises,
                current = viewModel.exercise,
                onAdd = { name ->
                    viewModel.addCustomExercise(name)?.also { showMessage("Added $it to your exercises") }
                },
                onPick = ::selectExercise
            )
        }
    }

    private fun selectExercise(exercise: String) {
        viewModel.setExercise(exercise)
        binding.exerciseField.text = exercise
    }

    private fun setupAthleteSpinner() {
        binding.athleteSpinner.adapter = athleteAdapter
        binding.athleteSpinner.onItemSelected { position ->
            athletes.getOrNull(position)?.let {
                viewModel.selectAthlete(it.id)
                refreshAthleteControls()
            }
        }
    }

    private fun setupRangeToggle() {
        val selected = rangeButtons.entries.firstOrNull { it.value == viewModel.chartRange }?.key ?: R.id.rangeAll
        binding.rangeToggle.check(selected)
        binding.rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val range = rangeButtons[checkedId]
            if (isChecked && range != null) {
                viewModel.chartRange = range
                drawChart()
            }
        }
    }

    /** Tapping a point on the est. max line selects that day; tapping it again (or empty space) clears it. */
    private fun setupChartSelection() {
        binding.liftChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) = setSelectedDay(e?.data as? DayBest)
            override fun onNothingSelected() = setSelectedDay(null)
        })
    }

    private fun setSelectedDay(day: DayBest?) {
        selectedDay = day
        if (day == null || !viewModel.canEdit(viewModel.selected)) {
            binding.editSetButton.visibility = View.GONE
            return
        }
        val date = day.bestSet.date.monthDay()
        binding.editSetButton.text =
            if (setsOn(day).size > 1) "Edit or delete sets from $date" else "Edit or delete set from $date"
        binding.editSetButton.visibility = View.VISIBLE
    }

    /** Every logged set on the same calendar day as [day] (the chart shows one point per day), oldest first. */
    private fun setsOn(day: DayBest): List<LiftEntry> =
        sets.filter { localDay(it.date) == day.day }.sortedBy { it.date }

    /** Selects an athlete in both the ViewModel and the spinner (e.g. right after adding one). */
    private fun selectAthlete(id: String) {
        viewModel.selectAthlete(id)
        val index = athletes.indexOfFirst { it.id == id }
        if (index >= 0) binding.athleteSpinner.setSelection(index, false)
        refreshAthleteControls()
    }

    private fun observeData() {
        viewModel.athletes.observe(activity) { list ->
            athletes = list
            athleteAdapter.clear()
            athleteAdapter.addAll(list.map { athlete -> if (athlete.id == viewModel.uid) "${athlete.label()} · You" else athlete.label() })

            val hasAthletes = list.isNotEmpty()
            binding.athleteSpinner.visibility = if (hasAthletes) View.VISIBLE else View.INVISIBLE
            binding.noAthletesText.visibility = if (hasAthletes) View.GONE else View.VISIBLE
            if (!hasAthletes) {
                viewModel.selectAthlete(null)
            } else {
                // Keep the current athlete if they still exist; otherwise start on your own profile, then the first.
                val index = list.indexOfFirst { it.id == viewModel.athleteId }.takeIf { it >= 0 }
                    ?: list.indexOfFirst { it.id == viewModel.uid }.takeIf { it >= 0 }
                    ?: 0
                binding.athleteSpinner.setSelection(index, false)
                viewModel.selectAthlete(list[index].id)
            }
            refreshAthleteControls()
            drawChart()
        }

        viewModel.profile.observe(activity) { refreshAthleteControls() }

        viewModel.entries.observe(activity) { entries ->
            sets = entries
            drawChart()
        }

        viewModel.exerciseCatalog.observe(activity) { binding.exerciseField.text = viewModel.exercise }

        viewModel.workoutLog.observe(activity) { state ->
            lastLog = state
            renderWorkoutLog(state)
        }
    }

    /** Which buttons make sense for the picked athlete and this account. */
    private fun refreshAthleteControls() {
        val athlete = viewModel.selected
        val canEdit = viewModel.canEdit(athlete)
        binding.athleteMenuButton.isVisible = canEdit
        binding.logWorkoutButton.isVisible = canEdit

        binding.athleteHint.text = when {
            athlete != null && !viewModel.canSeeProgress(athlete) -> "${athlete.name.firstName()} keeps their progress private."
            athlete != null && !canEdit -> "Viewing ${athlete.name.firstName()}'s progress. Only coaches and ${athlete.name.firstName()} can log for them."
            !viewModel.isCoach && viewModel.ownAthlete == null && viewModel.athleteList.isNotEmpty() ->
                "Your athlete profile is still being set up. Check your connection if this doesn't go away."
            else -> ""
        }
        binding.athleteHint.isVisible = binding.athleteHint.text.isNotEmpty()
        setSelectedDay(selectedDay)
    }

    // --- Chart ---

    private fun drawChart() {
        // Every redraw clears the chart's highlight, so drop the selection (and hide the edit button) too.
        setSelectedDay(null)
        val chart = binding.liftChart
        binding.chartCaption.text = when (sets.size) {
            0 -> ""
            1 -> "1 set"
            else -> "${sets.size} sets"
        }

        val athlete = viewModel.selected
        val summary = if (sets.isEmpty()) null else chart.showProgress(sets, viewModel.chartRange)
        if (summary == null) {
            chart.setNoDataText(
                when {
                    viewModel.athleteId == null ->
                        if (viewModel.isCoach) "No athletes yet. They show up here once they join with your team code."
                        else "Setting up your athlete profile…"
                    athlete != null && !viewModel.canSeeProgress(athlete) -> "${athlete.name.firstName()}'s progress is private."
                    sets.isEmpty() -> "No ${viewModel.exercise} sets yet." + if (viewModel.canEdit(athlete)) " Tap Log set to add one." else ""
                    else -> "No ${viewModel.exercise} sets in the last ${viewModel.chartRange.phrase}. Tap All to see every set."
                }
            )
            chart.clear()
            binding.bestMaxText.text = "—"
            binding.changeGroup.visibility = View.INVISIBLE
            return
        }

        binding.bestMaxText.text = "${summary.best.bestE1rm.wholeLb()} lb"
        val gain = summary.gain
        if (gain == null) {
            binding.changeGroup.visibility = View.INVISIBLE
        } else {
            binding.changeGroup.visibility = View.VISIBLE
            binding.changeLabel.text = "Since ${summary.since}"
            binding.changeText.text = "${if (gain >= 0f) "+" else "−"}${abs(gain).wholeLb()} lb"
        }
    }

    // --- Athletes ---

    private fun showAthleteMenu(anchor: View) {
        val athlete = viewModel.selected ?: return
        PopupMenu(activity, anchor).apply {
            menu.add(0, MENU_EDIT, 0, "Edit ${athlete.name.firstName()}")
            menu.add(0, MENU_DELETE, 1, "Delete ${athlete.name.firstName()}")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> editAthlete(athlete)
                    MENU_DELETE -> confirmDeleteAthlete(athlete)
                }
                true
            }
        }.show()
    }

    private fun editAthlete(athlete: Athlete) {
        val own = athlete.id == viewModel.uid
        showAthleteDialog(
            context = activity,
            title = if (own) "Edit your profile" else "Edit athlete",
            initial = athlete.details(),
            intro = if (own) "Changes go to your account and your profile on every team." else null
        ) { details ->
            viewModel.updateAthlete(
                athlete.copy(
                    name = details.name,
                    positionGroup = details.positionGroup,
                    bodyWeightLb = details.bodyWeightLb,
                    phone = details.phone,
                    email = details.email
                )
            )
            binding.lastSavedText.text = "Saved ${details.name}"
        }
    }

    private fun confirmDeleteAthlete(athlete: Athlete) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Delete ${athlete.name}?")
            .setMessage("Every set and logged workout for ${athlete.name.firstName()} is deleted too, for everyone on ${viewModel.scope.title}. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAthlete(athlete) { showMessage("Deleted ${athlete.name}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Sets ---

    private fun onAddSetClicked() {
        val athlete = viewModel.selected
        when {
            athlete != null && viewModel.canEdit(athlete) -> Unit
            athletes.isEmpty() || (!viewModel.isCoach && viewModel.ownAthlete == null) -> {
                // No one to log for yet: the roster fills itself (no adding athletes by hand).
                showMessage(
                    if (viewModel.isCoach) "No athletes yet. They show up here once they join with your team code."
                    else "Your athlete profile is still being set up. Check your connection and try again."
                )
                return
            }
            viewModel.ownAthlete != null -> {
                showMessage("You can only log sets for yourself here.")
                viewModel.ownAthlete?.let { selectAthlete(it.id) }
                return
            }
            else -> {
                showMessage("Only coaches can log sets for other athletes.")
                return
            }
        }
        val target = athlete ?: return
        val exercise = viewModel.exercise
        showLogSetDialog(activity, target, exercise) { weight, reps ->
            viewModel.addEntry(target.id, exercise, weight, reps)
            binding.lastSavedText.text = "Saved ${target.name}: $exercise ${weight.lb()} × $reps"
        }
    }

    /**
     * Edit or delete a set behind the chart point the user tapped. Each point is one training day
     * (that day's best estimated max), so when the day has more than one set the user picks which
     * one first. Both actions can be undone from the snackbar.
     */
    private fun delLog() {
        val day = selectedDay ?: return
        val athlete = viewModel.selected ?: return
        val daySets = setsOn(day)
        when (daySets.size) {
            0 -> return
            1 -> showSetActions(athlete, daySets.single())
            else -> {
                val labels = daySets.map { set ->
                    val e1rm = Strength.estimatedOneRepMax(set.weight, set.reps).wholeLb()
                    "${set.weight.lb()} lb × ${set.reps}  (est. $e1rm lb)  ${set.date.shortTime()}"
                }.toTypedArray()
                MaterialAlertDialogBuilder(activity)
                    .setTitle("${viewModel.exercise} sets on ${day.bestSet.date.monthDay()}")
                    .setItems(labels) { _, which -> showSetActions(athlete, daySets[which]) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    /** Edit / Delete / Cancel for one set. */
    private fun showSetActions(athlete: Athlete, set: LiftEntry) {
        val e1rm = Strength.estimatedOneRepMax(set.weight, set.reps).wholeLb()
        MaterialAlertDialogBuilder(activity)
            .setTitle("${set.exercise}, ${set.date.monthDay()} at ${set.date.shortTime()}")
            .setMessage("${set.weight.lb()} lb × ${set.reps} (est. max $e1rm lb)")
            .setPositiveButton("Edit") { _, _ -> editSet(athlete, set) }
            .setNeutralButton("Delete") { _, _ -> deleteSet(athlete, set) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Reopens the log dialog pre-filled with [original]; saving keeps the set's id and date. */
    private fun editSet(athlete: Athlete, original: LiftEntry) {
        showLogSetDialog(activity, athlete, original.exercise, initial = original) { weight, reps ->
            if (weight == original.weight && reps == original.reps) return@showLogSetDialog
            val changed = original.copy(weight = weight, reps = reps)
            viewModel.updateEntry(changed, previous = original)
            binding.lastSavedText.text = "Updated ${athlete.name}: ${original.exercise} ${weight.lb()} × $reps"
            showUndo("Changed to ${weight.lb()} × $reps") {
                viewModel.updateEntry(original, previous = changed)
                binding.lastSavedText.text = "Restored ${athlete.name}: ${original.exercise} ${original.weight.lb()} × ${original.reps}"
            }
        }
    }

    private fun deleteSet(athlete: Athlete, set: LiftEntry) {
        viewModel.deleteEntry(set)
        binding.lastSavedText.text = "Deleted ${athlete.name}: ${set.exercise} ${set.weight.lb()} × ${set.reps}"
        showUndo("Deleted ${set.weight.lb()} × ${set.reps}") {
            viewModel.restoreEntry(set)
            binding.lastSavedText.text = "Restored ${athlete.name}: ${set.exercise} ${set.weight.lb()} × ${set.reps}"
        }
    }

    // --- Workout log ---

    private fun setupWorkoutLog() {
        binding.logWorkoutButton.setOnClickListener { logger.pickAndLog() }

        val selected = filterButtons.entries.firstOrNull { it.value == viewModel.currentLogFilter }?.key ?: R.id.logFilterAll
        binding.logFilterToggle.check(selected)
        binding.logFilterToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val filter = filterButtons[checkedId]
            if (isChecked && filter != null) {
                logLimit = LOG_PAGE
                viewModel.setLogFilter(filter)
            }
        }

        showSortLabel(viewModel.currentLogSort)
        binding.logSortButton.setOnClickListener {
            val next = if (viewModel.currentLogSort == LogSort.NEWEST) LogSort.OLDEST else LogSort.NEWEST
            logLimit = LOG_PAGE
            viewModel.setLogSort(next)
            showSortLabel(next)
        }

        binding.showMoreButton.setOnClickListener {
            logLimit += LOG_PAGE
            lastLog?.let(::renderWorkoutLog)
        }
    }

    private fun showSortLabel(sort: LogSort) {
        binding.logSortButton.text = if (sort == LogSort.NEWEST) "Newest" else "Oldest"
        binding.logSortButton.contentDescription = if (sort == LogSort.NEWEST) "Newest first. Tap for oldest first." else "Oldest first. Tap for newest first."
    }

    private fun renderWorkoutLog(state: WorkoutLogState) {
        val athlete = viewModel.selected
        val entries = state.items.filterIsInstance<LogItem.Entry>()
        val done = entries.count { it.session.isComplete }
        binding.workoutLogSummary.text = when {
            athlete == null -> "Workouts you log show up here"
            state.total == 0 -> "Nothing logged yet"
            else -> "${state.total} logged · ${entries.size} shown · $done done"
        }
        binding.workoutLogControls.isVisible = state.total > 0

        val list = binding.workoutLogList
        list.removeAllViews()
        var shown = 0
        for (item in state.items) {
            if (item is LogItem.Entry && shown >= logLimit) break
            when (item) {
                is LogItem.Month -> {
                    val row = ItemLogMonthBinding.inflate(activity.layoutInflater, list, false)
                    row.root.text = "${monthName(item.year, item.month)} · ${item.count} ${if (item.count == 1) "workout" else "workouts"}"
                    list.addView(row.root)
                }
                is LogItem.Entry -> {
                    list.addView(sessionRow(item).root)
                    shown++
                }
            }
        }
        binding.showMoreButton.isVisible = entries.size > logLimit

        binding.workoutLogEmpty.isVisible = entries.isEmpty()
        binding.workoutLogEmpty.text = when {
            athlete == null -> "Add an athlete, then log workouts from the Workouts tab."
            !viewModel.canSeeProgress(athlete) -> "${athlete.name.firstName()}'s workout log is private."
            state.total == 0 && viewModel.canEdit(athlete) -> "Tap Log workout, or the Log button on any workout in the Workouts tab, to track it here with a checklist."
            state.total == 0 -> "${athlete.name.firstName()} hasn't logged a workout yet."
            state.filter == LogFilter.DONE -> "No finished workouts yet."
            else -> "Every logged workout is finished."
        }
    }

    private fun sessionRow(item: LogItem.Entry): ItemSessionBinding {
        val session = item.session
        val row = ItemSessionBinding.inflate(activity.layoutInflater, binding.workoutLogList, false)
        val dayStart = session.day * DAY_MS
        row.weekday.text = dayFormat("EEE").format(Date(dayStart))
        row.dayOfMonth.text = dayFormat("d").format(Date(dayStart))
        row.name.text = session.workoutName
        val total = session.exercises.size
        row.meta.text = listOfNotNull(
            if (session.isComplete) "Done" else "${session.doneCount} of $total done",
            session.source.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        row.progress.setProgressCompat(if (total == 0) 0 else (session.doneCount * 100f / total).roundToInt(), false)
        row.count.text = "${session.doneCount}/$total"
        row.count.isVisible = !session.isComplete
        row.doneIcon.isVisible = session.isComplete
        row.root.contentDescription = "${session.workoutName}, ${dayFormat("EEEE, MMMM d").format(Date(dayStart))}, ${row.meta.text}"
        row.root.setOnClickListener {
            activity.startActivity(WorkoutSessionActivity.intent(activity, session.athleteId, session.id))
        }
        return row
    }

    // --- Data saved on the phone before teams ---

    /** Offered once: copy athletes and sets saved on this phone by 0.8 or earlier into this team or General. */
    fun offerLegacyImport(summary: LegacySummary) {
        val athletesText = if (summary.athletes.size == 1) "1 athlete" else "${summary.athletes.size} athletes"
        val setsText = if (summary.setCount == 1) "1 set" else "${summary.setCount} sets"
        MaterialAlertDialogBuilder(activity)
            .setTitle("Bring over your old lifts?")
            .setMessage(
                "This phone has $athletesText and $setsText saved from before teams. Copy them into ${viewModel.scope.title}? " +
                    "You can also do this later from Account & settings (tap your profile picture)."
            )
            .setPositiveButton("Copy them") { _, _ -> startLegacyImport(activity, viewModel, summary, ::showMessage) }
            .setNegativeButton("Not now", null)
            .setNeutralButton("Don't ask again") { _, _ -> viewModel.dismissLegacyData() }
            .show()
    }

    // --- Helpers ---

    private fun showUndo(message: String, undo: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.addWeightFab)   // keeps the snackbar above the Log set button
            .setAction("Undo") { undo() }
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setAnchorView(binding.addWeightFab).show()
    }

    private fun monthName(year: Int, month: Int): String {
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month, 1)
        return dayFormat("MMMM yyyy").format(calendar.time)
    }

    /** Formats day numbers from localDay(). UTC, because the day number already has the local offset baked in. */
    private fun dayFormat(pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private companion object {
        const val LOG_PAGE = 15
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
