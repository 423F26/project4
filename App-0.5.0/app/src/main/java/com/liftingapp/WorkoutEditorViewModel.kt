package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.LiftDatabase
import com.example.liftingapp.data.Workout
import com.example.liftingapp.data.WorkoutExercise
import com.example.liftingapp.data.WorkoutRepository
import kotlinx.coroutines.launch

/** What the editor reports back when it saves, so the Workouts tab can say "Saved" and offer Send. */
data class SavedWorkout(val id: Long, val name: String, val wasNew: Boolean)

/**
 * The workout being edited in WorkoutEditorActivity. Holding the draft here (not in the views)
 * means rotating or switching apps mid-edit doesn't lose the exercise list.
 */
class WorkoutEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(LiftDatabase.getDatabase(application).workoutDao())

    /** The workout as loaded, or null for a new one. */
    private var original: Workout? = null
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

    /** Loads [workoutId] (null = new workout). Only the first call does anything, so rotation doesn't reload. */
    fun start(workoutId: Long?) {
        if (started) return
        started = true
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
