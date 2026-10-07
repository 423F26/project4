package com.example.liftingapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Queries return Flow; the ViewModel turns them into LiveData with asLiveData() so the
 * Activity can observe them the same way project4's MainActivity observes `entries`.
 * Room re-emits automatically whenever the underlying tables change.
 */
@Dao
interface LiftDao {

    // --- Athletes ---
    @Insert
    suspend fun insertAthlete(athlete: Athlete): Long   // returns the new athlete's id

    @Query("SELECT * FROM athletes ORDER BY name COLLATE NOCASE")
    fun getAthletes(): Flow<List<Athlete>>

    // --- Sets ---
    @Insert
    suspend fun insertLift(entry: LiftEntry)

    /** Matches on the primary key (id), so the set keeps its original date. */
    @Update
    suspend fun updateLift(entry: LiftEntry)

    @Delete
    suspend fun deleteLift(entry: LiftEntry)

    /** One athlete's sets for one exercise, oldest first. Feeds the line chart (same order as project4). */
    @Query("SELECT * FROM lift_entries WHERE athleteId = :athleteId AND exercise = :exercise ORDER BY date ASC")
    fun getProgress(athleteId: Long, exercise: String): Flow<List<LiftEntry>>

    @Transaction
    @Query("SELECT * FROM lift_entries ORDER BY date DESC")
    fun getHistory(): Flow<List<LiftWithAthlete>>

    @Transaction
    @Query("SELECT * FROM lift_entries WHERE exercise = :exercise")
    fun getLiftsForExercise(exercise: String): Flow<List<LiftWithAthlete>>
}
