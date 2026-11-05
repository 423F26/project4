package com.example.liftingapp.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StyleRes
import androidx.core.content.edit
import com.example.liftingapp.R

/**
 * Light or dark mode, picked in Account & settings → Appearance. "System default" follows the phone's own
 * dark-mode setting (and switches with it). Each mode maps to a style in res/values/themes.xml:
 * Dark is the navy-and-gold look, Light is the bright navy-on-white look.
 */
enum class AppTheme(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark");

    fun isDark(context: Context): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @StyleRes
    fun styleRes(context: Context): Int = if (isDark(context)) R.style.Theme_LIFTINGAPP_Dark else R.style.Theme_LIFTINGAPP_Light

    companion object {
        private const val PREFS = "appearance"
        private const val KEY_THEME = "theme"

        fun current(context: Context): AppTheme {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
            return when (saved) {
                // Themes saved by 0.8 and earlier: the two dark palettes became Dark, Daylight became Light.
                "BLUE_GOLD", "GRAPHITE" -> DARK
                "DAYLIGHT" -> LIGHT
                else -> entries.firstOrNull { it.name == saved } ?: SYSTEM
            }
        }

        fun save(context: Context, theme: AppTheme) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme.name) }
        }

        /** Call in onCreate before any views are inflated. */
        fun apply(activity: Activity) = activity.setTheme(current(activity).styleRes(activity))
    }
}
