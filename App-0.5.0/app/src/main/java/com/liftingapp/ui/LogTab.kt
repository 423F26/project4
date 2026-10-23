package com.example.liftingapp.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.ChartRange
import com.example.liftingapp.data.DayBest
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.Strength
import com.example.liftingapp.data.localDay
import com.example.liftingapp.databinding.TabLogBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.abs

/**
 * Extended with an athlete picker and reps. The chart plots each training day's best
 * estimated max (plus the heaviest set, dashed) on a real time axis, with a range
 * selector underneath (1M / 3M / 6M / 1Y / All). See ProgressChart.kt.
 *
 * Tapping a point on the chart selects that training day and shows an "Edit or delete" button,
 * which runs delLog() so a typo'd set can be corrected or removed (with Undo).
 */
class LogTab(
    private val activity: AppCompatActivity,
    private val binding: TabLogBinding,
    private val viewModel: LiftViewModel
) {
    private var athletes: List<Athlete> = emptyList()
    private var sets: List<LiftEntry> = emptyList()
    private val athleteAdapter = spinnerAdapter(activity, emptyList())

    /** The chart point (one training day) the user last tapped, or null when nothing is selected. */
    private var selectedDay: DayBest? = null

    private val rangeButtons = mapOf(
        R.id.range1m to ChartRange.MONTH,
        R.id.range3m to ChartRange.THREE_MONTHS,
        R.id.range6m to ChartRange.SIX_MONTHS,
        R.id.range1y to ChartRange.YEAR,
        R.id.rangeAll to ChartRange.ALL
    )

    init {
        setupExerciseSpinner()
        setupAthleteSpinner()
        setupRangeToggle()
        binding.liftChart.setUpProgressChart()
        setupChartSelection()
        binding.addWeightFab.setOnClickListener { onAddSetClicked() }
        binding.editSetButton.setOnClickListener { delLog() }
        binding.addAthleteButton.setOnClickListener { showAddAthleteDialog(activity, viewModel, ::selectAthlete) }
        observeData()
    }

    private fun setupExerciseSpinner() {
        binding.exerciseSpinner.adapter = spinnerAdapter(activity, EXERCISES)
        binding.exerciseSpinner.setSelection(EXERCISES.indexOf(viewModel.exercise).coerceAtLeast(0), false)
        binding.exerciseSpinner.onItemSelected { position -> viewModel.setExercise(EXERCISES[position]) }
    }

    private fun setupAthleteSpinner() {
        binding.athleteSpinner.adapter = athleteAdapter
        binding.athleteSpinner.onItemSelected { position ->
            athletes.getOrNull(position)?.let { viewModel.selectAthlete(it.id) }
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
        if (day == null) {
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
    private fun selectAthlete(id: Long) {
        viewModel.selectAthlete(id)
        val index = athletes.indexOfFirst { it.id == id }
        if (index >= 0) binding.athleteSpinner.setSelection(index, false)
    }

    private fun observeData() {
        viewModel.athletes.observe(activity) { list ->
            athletes = list
            athleteAdapter.clear()
            athleteAdapter.addAll(list.map { it.label() })

            val hasAthletes = list.isNotEmpty()
            binding.athleteSpinner.visibility = if (hasAthletes) View.VISIBLE else View.INVISIBLE
            binding.noAthletesText.visibility = if (hasAthletes) View.GONE else View.VISIBLE
            if (!hasAthletes) {
                viewModel.selectAthlete(null)
                return@observe
            }
            // Keep the current athlete selected if they still exist, otherwise pick the first.
            val index = list.indexOfFirst { it.id == viewModel.athleteId }.takeIf { it >= 0 } ?: 0
            binding.athleteSpinner.setSelection(index, false)
            viewModel.selectAthlete(list[index].id)
        }

        viewModel.entries.observe(activity) { entries ->
            sets = entries
            drawChart()
        }
    }

    private fun drawChart() {
        // Every redraw clears the chart's highlight, so drop the selection (and hide the edit button) too.
        setSelectedDay(null)
        val chart = binding.liftChart
        binding.chartCaption.text = when (sets.size) {
            0 -> ""
            1 -> "1 set"
            else -> "${sets.size} sets"
        }

        val summary = if (sets.isEmpty()) null else chart.showProgress(sets, viewModel.chartRange)
        if (summary == null) {
            chart.setNoDataText(
                when {
                    viewModel.athleteId == null -> "Add an athlete to start logging."
                    sets.isEmpty() -> "No ${viewModel.exercise} sets yet. Tap Log set to add one."
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

    private fun onAddSetClicked() {
        val athlete = athletes.find { it.id == viewModel.athleteId }
        if (athlete == null) {
            // No one to log for yet: go straight to adding an athlete.
            showAddAthleteDialog(activity, viewModel, ::selectAthlete)
            return
        }
        val exercise = viewModel.exercise
        showLogSetDialog(activity, athlete, exercise) { weight, reps ->
            viewModel.addEntry(athlete.id, exercise, weight, reps)
            binding.lastSavedText.text = "Saved ${athlete.name}: $exercise ${weight.lb()} × $reps"
        }
    }

    /**
     * Edit or delete a set behind the chart point the user tapped. Each point is one training day
     * (that day's best estimated max), so when the day has more than one set the user picks which
     * one first. Both actions can be undone from the snackbar.
     */
    private fun delLog() {
        val day = selectedDay ?: return
        val athlete = athletes.find { it.id == viewModel.athleteId } ?: return
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
            viewModel.updateEntry(original.copy(weight = weight, reps = reps))
            binding.lastSavedText.text = "Updated ${athlete.name}: ${original.exercise} ${weight.lb()} × $reps"
            showUndo("Changed to ${weight.lb()} × $reps") {
                viewModel.updateEntry(original)
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

    private fun showUndo(message: String, undo: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.addWeightFab)   // keeps the snackbar above the Log set button
            .setAction("Undo") { undo() }
            .show()
    }
}
