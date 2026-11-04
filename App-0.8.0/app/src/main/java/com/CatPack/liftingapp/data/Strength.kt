package com.example.liftingapp.data

/** The main lifts. They lead every exercise list; the full list is in ExerciseCatalog.kt. */
val EXERCISES = listOf("Bench", "Squat", "Deadlift", "Power Clean", "Pull-ups", "Push-ups")

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

    /**
     * The set with the highest estimated max among [sets] (one exercise), or null if there are none.
     * Ties go to the earlier set, so a repeat of the same numbers doesn't "move" the best.
     * Pound-for-pound uses [bodyWeightLb]; a missing body weight gives 0.
     */
    fun bestOf(sets: List<LiftEntry>, bodyWeightLb: Float): BestSet? {
        val best = sets
            .sortedBy { it.date }
            .maxByOrNull { estimatedOneRepMax(it.weight, it.reps) } ?: return null
        val e1rm = estimatedOneRepMax(best.weight, best.reps)
        return BestSet(
            weight = best.weight,
            reps = best.reps,
            e1rm = e1rm,
            p4p = poundForPound(e1rm, bodyWeightLb),
            date = best.date
        )
    }

    fun poundForPound(e1rm: Float, bodyWeightLb: Float): Float = if (bodyWeightLb > 0f) e1rm / bodyWeightLb else 0f
}
