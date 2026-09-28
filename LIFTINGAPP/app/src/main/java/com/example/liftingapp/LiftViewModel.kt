package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.LeaderboardRow
import com.example.liftingapp.data.LiftDatabase
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.LiftRepository
import com.example.liftingapp.data.LiftWithAthlete
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.data.buildLeaderboard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** What the leaderboard list needs in one piece, so the adapter never shows rows with the wrong unit. */
data class LeaderboardState(val exercise: String, val rankBy: RankBy, val rows: List<LeaderboardRow>)

/**
 * Holds screen state and survives rotation. Same shape as project4's ViewModel:
 * the Activity observes LiveData and calls setExercise(...) / addEntry(...).
 * Internally Room gives Flows, and asLiveData() bridges them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LiftRepository(LiftDatabase.getDatabase(application).liftDao())

    val athletes: LiveData<List<Athlete>> = repository.athletes.asLiveData()
    val history: LiveData<List<LiftWithAthlete>> = repository.history.asLiveData()

    // --- Log tab (project4's spinner + chart, now per athlete) ---
    private val selectedExercise = MutableStateFlow(EXERCISES.first())
    private val selectedAthlete = MutableStateFlow<Long?>(null)

    val exercise: String get() = selectedExercise.value
    val athleteId: Long? get() = selectedAthlete.value

    fun setExercise(exercise: String) { selectedExercise.value = exercise }
    fun selectAthlete(id: Long?) { selectedAthlete.value = id }

    /** The chart data: the selected athlete's sets for the selected exercise, oldest first. */
    val entries: LiveData<List<LiftEntry>> =
        combine(selectedAthlete, selectedExercise) { id, exercise -> id to exercise }
            .flatMapLatest { (id, exercise) ->
                if (id == null) flowOf(emptyList()) else repository.progressFor(id, exercise)
            }
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
    fun addAthlete(name: String, positionGroup: String, bodyWeightLb: Float, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.addAthlete(name.trim(), positionGroup.trim().uppercase(), bodyWeightLb)
            onAdded(id)
        }
    }

    /** project4's addEntry(exercise, weight), plus who did it and how many reps. */
    fun addEntry(athleteId: Long, exercise: String, weight: Float, reps: Int) {
        viewModelScope.launch {
            repository.insert(LiftEntry(athleteId = athleteId, exercise = exercise, weight = weight, reps = reps))
        }
    }
}
