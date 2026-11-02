package com.example.liftingapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One athlete on the roster. Every logged set belongs to an athlete, which is what
 * makes teammate comparisons, leaderboards and (later) a coach roster view possible.
 */
@Entity(tableName = "athletes")
data class Athlete(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val positionGroup: String,   // e.g. "OL", "WR", "DB" — used later for position-group benchmarking
    val bodyWeightLb: Float,     // needed for pound-for-pound rankings
    val phone: String? = null,   // optional; fills in the "To" field when a workout is texted
    val email: String? = null    // optional; fills in the "To" field when a workout is emailed
)
