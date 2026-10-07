package com.example.liftingapp.data

import kotlinx.coroutines.flow.Flow

/** The single place the rest of the app goes for data. Later this is where a backend API would plug in. */
class LiftRepository(private val dao: LiftDao) {

    val athletes: Flow<List<Athlete>> = dao.getAthletes()
    val history: Flow<List<LiftWithAthlete>> = dao.getHistory()

    fun progressFor(athleteId: Long, exercise: String): Flow<List<LiftEntry>> =
        dao.getProgress(athleteId, exercise)

    fun liftsFor(exercise: String): Flow<List<LiftWithAthlete>> = dao.getLiftsForExercise(exercise)

    suspend fun addAthlete(name: String, positionGroup: String, bodyWeightLb: Float): Long =
        dao.insertAthlete(Athlete(name = name, positionGroup = positionGroup, bodyWeightLb = bodyWeightLb))

    suspend fun insert(entry: LiftEntry) = dao.insertLift(entry)

    suspend fun update(entry: LiftEntry) = dao.updateLift(entry)

    suspend fun delete(entry: LiftEntry) = dao.deleteLift(entry)
}
