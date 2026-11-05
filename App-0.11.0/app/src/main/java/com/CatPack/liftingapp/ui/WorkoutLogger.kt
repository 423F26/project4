package com.example.liftingapp.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.WorkoutSessionActivity
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.DAY_MS
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.data.sportSectionFor
import com.google.android.material.datepicker.MaterialDatePicker

/**
 * "Log workout": pick one of the Workouts tab's workouts (the library or a sport plan day), pick the
 * day it was done, and open its checklist (WorkoutSessionActivity). Started from the Log tab's
 * Log workout button, or the Log button on any workout card.
 *
 * It's logged for the athlete picked on the Log tab when this person may log for them (coaches,
 * or the athlete themselves); otherwise for this person's own athlete profile.
 */
class WorkoutLogger(
    private val activity: AppCompatActivity,
    private val viewModel: LiftViewModel,
    private val showMessage: (String) -> Unit
) {

    /** From the Log tab: choose the workout first. */
    fun pickAndLog() {
        val athlete = targetAthlete() ?: return
        val sections = workoutSections(activity, viewModel.workoutLibraryAll, viewModel.scope)
        showPickerDialog(
            context = activity,
            title = "Log a workout for ${athlete.name.firstName()}",
            searchHint = "Search workouts",
            sections = sections,
            emptyMessage = "No workout matches that. Make one on the Workouts tab.",
            onPick = { (item, source) -> pickDay(athlete, item, source) }
        )
    }

    /** From a workout card: this workout. */
    fun log(item: WorkoutWithExercises, source: String) {
        val athlete = targetAthlete() ?: return
        pickDay(athlete, item, source)
    }

    private fun targetAthlete(): Athlete? {
        val selected = viewModel.selected
        if (viewModel.canEdit(selected)) return selected
        viewModel.ownAthlete?.let { return it }
        showMessage(
            if (viewModel.isCoach && viewModel.athleteList.isEmpty()) "No athletes yet. They show up once they join with your team code."
            else if (viewModel.isCoach) "Pick an athlete on the Log tab first, then log their workout."
            else "Your athlete profile is still being set up. Check your connection and try again."
        )
        return null
    }

    private fun pickDay(athlete: Athlete, item: WorkoutWithExercises, source: String) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("When did ${athlete.name.firstName()} do it?")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            // The picker hands back midnight UTC of the chosen date, so this is that calendar day's number.
            val day = Math.floorDiv(selection, DAY_MS)
            val sessionId = viewModel.logWorkout(item, athlete.id, day, source)
            activity.startActivity(WorkoutSessionActivity.intent(activity, athlete.id, sessionId))
        }
        picker.show(activity.supportFragmentManager, "workout_day")
    }

    companion object {
        /**
         * The workouts the Workouts tab shows for [scope], grouped the same way: General use gets
         * the library (yours, then premade); a team gets only its sport's seasons, at the week
         * currently picked there.
         */
        fun workoutSections(
            context: Context,
            library: List<WorkoutWithExercises>,
            scope: Scope
        ): List<PickerSection<Pair<WorkoutWithExercises, String>>> {
            val sections = mutableListOf<PickerSection<Pair<WorkoutWithExercises, String>>>()
            val teamSport = sportSectionFor(scope)
            if (teamSport == null) {
                val mine = library.filter { !it.workout.isPremade }
                val premade = library.filter { it.workout.isPremade }
                if (mine.isNotEmpty()) sections += PickerSection("Your workouts", mine.map { it.toPickerItem("Your workout") })
                if (premade.isNotEmpty()) sections += PickerSection("Premade", premade.map { it.toPickerItem("Premade") })
                return sections
            }
            teamSport.seasons.forEach { season ->
                val week = SportSections.weekIndex(context, teamSport, season)
                val label = buildString {
                    append(teamSport.sport).append(' ').append(season.name)
                    if (season.hasWeeks) append(", week ").append(week + 1)
                }
                sections += PickerSection(label, season.days.map { day ->
                    val item = season.workoutFor(day, week)
                    PickerItem(day.name, exerciseSummary(item), item to label)
                })
            }
            return sections
        }

        private fun WorkoutWithExercises.toPickerItem(source: String) = PickerItem(workout.name, exerciseSummary(this), this to source)

        private fun exerciseSummary(item: WorkoutWithExercises): String {
            val names = item.exercises.map { it.name }
            return if (names.size <= 3) names.joinToString(", ") else names.take(3).joinToString(", ") + " +${names.size - 3} more"
        }
    }
}

/** "Jordan Smith" -> "Jordan". */
fun String.firstName(): String = trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { this }
