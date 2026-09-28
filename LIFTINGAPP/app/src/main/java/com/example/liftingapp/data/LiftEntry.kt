package com.example.liftingapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged set.this adds:
 *  - athleteId: who did the set (foreign key to [Athlete]; deleting an athlete deletes their sets)
 *  - reps: needed for estimated 1RM, volume, and everything in the analytics backlog
 */
@Entity(
    tableName = "lift_entries",
    foreignKeys = [
        ForeignKey(
            entity = Athlete::class,
            parentColumns = ["id"],
            childColumns = ["athleteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("athleteId")]
)
data class LiftEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val athleteId: Long,
    val exercise: String,      // "Bench", "Squat", "Deadlift", ...
    val weight: Float,         // pounds
    val reps: Int,
    val date: Long = System.currentTimeMillis()
)
