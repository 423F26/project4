package com.example.liftingapp.ui

import android.content.Context
import androidx.annotation.StyleRes
import androidx.core.content.edit
import com.example.liftingapp.R

/**
 * The themes offered in the palette menu (header, top right). Each maps to a style in
 * res/values/themes.xml. The choice is saved on the device and applied on launch.
 */
enum class AppTheme(val label: String, @StyleRes val styleRes: Int) {
    BLUE_GOLD("Blue & Gold", R.style.Theme_LIFTINGAPP_BlueGold),
    GRAPHITE("Graphite", R.style.Theme_LIFTINGAPP_Graphite),
    DAYLIGHT("Daylight", R.style.Theme_LIFTINGAPP_Daylight);

    companion object {
        private const val PREFS = "appearance"
        private const val KEY_THEME = "theme"

        fun current(context: Context): AppTheme {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
            return entries.firstOrNull { it.name == saved } ?: BLUE_GOLD
        }

        fun save(context: Context, theme: AppTheme) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme.name) }
        }
    }
}
