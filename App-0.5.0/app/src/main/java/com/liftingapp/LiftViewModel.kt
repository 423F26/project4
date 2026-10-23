package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.ChartRange
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.LeaderboardRow
import com.example.liftingapp.data.LiftDatabase
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.LiftRepository
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.data.WorkoutFilter
import com.example.liftingapp.data.WorkoutRepository
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.data.buildLeaderboard
import com.example.liftingapp.data.workoutLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** What the leaderboard list needs in one piece, so the adapter never shows rows with the wrong unit. */
data class LeaderboardState(val exercise: String, val rankBy: RankBy, val rows: List<LeaderboardRow>)

/** The Workouts tab's list for the selected filter, plus the filter itself (for the empty message). */
data class WorkoutListState(val filter: WorkoutFilter, val workouts: List<WorkoutWithExercises>)

/**
 * Holds screen state and survives rotation. Same shape as project4's ViewModel:
 * the Activity observes LiveData and calls setExercise(...) / addEntry(...).
 * Internally Room gives Flows, and asLiveData() bridges them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiftViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LiftDatabase.getDatabase(application)
    private val repository = LiftRepository(database.liftDao())
    private val workoutRepository = WorkoutRepository(database.workoutDao())

    val athletes: LiveData<List<Athlete>> = repository.athletes.asLiveData()

    // --- Log tab (project4's spinner + chart, now per athlete) ---
    private val selectedExercise = MutableStateFlow(EXERCISES.first())
    private val selectedAthlete = MutableStateFlow<Long?>(null)

    val exercise: String get() = selectedExercise.value
    val athleteId: Long? get() = selectedAthlete.value

    fun setExercise(exercise: String) { selectedExercise.value = exercise }
    fun selectAthlete(id: Long?) { selectedAthlete.value = id }

    /** How much history the chart shows (1M / 3M / 6M / 1Y / All). Kept here so it survives rotation and theme changes. */
    var chartRange: ChartRange = ChartRange.ALL

    /** The chart data: the selected athlete's sets for the selected exercise, oldest first. */
    val entries: LiveData<List<LiftEntry>> =
        combine(selectedAthlete, selectedExercise) { id, exercise -> id to exercise }
            .flatMapLatest { (id, exercise) ->
                if (id == null) flowOf(emptyList()) else repository.progressFor(id, exercise)
            }
            .asLiveData()

    // --- Workouts tab ---
    private val workoutFilter = MutableStateFlow(WorkoutFilter.ALL)

    val currentWorkoutFilter: WorkoutFilter get() = workoutFilter.value

    fun setWorkoutFilter(filter: WorkoutFilter) { workoutFilter.value = filter }

    /** Re-sorts when the workouts change, re-filters when the All / Premade / Mine toggle changes. */
    val workouts: LiveData<WorkoutListState> =
        workoutRepository.workouts
            .combine(workoutFilter) { all, filter -> WorkoutListState(filter, workoutLibrary(all, filter)) }
            .asLiveData()

    // --- Leaderboard tab ---
    private val leaderboardExercise = MutableStateFlow(EXERCISES.first())
    private val rankByMode = MutableStateFlow(RankBy.ESTIMATED_MAX)

    val currentLeaderboardExercise: String get() = leaderboardExercise.value
    val currentRankBy: RankBy get() = rankByMode.value

    fun setLeaderboardExercise(exercise: String) { leaderboardExercise.value = exercise }
    fun setRankBy(rankBy: RankBy) { rankByMode.value = rankBy }

    /** Re-queries when the exercise changes, re-sorts when the ranking mode changes. */
    val leaderboard: LiveData<LeaderboardState> =
        leaderboardExercise
            .flatMapLatest { exercise -> repository.liftsFor(exercise).combine(rankByMode) { lifts, rankBy ->
                LeaderboardState(exercise, rankBy, buildLeaderboard(lifts, rankBy))
            } }
            .asLiveData()

    // --- Writes ---
    fun addAthlete(
        name: String,
        positionGroup: String,
        bodyWeightLb: Float,
        phone: String? = null,
        email: String? = null,
        onAdded: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.addAthlete(name.trim(), positionGroup.trim().uppercase(), bodyWeightLb, phone, email)
            onAdded(id)
        }
    }

    /** Saves an athlete's changed details (the Send dialog uses this for phone and email). */
    fun updateAthlete(athlete: Athlete) {
        viewModelScope.launch { repository.updateAthlete(athlete) }
    }

    /** project4's addEntry(exercise, weight), plus who did it and how many reps. */
    fun addEntry(athleteId: Long, exercise: String, weight: Float, reps: Int) {
        viewModelScope.launch {
            repository.insert(LiftEntry(athleteId = athleteId, exercise = exercise, weight = weight, reps = reps))
        }
    }

    /** Saves a corrected set. [entry] must keep its original id; its date is left as-is. */
    fun updateEntry(entry: LiftEntry) {
        viewModelScope.launch { repository.update(entry) }
    }

    fun deleteEntry(entry: LiftEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    /** Puts a deleted set back exactly as it was (same id and date). Used by the Undo snackbar. */
    fun restoreEntry(entry: LiftEntry) {
        viewModelScope.launch { repository.insert(entry) }
    }

    /** Looks up one workout (e.g. the one just saved) and hands it to [onLoaded]; skipped if it's gone. */
    fun loadWorkout(id: Long, onLoaded: (WorkoutWithExercises) -> Unit) {
        viewModelScope.launch { workoutRepository.workout(id)?.let(onLoaded) }
    }

    /** Deletes a workout and its exercises. Keep [item] to undo with restoreWorkout(). */
    fun deleteWorkout(item: WorkoutWithExercises) {
        viewModelScope.launch { workoutRepository.delete(item.workout) }
    }

    /** Puts a deleted workout back exactly as it was (same ids). Used by the Undo snackbar. */
    fun restoreWorkout(item: WorkoutWithExercises) {
        viewModelScope.launch { workoutRepository.restore(item) }
    }
}
