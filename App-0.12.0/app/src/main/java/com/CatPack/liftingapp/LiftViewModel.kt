package com.example.liftingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.liftingapp.data.Athlete
import com.example.liftingapp.data.AthleteDetails
import com.example.liftingapp.data.CloudPaths
import com.example.liftingapp.data.awaitResult
import com.example.liftingapp.data.details
import com.example.liftingapp.data.ChartRange
import com.example.liftingapp.data.CloudErrors
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.ExerciseCatalog
import com.example.liftingapp.data.Follows
import com.example.liftingapp.data.AccountWorkouts
import com.example.liftingapp.data.FriendInbox
import com.example.liftingapp.data.Friends
import com.example.liftingapp.data.LibraryWorkout
import com.example.liftingapp.data.Recipient
import com.example.liftingapp.data.TeamPeople
import com.example.liftingapp.data.accountLibrary
import com.example.liftingapp.data.GeneralBoard
import com.example.liftingapp.data.LeaderboardRow
import com.example.liftingapp.data.LegacyImport
import com.example.liftingapp.data.LegacySummary
import com.example.liftingapp.data.LiftDatabase
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.LiftRepository
import com.example.liftingapp.data.LocalAthlete
import com.example.liftingapp.data.LogFilter
import com.example.liftingapp.data.LogItem
import com.example.liftingapp.data.LogSort
import com.example.liftingapp.data.ProfileRepository
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.data.Scope
import com.example.liftingapp.data.ScopeStore
import com.example.liftingapp.data.TeamRepository
import com.example.liftingapp.data.SentWorkout
import com.example.liftingapp.data.TeamInbox
import com.example.liftingapp.data.TeamWorkouts
import com.example.liftingapp.data.TeamWorkout
import com.example.liftingapp.data.UserProfile
import com.example.liftingapp.data.WorkoutFilter
import com.example.liftingapp.data.WorkoutRepository
import com.example.liftingapp.data.WorkoutSession
import com.example.liftingapp.data.WorkoutWithExercises
import com.example.liftingapp.data.generalLeaderboard
import com.example.liftingapp.data.inBackground
import com.example.liftingapp.data.sessionFrom
import com.example.liftingapp.data.teamLeaderboard
import com.example.liftingapp.data.workoutLog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the leaderboard list needs in one piece, so the adapter never shows rows with the wrong unit. */
data class LeaderboardState(val exercise: String, val rankBy: RankBy, val rows: List<LeaderboardRow>)

/** The Workouts tab's list for the selected filter, plus the filter itself (for the empty message). */
data class WorkoutListState(val filter: WorkoutFilter, val workouts: List<LibraryWorkout>)

/** The Log tab's workout log for the selected athlete, already sorted, filtered and grouped. */
data class WorkoutLogState(val sort: LogSort, val filter: LogFilter, val items: List<LogItem>, val total: Int)

/**
 * Holds screen state for MainActivity's tabs and survives rotation and theme changes.
 *
 * Everything belongs to one [scope] (the team or General space picked on the team select screen).
 * Switching teams starts a fresh MainActivity, and with it a fresh ViewModel for the new scope.
 * Athletes, sets and workout logs come from Cloud Firestore (LiftRepository); the workout library
 * is still on the phone (Room, WorkoutRepository).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiftViewModel(application: Application) : AndroidViewModel(application) {

    val uid: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope: Scope = ScopeStore.load(application, uid) ?: Scope.General

    private val database = LiftDatabase.getDatabase(application)
    private val workoutRepository = WorkoutRepository(database.workoutDao())
    val repository = LiftRepository(scope, uid)
    val profileRepository = ProfileRepository(uid)
    val teamRepository = TeamRepository(uid)
    private val legacyImport = LegacyImport(application)

    /** Cloud write failures from anywhere in the app; MainActivity shows them in a snackbar. */
    val cloudMessages = CloudErrors.messages

    // --- Who's signed in ---

    private val profileState: StateFlow<UserProfile?> = profileRepository.profile()
        .onEach { repository.profile = it }
        .catch { report("load your profile", it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profile: LiveData<UserProfile> = profileState.filterNotNull().asLiveData()
    val currentProfile: UserProfile? get() = profileState.value
    val isCoach: Boolean get() = currentProfile?.isCoach == true

    /** This person's name for things others see (sent workouts, the team's Send list). */
    val myName: String get() = currentProfile?.name?.takeIf { it.isNotBlank() }
        ?: FirebaseAuth.getInstance().currentUser?.displayName.orEmpty().ifBlank { "A teammate" }

    /** The account's own workouts (AccountWorkouts.kt): the same on every team, in General use and on every phone. */
    private val myWorkoutsState: StateFlow<List<TeamWorkout>> = AccountWorkouts.list(uid)
        .catch { report("load your workouts", it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Roster ---

    /** Null until the first result arrives, so screens can tell "loading" from "no athletes". */
    private val athleteState: StateFlow<List<Athlete>?> = repository.athletes()
        .catch {
            report("load the roster", it)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val athletes: LiveData<List<Athlete>> = athleteState.filterNotNull().asLiveData()
    val athleteList: List<Athlete> get() = athleteState.value.orEmpty()

    /** This person's own athlete profile in this scope, if they've added one. */
    val ownAthlete: Athlete? get() = athleteList.find { it.id == uid }

    init {
        // Accounts are athletes: make this person's own athlete profile here from their sign-up
        // details, so nobody has to add themselves. Not on teams for coaches (they'd land on their
        // own roster and leaderboard). There's no adding athletes by hand: a team's roster is
        // everyone who joined with its code.
        viewModelScope.launch {
            val profile = profileState.filterNotNull().first()
            if (scope == Scope.General || !profile.isCoach) {
                runCatching { repository.ensureOwnAthlete(profile) }   // offline: tried again next time
            }
            val name = myName
            // On the team's in-app Send list, so anyone on the team can send to this person.
            (scope as? Scope.Team)?.let { team ->
                runCatching { TeamPeople.announce(team.id, uid, name, profile.isCoach) }
            }
            // Workouts follow the account: ones made on this phone before, and team workouts made on any team.
            runCatching {
                AccountWorkouts.importFromPhone(uid, name, workoutRepository, workoutRepository.workouts.first())
            }.onFailure { report("move your workouts to your account", it) }
            runCatching { AccountWorkouts.carryFromTeams(uid, name) }   // offline: tried again next time
        }
    }

    /** Logging sets and workouts for [athlete]: coaches, or the athlete themselves (everything in General is yours). */
    fun canEdit(athlete: Athlete?): Boolean =
        athlete != null && (scope == Scope.General || isCoach || athlete.id == uid || athlete.ownerUid == uid)

    /** Teammates can hide their chart and workout log from other athletes (never from coaches). */
    fun canSeeProgress(athlete: Athlete?): Boolean = athlete != null && (canEdit(athlete) || isCoach || !athlete.hideProgress)

    // --- Log tab ---

    private val selectedExercise = MutableStateFlow(EXERCISES.first())
    private val selectedAthlete = MutableStateFlow<String?>(null)

    val exercise: String get() = selectedExercise.value
    val athleteId: String? get() = selectedAthlete.value
    val selected: Athlete? get() = athleteList.find { it.id == selectedAthlete.value }

    fun setExercise(exercise: String) { selectedExercise.value = exercise }
    fun selectAthlete(id: String?) { selectedAthlete.value = id }

    /** How much history the chart shows (1M / 3M / 6M / 1Y / All). Kept here so it survives rotation and theme changes. */
    var chartRange: ChartRange = ChartRange.ALL

    /** The selected athlete's id, but only when this person may see their progress (else null). */
    private val visibleAthleteId: Flow<String?> =
        combine(athleteState, selectedAthlete, profileState) { list, id, _ ->
            list?.find { it.id == id }?.takeIf { canSeeProgress(it) }?.id
        }.distinctUntilChanged()

    /** The chart data: the selected athlete's sets for the selected exercise, oldest first. */
    val entries: LiveData<List<LiftEntry>> =
        combine(visibleAthleteId, selectedExercise) { id, exercise -> id to exercise }
            .flatMapLatest { (id, exercise) ->
                if (id == null) flowOf(emptyList())
                else repository.lifts(id, exercise).catch {
                    report("load the chart", it)
                    emit(emptyList())
                }
            }
            .asLiveData()

    private val logSort = MutableStateFlow(LogSort.NEWEST)
    private val logFilter = MutableStateFlow(LogFilter.ALL)

    fun setLogSort(sort: LogSort) { logSort.value = sort }
    fun setLogFilter(filter: LogFilter) { logFilter.value = filter }
    val currentLogSort: LogSort get() = logSort.value
    val currentLogFilter: LogFilter get() = logFilter.value

    private val sessions: Flow<List<WorkoutSession>> = visibleAthleteId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.sessions(id).catch {
            report("load the workout log", it)
            emit(emptyList())
        }
    }

    val workoutLog: LiveData<WorkoutLogState> =
        combine(sessions, logSort, logFilter) { list, sort, filter ->
            WorkoutLogState(sort, filter, workoutLog(list, sort, filter), list.size)
        }.asLiveData()

    // --- Exercise list (Log tab, Leaderboard, workout editor) ---

    private val catalogState: StateFlow<List<String>> =
        combine(profileState, workoutRepository.workouts, myWorkoutsState, athleteState) { profile, workouts, mine, athletes ->
            val fromWorkouts = (workouts.flatMap { it.exercises } + mine.flatMap { it.exercises }).map { it.name }
            val logged = athletes.orEmpty().flatMap { it.bests.keys }
            ExerciseCatalog.build(profile?.customExercises.orEmpty(), fromWorkouts + logged)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ExerciseCatalog.build(emptyList()))

    val exerciseCatalog: LiveData<List<String>> = catalogState.asLiveData()
    val currentCatalog: List<String> get() = catalogState.value
    val customExercises: List<String> get() = currentProfile?.customExercises.orEmpty()

    /** Adds an exercise to this person's list (it then shows up everywhere) and returns its name. */
    fun addCustomExercise(raw: String): String? {
        val name = ExerciseCatalog.cleanNewName(raw) ?: return null
        if (ExerciseCatalog.contains(currentCatalog, name)) return ExerciseCatalog.canonical(name, currentCatalog)
        inBackground("add $name") { profileRepository.addCustomExercise(name) }
        return name
    }

    fun removeCustomExercise(name: String) {
        inBackground("remove $name") { profileRepository.removeCustomExercise(name) }
    }

    // --- Workouts tab ---

    private val workoutFilter = MutableStateFlow(WorkoutFilter.ALL)

    val currentWorkoutFilter: WorkoutFilter get() = workoutFilter.value

    fun setWorkoutFilter(filter: WorkoutFilter) { workoutFilter.value = filter }

    /** Re-sorts when the workouts change, re-filters when the All / Premade / Mine toggle changes. */
    val workouts: LiveData<WorkoutListState> =
        combine(myWorkoutsState, workoutRepository.workouts, workoutFilter) { mine, phone, filter ->
            WorkoutListState(filter, accountLibrary(mine, phone, filter))
        }.asLiveData()

    /** The whole library (for "Log workout"), in the Workouts tab's order. */
    private val libraryState: StateFlow<List<WorkoutWithExercises>> =
        combine(myWorkoutsState, workoutRepository.workouts) { mine, phone ->
            accountLibrary(mine, phone, WorkoutFilter.ALL).map { it.item }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workoutLibraryAll: List<WorkoutWithExercises> get() = libraryState.value

    /** One of the account's workouts by id, if it's loaded (e.g. right after the editor saved it). */
    fun myWorkout(id: String): TeamWorkout? = myWorkoutsState.value.firstOrNull { it.id == id }

    // --- Leaderboard tab ---

    private val leaderboardExercise = MutableStateFlow(EXERCISES.first())
    private val rankByMode = MutableStateFlow(RankBy.ESTIMATED_MAX)

    val currentLeaderboardExercise: String get() = leaderboardExercise.value
    val currentRankBy: RankBy get() = rankByMode.value

    fun setLeaderboardExercise(exercise: String) { leaderboardExercise.value = exercise }
    fun setRankBy(rankBy: RankBy) { rankByMode.value = rankBy }

    /**
     * A team ranks its own roster (already loaded for the Log tab, so no extra reads). General
     * ranks everyone's published General rows, sorted by the server.
     */
    val leaderboard: LiveData<LeaderboardState> = when (scope) {
        is Scope.Team ->
            combine(athleteState.filterNotNull(), leaderboardExercise, rankByMode) { list, exercise, rankBy ->
                LeaderboardState(exercise, rankBy, teamLeaderboard(list, exercise, rankBy, uid))
            }
        Scope.General ->
            combine(leaderboardExercise, rankByMode) { exercise, rankBy -> exercise to rankBy }
                .flatMapLatest { (exercise, rankBy) ->
                    GeneralBoard.top(exercise, rankBy)
                        .map { entries -> LeaderboardState(exercise, rankBy, generalLeaderboard(entries, exercise, rankBy, uid)) }
                        .catch {
                            report("load the General leaderboard", it)
                            emit(LeaderboardState(exercise, rankBy, emptyList()))
                        }
                }
    }.asLiveData()

    // --- Following (Leaderboard tab; see data/Follows.kt) ---

    /** The accounts this person follows. */
    val following: LiveData<Set<String>> = Follows.following(uid)
        .catch { report("load who you follow", it); emit(emptySet()) }
        .asLiveData()

    /** How many people follow this person. Only they can see it. */
    val followerCount: LiveData<Int> = Follows.followerCount(uid)
        .catch { report("load your followers", it); emit(0) }
        .asLiveData()

    fun setFollowing(targetUid: String, name: String, follow: Boolean) {
        if (targetUid == uid) return
        inBackground(if (follow) "follow" else "unfollow") {
            if (follow) Follows.follow(uid, targetUid, name) else Follows.unfollow(uid, targetUid)
        }
    }

    // --- Writes: athletes ---

    /**
     * Saves changed details (the edit dialog, and the Send dialog for phone and email). Editing your
     * own athlete also updates your account and your athlete profile on every other team.
     */
    fun updateAthlete(athlete: Athlete) {
        if (!canEdit(athlete)) return
        repository.updateAthlete(athlete)
        if (athlete.id == uid) updateMyProfile(athlete.details(), alreadySavedHere = true)
    }

    /**
     * Account & settings → edit profile: name, position, body weight and phone go to the account
     * and to this person's own athlete in General use and on each of their teams, and the name to
     * the member list coaches see.
     */
    fun updateMyProfile(details: AthleteDetails, alreadySavedHere: Boolean = false) {
        val settings = currentProfile
        inBackground("save your profile") {
            profileRepository.updateDetails(details)
            val teams = CloudPaths.myTeams(uid).get().awaitResult().documents
            val scopes = listOf<Scope>(Scope.General) + teams.map { Scope.Team(it.id, it.getString("teamName").orEmpty()) }
            scopes.forEach { other ->
                val here = other.isSameAs(scope)
                if (alreadySavedHere && here) return@forEach
                val repo = if (here) repository else LiftRepository(other, uid).also { it.profile = settings }
                repo.updateOwnDetails(details)
            }
            teams.forEach { team -> CloudPaths.members(team.id).document(uid).update("name", details.name) }
        }
    }

    fun deleteAthlete(athlete: Athlete, onDone: () -> Unit) {
        inBackground("delete ${athlete.name}") {
            repository.deleteAthlete(athlete.id)
            onDone()
        }
    }

    // --- Writes: sets ---

    /** Logs a set. [date] defaults to now; sets logged from a past workout use that day. */
    fun addEntry(athleteId: String, exercise: String, weight: Float, reps: Int, date: Long = System.currentTimeMillis()) {
        repository.saveLift(LiftEntry(athleteId = athleteId, exercise = exercise, weight = weight, reps = reps, date = date))
    }

    /** Saves a corrected set. [entry] keeps its original id and date; [previous] is how it was. */
    fun updateEntry(entry: LiftEntry, previous: LiftEntry) {
        repository.saveLift(entry, previous = previous)
    }

    fun deleteEntry(entry: LiftEntry) = repository.deleteLift(entry)

    /** Puts a deleted set back exactly as it was (same id and date). Used by the Undo snackbar. */
    fun restoreEntry(entry: LiftEntry) {
        repository.saveLift(entry)
    }

    // --- Writes: workout log ---

    /** Logs [item] for [athleteId] on local day [day]. Returns the log entry's id. */
    fun logWorkout(item: WorkoutWithExercises, athleteId: String, day: Long, source: String): String {
        val id = repository.addSession(sessionFrom(item, athleteId, day, source))
        rememberExercises(item.exercises.map { it.name })
        return id
    }

    /**
     * Puts the exercises of a logged workout in this person's "Your exercises" list (Account &
     * settings), so what they actually do is easy to find. Same spelling as the exercise list.
     */
    fun rememberExercises(names: List<String>) {
        val known = customExercises
        val new = names.mapNotNull(ExerciseCatalog::cleanNewName)
            .map { ExerciseCatalog.canonical(it, currentCatalog) }
            .filter { !ExerciseCatalog.contains(known, it) }
            .distinctBy { it.lowercase() }
        if (new.isEmpty()) return
        inBackground("add to your exercises") { profileRepository.addCustomExercises(new) }
    }

    fun deleteSession(session: WorkoutSession) = repository.deleteSession(session)

    fun restoreSession(session: WorkoutSession) = repository.restoreSession(session)

    // --- Workouts library (on the phone) ---

    /** Looks up a premade workout on this phone and hands it to [onLoaded]; skipped if it's gone. */
    fun loadWorkout(id: Long, onLoaded: (WorkoutWithExercises) -> Unit) {
        viewModelScope.launch { workoutRepository.workout(id)?.let(onLoaded) }
    }

    /**
     * Deletes one of your workouts from the account (everywhere), or a premade one from this phone.
     * Keep [entry] to undo with restoreWorkout(). Team workouts made from it stay with their teams.
     */
    fun deleteWorkout(entry: LibraryWorkout) {
        val cloud = entry.cloud
        if (cloud != null) inBackground("delete ${cloud.name}") { AccountWorkouts.delete(uid, cloud.id) }
        else viewModelScope.launch { workoutRepository.delete(entry.item.workout) }
    }

    /** Puts a deleted workout back exactly as it was (same ids). Used by the Undo snackbar. */
    fun restoreWorkout(entry: LibraryWorkout) {
        val cloud = entry.cloud
        if (cloud != null) inBackground("restore ${cloud.name}") { AccountWorkouts.restore(uid, cloud) }
        else viewModelScope.launch { workoutRepository.restore(entry.item) }
    }

    // --- Team workouts and in-app sending (teams only; see data/TeamWorkouts.kt) ---

    /** Workouts anyone on the team made, shared with everyone on it. Empty in General use. */
    val teamWorkouts: LiveData<List<TeamWorkout>> = when (scope) {
        is Scope.Team -> TeamWorkouts.list(scope.id)
            .catch { report("load the team workouts", it); emit(emptyList()) }
            .asLiveData()
        Scope.General -> flowOf(emptyList<TeamWorkout>()).asLiveData()
    }

    /** Workouts sent in the app to this person or the whole team (and ones they sent). */
    val inbox: LiveData<List<SentWorkout>> = when (scope) {
        is Scope.Team -> TeamInbox.list(scope.id, uid)
            .catch { report("load sent workouts", it); emit(emptyList()) }
            .asLiveData()
        Scope.General -> flowOf(emptyList<SentWorkout>()).asLiveData()
    }

    /** A team workout's maker, or any coach, can edit or delete it. */
    fun canEditTeamWorkout(createdBy: String): Boolean = createdBy == uid || isCoach

    fun deleteTeamWorkout(workout: TeamWorkout) {
        val team = scope as? Scope.Team ?: return
        inBackground("delete ${workout.name}") { TeamWorkouts.delete(team.id, workout.id) }
    }

    fun deleteSent(sent: SentWorkout) {
        val team = scope as? Scope.Team ?: return
        inBackground("remove ${sent.name}") { TeamInbox.delete(team.id, sent.id) }
    }

    /** Everyone else on the open team (athletes and coaches), for "Send in CatPack". Empty in General use. */
    val teamPeople: LiveData<List<Recipient>> = when (scope) {
        is Scope.Team -> TeamPeople.list(scope.id, uid)
            .catch { report("load your teammates", it); emit(emptyList()) }
            .asLiveData()
        Scope.General -> flowOf(emptyList<Recipient>()).asLiveData()
    }

    /** Workouts friends sent this account. Shown on every team and in General use. */
    val friendInbox: LiveData<List<SentWorkout>> = FriendInbox.list(uid)
        .catch { report("load workouts friends sent you", it); emit(emptyList()) }
        .asLiveData()

    fun deleteFriendSent(sent: SentWorkout) {
        inBackground("remove ${sent.name}") { FriendInbox.delete(uid, sent.id) }
    }

    /** People who follow each other with this account (Friends in InAppSending.kt). */
    fun loadFriends(onLoaded: (List<Recipient>) -> Unit) {
        viewModelScope.launch {
            val friends = try { Friends.mutual(uid) } catch (e: Exception) { report("load your friends", e); emptyList() }
            onLoaded(friends)
        }
    }

    /** Sends [item] in the app to someone on the open team, or to everyone on it when [to] is null. */
    fun sendToTeam(item: WorkoutWithExercises, to: Recipient?, onDone: (String) -> Unit) {
        val team = scope as? Scope.Team ?: return
        val name = myName
        inBackground("send the workout") {
            TeamInbox.send(team.id, item, uid, name, to?.uid, to?.name ?: "Everyone on ${team.name}")
            onDone(if (to == null) "Sent to everyone on ${team.name}" else "Sent to ${to.name}")
        }
    }

    /** Sends [item] to a mutual friend's account; it shows under "Sent to you" wherever they are. */
    fun sendToFriend(item: WorkoutWithExercises, to: Recipient, onDone: (String) -> Unit) {
        val name = myName
        inBackground("send the workout") {
            FriendInbox.send(item, uid, name, to)
            onDone("Sent to ${to.name}")
        }
    }

    // --- Team checks and old data ---

    /** Calls [onRemoved] if this person is no longer on the open team (left elsewhere, or removed). */
    fun checkMembership(onRemoved: () -> Unit) {
        val team = scope as? Scope.Team ?: return
        viewModelScope.launch {
            if (teamRepository.isStillMember(team.id) == false) onRemoved()
        }
    }

    /** Athletes and sets saved on this phone before teams existed and not imported yet, if any. */
    fun checkLegacyData(onFound: (LegacySummary) -> Unit) {
        viewModelScope.launch { legacyImport.pending()?.let(onFound) }
    }

    fun legacySummary(onResult: (LegacySummary?) -> Unit) {
        viewModelScope.launch { onResult(legacyImport.summary()) }
    }

    fun dismissLegacyData() = legacyImport.markHandled()

    fun importLegacy(chosen: List<LocalAthlete>, onDone: (sets: Int) -> Unit) {
        val asOwnProfile = !isCoach
        inBackground("import the old data") {
            val copied = legacyImport.importInto(repository, chosen, asOwnProfile)
            onDone(copied)
        }
    }

    private fun report(what: String, error: Throwable) = CloudErrors.report(what, error)
}

/** Same team (by id) or both General, ignoring the cached team name and sport. */
private fun Scope.isSameAs(other: Scope): Boolean = when (this) {
    Scope.General -> other == Scope.General
    is Scope.Team -> other is Scope.Team && other.id == id
}
