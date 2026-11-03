package com.example.liftingapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.SPORT_SECTIONS
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.databinding.ActivitySportDayBinding
import com.example.liftingapp.databinding.ItemWorkoutExerciseEditBinding
import com.example.liftingapp.ui.AppTheme
import com.example.liftingapp.ui.showSendWorkoutDialog
import com.google.android.material.snackbar.Snackbar

/**
 * Full-screen view of one Football/Basketball plan day, opened by tapping its card in the Workouts
 * tab (ui/SportSections.kt). Looks like WorkoutEditorActivity, but read-only: the plans are fixed,
 * so the top-right button is Send instead of Save, and the exercise cards have no ⋮ menu.
 *
 * The day is passed by position (sport name, season, week, day) and rebuilt from data/, so
 * nothing needs to be saved or parcelled.
 */
class SportDayActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySportDayBinding
    private var athletes: List<Athlete> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)   // must happen before any views are inflated
        super.onCreate(savedInstanceState)
        binding = ActivitySportDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sport = SPORT_SECTIONS.firstOrNull { it.sport == intent.getStringExtra(EXTRA_SPORT) }
        val season = sport?.seasons?.getOrNull(intent.getIntExtra(EXTRA_SEASON, 0))
        val dayIndex = intent.getIntExtra(EXTRA_DAY, 0)
        val day = season?.days?.getOrNull(dayIndex)
        if (sport == null || season == null || day == null) {   // stale link (e.g. plan changed); nothing to show
            finish()
            return
        }
        val week = intent.getIntExtra(EXTRA_WEEK, 0)
        val item = season.workoutFor(day, week)

        binding.screenTitle.text = "${sport.sport} · ${season.name}"
        TooltipCompat.setTooltipText(binding.closeButton, binding.closeButton.contentDescription)
        binding.closeButton.setOnClickListener { finish() }

        binding.dayName.text = day.name
        val count = item.exercises.size
        binding.dayMeta.text = listOfNotNull(
            if (season.hasWeeks) "Week ${week.coerceIn(0, season.cycle.lastIndex) + 1} of ${season.cycle.size}" else null,
            if (count == 1) "1 exercise" else "$count exercises"
        ).joinToString(" · ")

        // workoutFor() puts "Week N of 4." in front of the notes; the meta line above already says it.
        val notes = day.notes.trim()
        binding.notesLabel.isVisible = notes.isNotEmpty()
        binding.dayNotes.isVisible = notes.isNotEmpty()
        binding.dayNotes.text = notes

        binding.exerciseCount.text = if (count == 1) "1 exercise" else "$count exercises"
        item.exercises.forEachIndexed { index, exercise ->
            val row = ItemWorkoutExerciseEditBinding.inflate(LayoutInflater.from(this), binding.exerciseList, true)
            row.number.text = (index + 1).toString()
            row.title.text = exercise.name
            row.detail.text = WorkoutShare.prescription(exercise, times = " × ")
            row.cue.text = exercise.notes
            row.cue.isVisible = exercise.notes.isNotBlank()
            row.moreButton.isVisible = false   // read-only: nothing to move or remove
            row.root.isClickable = false
            row.root.isFocusable = false
        }
        binding.sourceText.text = "Source: ${sport.source}"

        // Send uses the same dialog as the Workouts tab, with this phone's athletes.
        val viewModel = ViewModelProvider(this)[LiftViewModel::class.java]
        viewModel.athletes.observe(this) { athletes = it }
        binding.sendButton.setOnClickListener {
            showSendWorkoutDialog(
                context = this,
                item = item,
                athletes = athletes,
                onAthleteChanged = viewModel::updateAthlete,
                onProblem = { message -> Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show() }
            )
        }
    }

    companion object {
        private const val EXTRA_SPORT = "sport"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_WEEK = "week"
        private const val EXTRA_DAY = "day"

        fun intent(context: Context, sport: String, seasonIndex: Int, weekIndex: Int, dayIndex: Int): Intent =
            Intent(context, SportDayActivity::class.java)
                .putExtra(EXTRA_SPORT, sport)
                .putExtra(EXTRA_SEASON, seasonIndex)
                .putExtra(EXTRA_WEEK, weekIndex)
                .putExtra(EXTRA_DAY, dayIndex)
    }
}
