#!/usr/bin/env kotlin
package com.example.lifttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lift_entries")
data class LiftEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exercise: String,      // "Bench", "Squat", "Deadlift"
    val weight: Float,
    val date: Long = System.currentTimeMillis()
)