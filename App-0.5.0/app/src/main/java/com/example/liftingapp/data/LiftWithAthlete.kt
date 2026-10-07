package com.example.liftingapp.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A set together with the athlete who did it. Room fills in [athlete] automatically
 * for any DAO query that returns this type (the query must be marked @Transaction).
 */
data class LiftWithAthlete(
    @Embedded val lift: LiftEntry,
    @Relation(parentColumn = "athleteId", entityColumn = "id")
    val athlete: Athlete
)
