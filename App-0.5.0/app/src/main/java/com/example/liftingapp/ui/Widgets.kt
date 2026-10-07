package com.example.liftingapp.ui

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.annotation.AttrRes
import com.example.liftingapp.R
import com.google.android.material.color.MaterialColors

/** Spinner adapter using the app's themed item layouts (rounded field + Poppins text). */
fun spinnerAdapter(context: Context, items: List<String>): ArrayAdapter<String> =
    ArrayAdapter(context, R.layout.item_spinner, items.toMutableList()).apply {
        setDropDownViewResource(R.layout.item_spinner_dropdown)
    }

/** Shorter way to write OnItemSelectedListener object. */
fun Spinner.onItemSelected(action: (position: Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}

/** Resolves a color attribute (e.g. colorPrimary) from the current theme. */
fun View.themeColor(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr)
