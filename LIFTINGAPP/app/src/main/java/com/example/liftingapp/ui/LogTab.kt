package com.example.liftingapp.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.Strength
import com.example.liftingapp.databinding.TabLogBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * extended with an athlete picker and reps. The chart draws two lines:
 * the weight lifted (dashed) and the estimated 1RM, so a 225×5 and a 245×2 are comparable.
 */
class LogTab(
    private val activity: AppCompatActivity,
    private val binding: TabLogBinding,
    private val viewModel: LiftViewModel
) {
    private var athletes: List<Athlete> = emptyList()
    private val athleteAdapter = boneSpinnerAdapter(activity, emptyList())

    init {
        setupExerciseSpinner()
        setupAthleteSpinner()
        binding.liftChart.applyWashStyle()
        binding.addWeightFab.setOnClickListener { onAddSetClicked() }
        binding.addAthleteButton.setOnClickListener { showAddAthleteDialog(activity, viewModel, ::selectAthlete) }
        observeData()
    }

    private fun setupExerciseSpinner() {
        binding.exerciseSpinner.adapter = boneSpinnerAdapter(activity, EXERCISES)
        binding.exerciseSpinner.setSelection(EXERCISES.indexOf(viewModel.exercise).coerceAtLeast(0), false)
        binding.exerciseSpinner.onItemSelected { position -> viewModel.setExercise(EXERCISES[position]) }
    }

    private fun setupAthleteSpinner() {
        binding.athleteSpinner.adapter = athleteAdapter
        binding.athleteSpinner.onItemSelected { position ->
            athletes.getOrNull(position)?.let { viewModel.selectAthlete(it.id) }
        }
    }

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

        viewModel.entries.observe(activity) { entries -> drawChart(entries) }
    }

    private fun drawChart(entries: List<LiftEntry>) {
        val chart = binding.liftChart
        binding.chartCaption.text = when (entries.size) {
            0 -> ""
            1 -> "1 set"
            else -> "${entries.size} sets"
        }

        if (entries.isEmpty()) {
            chart.setNoDataText(
                if (viewModel.athleteId == null) "Add an athlete to start logging."
                else "No ${viewModel.exercise} sets yet. Tap + to log one."
            )
            chart.clear()
            return
        }

        val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(entries.map { dateFormat.format(Date(it.date)) })

        val bone = ContextCompat.getColor(activity, R.color.bone)
        val muted = ContextCompat.getColor(activity, R.color.bone_muted)

        val weightLine = LineDataSet(
            entries.mapIndexed { index, lift -> Entry(index.toFloat(), lift.weight) },
            "Weight"
        ).styled(muted, dashed = true)

        val e1rmLine = LineDataSet(
            entries.mapIndexed { index, lift -> Entry(index.toFloat(), Strength.estimatedOneRepMax(lift.weight, lift.reps)) },
            "Est. 1RM"
        ).styled(bone)

        chart.data = LineData(weightLine, e1rmLine)
        chart.invalidate()
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
}
