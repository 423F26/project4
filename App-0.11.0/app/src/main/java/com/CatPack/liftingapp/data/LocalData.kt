package com.example.liftingapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Athletes and sets as versions up to 0.8 stored them, on the phone only. Since 0.9 they live in
 * Cloud Firestore (see Models.kt); these tables are kept unchanged (same names and columns, so no
 * database migration) only so LegacyImport can bring old data over. Nothing new is written here.
 */

@Entity(tableName = "athletes")
data class LocalAthlete(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val positionGroup: String,
    val bodyWeightLb: Float,
    val phone: String? = null,
    val email: String? = null
)

@Entity(
    tableName = "lift_entries",
    foreignKeys = [
        ForeignKey(
            entity = LocalAthlete::class,
            parentColumns = ["id"],
            childColumns = ["athleteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("athleteId")]
)
data class LocalLiftEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val athleteId: Long,
    val exercise: String,
    val weight: Float,
    val reps: Int,
    val date: Long = System.currentTimeMillis()
)
