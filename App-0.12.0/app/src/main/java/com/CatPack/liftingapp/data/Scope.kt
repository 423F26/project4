package com.example.liftingapp.data

/**
 * Whose data the app is showing: one team's, or the signed-in person's own ("General use").
 * Picked on the team select screen and switched with the header's Switch team button.
 *
 *  - Team: everyone on the team sees the team's roster, charts, workout logs and leaderboard.
 *  - General: only your own athletes; the leaderboard shows everyone else using General.
 */
sealed interface Scope {
    val title: String

    data object General : Scope {
        override val title: String get() = "General use"
    }

    data class Team(val id: String, val name: String, val sport: String = "", val isOwner: Boolean = false) : Scope {
        override val title: String get() = name
    }
}
