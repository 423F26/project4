package com.example.liftingapp.data

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which team (or General) each account last picked on this phone, so reopening the app
 * goes straight back to it. Kept per account, so a second person signing in on the same phone
 * starts on the team select screen instead of inside someone else's team.
 */
object ScopeStore {
    private const val PREFS = "scope"
    private const val KEY_ASK_EVERY_LAUNCH = "ask_every_launch"

    fun load(context: Context, uid: String): Scope? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString("${uid}_type", null)) {
            TYPE_GENERAL -> Scope.General
            TYPE_TEAM -> {
                val id = prefs.getString("${uid}_team_id", null) ?: return null
                Scope.Team(
                    id = id,
                    name = prefs.getString("${uid}_team_name", null).orEmpty().ifEmpty { "Team" },
                    sport = prefs.getString("${uid}_team_sport", null).orEmpty(),
                    isOwner = prefs.getBoolean("${uid}_team_owner", false)
                )
            }
            else -> null
        }
    }

    fun save(context: Context, uid: String, scope: Scope) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            when (scope) {
                Scope.General -> {
                    putString("${uid}_type", TYPE_GENERAL)
                    remove("${uid}_team_id")
                }
                is Scope.Team -> {
                    putString("${uid}_type", TYPE_TEAM)
                    putString("${uid}_team_id", scope.id)
                    putString("${uid}_team_name", scope.name)
                    putString("${uid}_team_sport", scope.sport)
                    putBoolean("${uid}_team_owner", scope.isOwner)
                }
            }
        }
    }

    fun clear(context: Context, uid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove("${uid}_type")
            remove("${uid}_team_id")
            remove("${uid}_team_name")
            remove("${uid}_team_sport")
            remove("${uid}_team_owner")
        }
    }

    /** Account & settings → "Choose a team every time I open the app". Off by default. */
    fun askEveryLaunch(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASK_EVERY_LAUNCH, false)

    fun setAskEveryLaunch(context: Context, ask: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASK_EVERY_LAUNCH, ask) }
    }

    private const val TYPE_GENERAL = "general"
    private const val TYPE_TEAM = "team"
}
