package com.example.liftingapp.data

/** The lifts offered in the app. Add more here and they show up everywhere. */
val EXERCISES = listOf("Bench", "Squat", "Deadlift", "Power Clean")

/** Reps above this make 1RM estimates unreliable, so the log screen caps input here. */
const val MAX_REPS = 20

object Strength {
    /**
     * Epley formula: e1RM = weight × (1 + reps / 30).
     * A true single (1 rep) returns the weight itself.
     * This puts a 225×5 and a 245×2 on the same scale so they can be compared.
     */
    fun estimatedOneRepMax(weight: Float, reps: Int): Float {
        require(reps >= 1) { "reps must be at least 1" }
        return if (reps == 1) weight else weight * (1 + reps / 30f)
    }
}
