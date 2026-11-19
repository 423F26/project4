package com.example.liftingapp.ui

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.liftingapp.data.DistanceUnit

/**
 * Miles or kilometers for cardio, remembered on this phone. Changed in Account & settings →
 * Appearance, or with the mi/km switch in the log dialog. Only the display changes: cardio sets are
 * always saved in meters and seconds.
 */
object DistanceUnits {
    private const val PREFS = "appearance"
    private const val KEY_UNIT = "distance_unit"

    private val live = MutableLiveData<DistanceUnit>()

    /** Fires whenever the unit changes, so open screens can redraw. */
    fun changes(context: Context): LiveData<DistanceUnit> {
        if (live.value == null) live.value = current(context)
        return live
    }

    fun current(context: Context): DistanceUnit =
        DistanceUnit.fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UNIT, null))

    fun save(context: Context, unit: DistanceUnit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_UNIT, unit.key) }
        if (live.value != unit) live.value = unit
    }
}
