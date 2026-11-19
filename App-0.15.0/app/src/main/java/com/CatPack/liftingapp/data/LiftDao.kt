package com.example.liftingapp.data

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only access to the athletes and sets saved on the phone before 0.9, for LegacyImport.
 * Everything new goes to Cloud Firestore (LiftRepository).
 */
@Dao
interface LiftDao {

    @Query("SELECT * FROM athletes ORDER BY name COLLATE NOCASE")
    suspend fun athletes(): List<LocalAthlete>

    @Query("SELECT * FROM lift_entries ORDER BY date ASC")
    suspend fun lifts(): List<LocalLiftEntry>

    @Query("SELECT COUNT(*) FROM lift_entries")
    suspend fun liftCount(): Int
}
