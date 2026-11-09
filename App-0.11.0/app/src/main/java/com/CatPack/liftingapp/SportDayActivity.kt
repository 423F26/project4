package com.example.liftingapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import com.example.liftingapp.data.SPORT_SECTIONS
import com.example.liftingapp.data.WorkoutShare
import com.example.liftingapp.databinding.ActivitySportDayBinding
import com.example.liftingapp.databinding.ItemWorkoutExerciseEditBinding
import com.example.liftingapp.ui.AppTheme

/**
 * Full-screen view of one Football/Basketball plan day, opened by tapping its card on the Workouts
 * tab (ui/SportSections.kt). Looks like WorkoutEditorActivity but read-only: the plans are fixed, so
 * the exercise cards have no ⋮ menu and there's nothing to save.
 *
 * Log and Send close this screen and hand the action back to the Workouts tab (RESULT_OK with
 * [EXTRA_ACTION]), which runs it exactly like the card's own Log and Send buttons: same athlete,
 * same permissions, same dialogs. So this screen needs no ViewModel of its own.
 *
 * The day is passed by position (sport, season, week, day) and rebuilt from data/.
 */
class SportDayActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySportDayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)   // must happen before any views are inflated
        super.onCreate(savedInstanceState)
        binding = ActivitySportDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sport = SPORT_SECTIONS.firstOrNull { it.sport == intent.getStringExtra(EXTRA_SPORT) }
        val season = sport?.seasons?.getOrNull(intent.getIntExtra(EXTRA_SEASON, 0))
        val day = season?.days?.getOrNull(intent.getIntExtra(EXTRA_DAY, 0))
        if (sport == null || season == null || day == null) {   // stale link (plan changed); nothing to show
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
        val countText = if (count == 1) "1 exercise" else "$count exercises"
        binding.dayMeta.text = listOfNotNull(
            if (season.hasWeeks) "Week ${week.coerceIn(0, season.totalWeeks - 1) + 1} of ${season.totalWeeks}" else null,
            countText
        ).joinToString(" · ")

        // The day's own notes. (workoutFor() also puts "Week N of 4." in front; the line above says that.)
        val notes = day.notes.trim()
        binding.notesLabel.isVisible = notes.isNotEmpty()
        binding.dayNotes.isVisible = notes.isNotEmpty()
        binding.dayNotes.text = notes

        binding.exerciseCount.text = countText
        val inflater = LayoutInflater.from(this)
        item.exercises.forEachIndexed { index, exercise ->
            val row = ItemWorkoutExerciseEditBinding.inflate(inflater, binding.exerciseList, true)
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

        binding.logButton.contentDescription = "Log ${item.workout.name}"
        binding.sendButton.contentDescription = "Send ${item.workout.name}"
        binding.logButton.setOnClickListener { finishWith(ACTION_LOG) }
        binding.sendButton.setOnClickListener { finishWith(ACTION_SEND) }
    }

    private fun finishWith(action: String) {
        setResult(Activity.RESULT_OK, Intent().putExtras(intent).putExtra(EXTRA_ACTION, action))
        finish()
    }

    companion object {
        const val EXTRA_SPORT = "sport"
        const val EXTRA_SEASON = "season"
        const val EXTRA_WEEK = "week"
        const val EXTRA_DAY = "day"
        const val EXTRA_ACTION = "action"
        const val ACTION_LOG = "log"
        const val ACTION_SEND = "send"

        fun intent(context: Context, sport: String, seasonIndex: Int, weekIndex: Int, dayIndex: Int): Intent =
            Intent(context, SportDayActivity::class.java)
                .putExtra(EXTRA_SPORT, sport)
                .putExtra(EXTRA_SEASON, seasonIndex)
                .putExtra(EXTRA_WEEK, weekIndex)
                .putExtra(EXTRA_DAY, dayIndex)
    }
}
