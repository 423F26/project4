package com.example.liftingapp.data

class LiftRepository(private val dao: LiftDao) {
    fun getEntriesForExercise(exercise: String) = dao.getEntriesForExercise(exercise)
    suspend fun insert(entry: LiftEntry) = dao.insert(entry)
}