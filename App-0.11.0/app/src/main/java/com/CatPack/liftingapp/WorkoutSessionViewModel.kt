package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.CloudErrors
import com.example.liftingapp.data.ExerciseCatalog
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.LiftRepository
import com.example.liftingapp.data.ProfileRepository
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.SessionExercise
import com.example.liftingapp.data.UserProfile
import com.example.liftingapp.data.WorkoutSession
import com.example.liftingapp.data.allMarked
import com.example.liftingapp.data.inBackground
import com.example.liftingapp.data.timeOnDay
import com.example.liftingapp.data.toggled
import com.example.liftingapp.data.withLoggedSet
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * One logged workout in WorkoutSessionActivity. Every check-off saves at once (Firestore applies it
 * locally straight away and syncs it), so there's no Save button and nothing to lose on rotation.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutSessionViewModel(application: Application) : AndroidViewModel(application) {

    val uid: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope: Scope = ScopeStore.load(application, uid) ?: Scope.General
    private val repository = LiftRepository(scope, uid)

    private val ids = MutableStateFlow<Pair<String, String>?>(null)

    /** Call once from onCreate; later calls (after rotation) are ignored. */
    fun start(athleteId: String, sessionId: String) {
        if (ids.value == null) ids.value = athleteId to sessionId
    }

    private val profileRepository = ProfileRepository(uid)

    private val profileState: StateFlow<UserProfile?> = profileRepository.profile()
        .onEach { repository.profile = it }
        .catch { CloudErrors.report("load your profile", it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profile: LiveData<UserProfile> = profileState.filterNotNull().asLiveData()

    private val athleteState: StateFlow<Athlete?> = ids.flatMapLatest { pair ->
        if (pair == null) flowOf(null)
        else repository.athlete(pair.first).catch {
            CloudErrors.report("load the athlete", it)
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val athlete: LiveData<Athlete?> = athleteState.asLiveData()

    /** Null while loading. [Loaded.session] is null if the workout was deleted. */
    data class Loaded(val session: WorkoutSession?)

    private val sessionState: StateFlow<Loaded?> = ids.flatMapLatest { pair ->
        if (pair == null) flowOf(null)
        else repository.session(pair.first, pair.second)
            .map<WorkoutSession?, Loaded?> { Loaded(it) }
            .catch {
                CloudErrors.report("load the workout", it)
                emit(Loaded(null))
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val session: LiveData<Loaded?> = sessionState.asLiveData()
    private val current: WorkoutSession? get() = sessionState.value?.session

    /** Coaches, and the athlete themselves, can check things off. Everything in General is yours. */
    val canEdit: Boolean get() {
        val athleteId = ids.value?.first ?: return false
        return scope == Scope.General || profileState.value?.isCoach == true ||
            athleteId == uid || athleteState.value?.ownerUid == uid
    }

    /** The exercise list, for adding an exercise and for matching names when a set is logged. */
    val catalog: List<String> get() = ExerciseCatalog.build(profileState.value?.customExercises.orEmpty())

    fun toggle(index: Int) = save { it.toggled(index) }

    fun markAll(done: Boolean) = save { it.allMarked(done) }

    fun changeDay(day: Long) = save { it.copy(day = day) }

    fun addExercise(exercise: SessionExercise) {
        save { it.copy(exercises = it.exercises + exercise) }
        rememberExercise(exercise.name)
    }

    /** An exercise added to a logged workout also goes into "Your exercises" (Account & settings). */
    private fun rememberExercise(raw: String) {
        val name = ExerciseCatalog.cleanNewName(raw)?.let { ExerciseCatalog.canonical(it, catalog) } ?: return
        if (ExerciseCatalog.contains(profileState.value?.customExercises.orEmpty(), name)) return
        inBackground("add $name to your exercises") { profileRepository.addCustomExercise(name) }
    }

    fun removeExercise(index: Int) = save { session ->
        session.copy(exercises = session.exercises.filterIndexed { i, _ -> i != index })
    }

    /**
     * Logs a set for exercise [index] (dated on the workout's day, so it lands on the right day of
     * the chart), notes it on the checklist and checks the exercise off.
     */
    fun logSet(index: Int, weight: Float, reps: Int) {
        val session = current ?: return
        val exercise = session.exercises.getOrNull(index) ?: return
        val name = ExerciseCatalog.canonical(exercise.name, catalog)
        repository.saveLift(
            LiftEntry(athleteId = session.athleteId, exercise = name, weight = weight, reps = reps, date = timeOnDay(session.day))
        )
        save { it.withLoggedSet(index, weight, reps) }
    }

    /** Deletes the workout and hands it back for Undo. */
    fun delete(): WorkoutSession? {
        val session = current ?: return null
        repository.deleteSession(session)
        return session
    }

    fun restore(session: WorkoutSession) = repository.restoreSession(session)

    private fun save(change: (WorkoutSession) -> WorkoutSession) {
        val session = current ?: return
        if (!canEdit) return
        repository.saveSession(change(session))
    }
}
