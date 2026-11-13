package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.AccountWorkouts
import com.example.liftingapp.data.LiftDatabase
import com.example.liftingapp.data.Workout
import com.example.liftingapp.data.WorkoutExercise
import com.example.liftingapp.data.WorkoutRepository
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.TeamWorkouts
import com.example.liftingapp.data.inBackground
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** What the editor reports back when it saves, so the Workouts tab can say "Saved" and offer Send. */
data class SavedWorkout(
    val id: Long, val name: String, val wasNew: Boolean,
    val teamWorkoutId: String? = null,
    val accountWorkoutId: String? = null
)

/**
 * The workout being edited in WorkoutEditorActivity. Holding the draft here (not in the views)
 * means rotating or switching apps mid-edit doesn't lose the exercise list.
 */
class WorkoutEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(LiftDatabase.getDatabase(application).workoutDao())

    /** The workout as loaded, or null for a new one. */
    private var original: Workout? = null

    /** Set for a team workout (shared in Firestore with everyone on the team) instead of one on this phone. */
    private var teamId: String? = null
    private var teamWorkoutId: String? = null
    val isTeamWorkout: Boolean get() = teamId != null
    private var originalDraft: Draft? = null
    private var started = false
    private var saving = false

    var name: String = ""
    var notes: String = ""
    private val _exercises = mutableListOf<WorkoutExercise>()
    val exercises: List<WorkoutExercise> get() = _exercises

    val isNew: Boolean get() = original == null
    val isPremade: Boolean get() = original?.isPremade == true

    /** True if the workout to edit was deleted before it could load (the screen just closes). */
    var notFound = false
        private set

    private val _ready = MutableLiveData(false)
    /** Becomes true once the draft is filled in (immediately for a new workout). */
    val ready: LiveData<Boolean> = _ready

    private val _saved = MutableLiveData<SavedWorkout?>(null)
    /** Set once the save has finished; the activity then closes. */
    val saved: LiveData<SavedWorkout?> = _saved

    /** Set for one of the account's own workouts (users/{uid}/workouts), which go with the account everywhere. */
    private var accountMode = false
    private var accountWorkoutId: String? = null

    /** Loads [workoutId] (null = new workout). Only the first call does anything, so rotation doesn't reload. */
    fun start(
        workoutId: Long?, teamMode: Boolean = false, teamWorkoutId: String? = null,
        accountMode: Boolean = false, accountWorkoutId: String? = null
    ) {
        if (started) return
        started = true
        if (accountMode) {
            this.accountMode = true
            this.accountWorkoutId = accountWorkoutId
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (accountWorkoutId == null) {
                originalDraft = draft()
                _ready.value = true
                return
            }
            viewModelScope.launch {
                val item = runCatching { AccountWorkouts.get(uid, accountWorkoutId) }.getOrNull()
                if (item == null) {
                    notFound = true
                } else {
                    original = item.asItem().workout
                    name = item.name
                    notes = item.notes
                    _exercises += item.exercises
                }
                originalDraft = draft()
                _ready.value = true
            }
            return
        }
        if (teamMode) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            teamId = (ScopeStore.load(getApplication(), uid) as? Scope.Team)?.id
            this.teamWorkoutId = teamWorkoutId
            val team = teamId
            if (team != null && teamWorkoutId != null) {
                viewModelScope.launch {
                    val item = runCatching { TeamWorkouts.get(team, teamWorkoutId) }.getOrNull()
                    if (item == null) {
                        notFound = true
                    } else {
                        original = item.asItem().workout
                        name = item.name
                        notes = item.notes
                        _exercises += item.exercises
                    }
                    originalDraft = draft()
                    _ready.value = true
                }
                return
            }
        }
        if (workoutId == null) {
            originalDraft = draft()
            _ready.value = true
            return
        }
        viewModelScope.launch {
            val item = repository.workout(workoutId)
            if (item == null) {
                notFound = true
            } else {
                original = item.workout
                name = item.workout.name
                notes = item.workout.notes
                _exercises += item.exercises
            }
            originalDraft = draft()
            _ready.value = true
        }
    }

    fun addExercise(exercise: WorkoutExercise) { _exercises += exercise }

    fun replaceExercise(index: Int, exercise: WorkoutExercise) { _exercises[index] = exercise }

    fun removeExercise(index: Int): WorkoutExercise = _exercises.removeAt(index)

    /** Puts a removed exercise back where it was (Undo). */
    fun insertExercise(index: Int, exercise: WorkoutExercise) {
        _exercises.add(index.coerceIn(0, _exercises.size), exercise)
    }

    /** Swaps exercise [from] with its neighbour at [to] (Move up / Move down). */
    fun moveExercise(from: Int, to: Int) {
        if (from !in _exercises.indices || to !in _exercises.indices) return
        val moved = _exercises.removeAt(from)
        _exercises.add(to, moved)
    }

    /** True if anything differs from what was loaded (used to ask before throwing changes away). */
    fun hasChanges(): Boolean = originalDraft != null && draft() != originalDraft

    /** Call only when [name] isn't blank and there's at least one exercise; the activity checks first. */
    fun save() {
        if (saving || _saved.value != null) return   // ignore a double tap on Save
        saving = true
        val now = System.currentTimeMillis()
        val workout = original?.copy(name = name.trim(), notes = notes.trim(), updatedAt = now)
            ?: Workout(name = name.trim(), notes = notes.trim(), createdAt = now, updatedAt = now)
        val exercisesToSave = _exercises.toList()
        if (accountMode) {
            val user = FirebaseAuth.getInstance().currentUser
            val wasNew = accountWorkoutId == null
            viewModelScope.launch {
                try {
                    val id = AccountWorkouts.save(user?.uid.orEmpty(), accountWorkoutId, workout.name, workout.notes,
                                                  exercisesToSave, user?.displayName.orEmpty())
                    _saved.value = SavedWorkout(0, workout.name, wasNew, accountWorkoutId = id)
                } catch (e: Exception) {
                    saving = false
                    inBackground("save the workout") { throw e }   // shows the error message
                }
            }
            return
        }
        val team = teamId
        if (team != null) {
            val user = FirebaseAuth.getInstance().currentUser
            val wasNew = teamWorkoutId == null
            viewModelScope.launch {
                try {
                    val id = TeamWorkouts.save(team, teamWorkoutId, workout.name, workout.notes, exercisesToSave,
                                               user?.uid.orEmpty(), user?.displayName.orEmpty())
                    _saved.value = SavedWorkout(0, workout.name, wasNew, teamWorkoutId = id)
                } catch (e: Exception) {
                    saving = false
                    inBackground("save the team workout") { throw e }   // shows the error message
                }
            }
            return
        }
        viewModelScope.launch {
            val id = repository.save(workout, exercisesToSave)
            _saved.value = SavedWorkout(id, workout.name, wasNew = original == null)
        }
    }

    /** The parts a coach can change, with ids and positions left out so only real edits count. */
    private data class Draft(val name: String, val notes: String, val exercises: List<WorkoutExercise>)

    private fun draft() = Draft(
        name = name.trim(),
        notes = notes.trim(),
        exercises = _exercises.map { it.copy(id = 0, workoutId = 0, position = 0) }
    )
}
