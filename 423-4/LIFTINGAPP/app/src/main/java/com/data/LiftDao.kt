#!/usr/bin/env kotlin
package com.example.lifttracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LiftDao {
    @Insert
    suspend fun insert(entry: LiftEntry)

    @Query("SELECT * FROM lift_entries WHERE exercise = :exercise ORDER BY date ASC")
    fun getEntriesForExercise(exercise: String): LiveData<List<LiftEntry>>
}
