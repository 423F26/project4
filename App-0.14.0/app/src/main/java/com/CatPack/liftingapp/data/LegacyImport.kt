package com.example.liftingapp.data

import android.content.Context
import androidx.core.content.edit

/** What's left on the phone from before 0.9: athletes and how many sets they have between them. */
data class LegacySummary(val athletes: List<LocalAthlete>, val setCount: Int)

/**
 * Brings athletes and sets saved on this phone by version 0.8 and earlier (Room only) into the
 * cloud, into whichever team or General space is open. Offered once from the Log tab and any time
 * from Account & settings. The phone's copy isn't deleted, so nothing is lost if the import is interrupted.
 *
 * Coaches bring over every athlete. Athlete accounts can only have one athlete (themselves), so
 * they pick which one is them; its sets go onto their own profile.
 */
class LegacyImport(context: Context) {

    private val appContext = context.applicationContext
    private val dao = LiftDatabase.getDatabase(appContext).liftDao()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Old data that hasn't been imported or dismissed yet, or null. */
    suspend fun pending(): LegacySummary? {
        if (prefs.getBoolean(KEY_HANDLED, false)) return null
        return summary()
    }

    /** Old data on the phone whether or not it was offered before (Account & settings shows this), or null if none. */
    suspend fun summary(): LegacySummary? {
        val athletes = dao.athletes()
        if (athletes.isEmpty()) return null
        return LegacySummary(athletes, dao.liftCount())
    }

    /** Stops the one-time prompt from coming back (Account & settings can still import). */
    fun markHandled() = prefs.edit { putBoolean(KEY_HANDLED, true) }

    /**
     * Copies [chosen] and their sets into [repo]'s scope. With [asOwnProfile] (athlete accounts)
     * exactly one athlete is expected and becomes the person's own profile, merged with it if it
     * already exists. Returns how many sets were copied.
     */
    suspend fun importInto(repo: LiftRepository, chosen: List<LocalAthlete>, asOwnProfile: Boolean): Int {
        val allLifts = dao.lifts().groupBy { it.athleteId }
        var copied = 0
        val targets = if (asOwnProfile) chosen.take(1) else chosen
        targets.forEach { local ->
            val sets = allLifts[local.id].orEmpty()
            val targetId = repo.newAthleteId(isOwnProfile = asOwnProfile)
            val athleteRef = CloudPaths.athlete(repo.scope, repo.uid, targetId)
            val existing = if (asOwnProfile) athleteRef.get().awaitResult().takeIf { it.exists() }?.toAthlete() else null
            val bodyWeight = existing?.bodyWeightLb ?: local.bodyWeightLb

            val cloudSets = sets.map { LiftEntry(athleteId = targetId, exercise = it.exercise, weight = it.weight, reps = it.reps, date = it.date) }
            val imported = cloudSets.groupBy { it.exercise }.mapNotNull { (exercise, list) ->
                Strength.bestOf(list, bodyWeight)?.let { exercise to it }
            }.toMap()
            val bests = (existing?.bests.orEmpty().keys + imported.keys).associateWith { exercise ->
                listOfNotNull(existing?.bests?.get(exercise), imported[exercise]).maxBy { it.e1rm }
            }

            val athlete = existing?.copy(bests = bests) ?: Athlete(
                id = targetId,
                name = local.name,
                positionGroup = local.positionGroup,
                bodyWeightLb = local.bodyWeightLb,
                phone = local.phone,
                email = local.email,
                ownerUid = repo.uid,
                linkedUid = if (asOwnProfile) repo.uid else null,
                hideProgress = asOwnProfile && repo.profile?.shareProgress == false,
                bests = bests
            )

            // The athlete first, then their sets in batches (Firestore takes up to 500 writes per batch).
            val lifts = CloudPaths.lifts(repo.scope, repo.uid, targetId)
            val writes = cloudSets.chunked(LiftRepository.MAX_BATCH)
            athleteRef.set(athlete.toMap()).orReport("import ${local.name}")
            writes.forEach { chunk ->
                val batch = CloudPaths.db.batch()
                chunk.forEach { set -> batch.set(lifts.document(), set.toMap(loggedBy = repo.uid) + ("importedFromPhone" to true)) }
                batch.commit().orReport("import ${local.name}'s sets")
            }
            copied += cloudSets.size
            if (repo.scope == Scope.General) GeneralBoard.syncOne(repo.uid, targetId, repo.profile)
        }
        markHandled()
        return copied
    }

    private companion object {
        const val PREFS = "legacy_import"
        const val KEY_HANDLED = "handled"
    }
}
