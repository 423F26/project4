package com.example.liftingapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.R
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.ExerciseCatalog
import com.example.liftingapp.databinding.DialogPickerBinding
import com.example.liftingapp.databinding.ItemPickerRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** One choice in a picker: what's shown, and what's handed back when it's tapped. */
data class PickerItem<T>(val title: String, val subtitle: String? = null, val value: T)

data class PickerSection<T>(val title: String, val items: List<PickerItem<T>>)

/**
 * A dialog with a search box over a sectioned list. Typing filters the list (by title or detail).
 * With [onAdd], a search that matches no title exactly offers "Add “…”" at the top, for things
 * that aren't listed yet (custom exercises).
 */
fun <T> showPickerDialog(
    context: Context,
    title: String,
    searchHint: String,
    sections: List<PickerSection<T>>,
    selectedTitle: String? = null,
    emptyMessage: String = "Nothing matches that.",
    addLabel: String = "Add",
    onAdd: ((String) -> Unit)? = null,
    onPick: (T) -> Unit
) {
    val b = DialogPickerBinding.inflate(LayoutInflater.from(context))
    b.searchLayout.hint = searchHint
    lateinit var dialog: AlertDialog

    val adapter = PickerAdapter(
        selectedTitle = selectedTitle,
        addLabel = addLabel,
        onPick = { value ->
            dialog.dismiss()
            @Suppress("UNCHECKED_CAST")
            onPick(value as T)
        },
        onAdd = { text ->
            dialog.dismiss()
            onAdd?.invoke(text)
        }
    )
    b.list.adapter = adapter

    fun refresh() {
        val query = b.searchInput.text?.toString()?.trim().orEmpty()
        val rows = mutableListOf<PickerRow>()
        val exact = sections.any { section -> section.items.any { it.title.equals(query, ignoreCase = true) } }
        if (onAdd != null && query.isNotEmpty() && !exact) rows += PickerRow.Add(query)
        sections.forEach { section ->
            val matches = if (query.isEmpty()) section.items
            else section.items.filter {
                it.title.contains(query, ignoreCase = true) || it.subtitle?.contains(query, ignoreCase = true) == true
            }
            if (matches.isNotEmpty()) {
                rows += PickerRow.Header(section.title)
                matches.forEach { rows += PickerRow.Item(it.title, it.subtitle, it.value) }
            }
        }
        adapter.submit(rows)
        b.emptyText.isVisible = rows.isEmpty()
        b.emptyText.text = emptyMessage
    }

    b.searchInput.doAfterTextChanged { refresh() }
    refresh()

    dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(b.root)
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * The exercise picker used by the Log tab and the Leaderboard: main lifts, then the person's own
 * exercises, then every other exercise from the Workouts tab, A to Z. Typing a name that isn't
 * there offers to add it ([onAdd] gets the typed name and returns the name to select, or null).
 */
fun showExercisePicker(
    context: Context,
    catalog: List<String>,
    custom: List<String>,
    current: String,
    onAdd: ((String) -> String?)?,
    onPick: (String) -> Unit
) {
    val customKeys = custom.map { it.lowercase() }.toSet()
    val main = catalog.filter { name -> EXERCISES.any { it.equals(name, ignoreCase = true) } }
    val mine = catalog.filter { it.lowercase() in customKeys && it !in main }
    val rest = catalog.filter { it !in main && it !in mine }
    val sections = listOfNotNull(
        PickerSection("Main lifts", main.map { PickerItem(it, value = it) }),
        mine.takeIf { it.isNotEmpty() }?.let { list -> PickerSection("Your exercises", list.map { PickerItem(it, value = it) }) },
        PickerSection("All exercises", rest.map { PickerItem(it, value = it) })
    )
    showPickerDialog(
        context = context,
        title = "Exercise",
        searchHint = if (onAdd != null) "Search or add an exercise" else "Search exercises",
        sections = sections,
        selectedTitle = current,
        emptyMessage = "No exercise matches that.",
        addLabel = "Add exercise",
        onAdd = onAdd?.let { add ->
            { typed: String ->
                val name = ExerciseCatalog.cleanNewName(typed)
                if (name != null) add(name)?.let(onPick)
            }
        },
        onPick = onPick
    )
}

private sealed interface PickerRow {
    data class Header(val title: String) : PickerRow
    data class Item(val title: String, val subtitle: String?, val value: Any?) : PickerRow
    data class Add(val text: String) : PickerRow
}

private class PickerAdapter(
    private val selectedTitle: String?,
    private val addLabel: String,
    private val onPick: (Any?) -> Unit,
    private val onAdd: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<PickerRow> = emptyList()

    class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    class RowHolder(val binding: ItemPickerRowBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")   // the whole list changes with every keystroke
    fun submit(newRows: List<PickerRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = if (rows[position] is PickerRow.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_picker_header, parent, false) as TextView)
        } else {
            val holder = RowHolder(ItemPickerRowBinding.inflate(inflater, parent, false))
            holder.binding.root.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                when (val row = rows[position]) {
                    is PickerRow.Item -> onPick(row.value)
                    is PickerRow.Add -> onAdd(row.text)
                    is PickerRow.Header -> Unit
                }
            }
            holder
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PickerRow.Header -> (holder as HeaderHolder).text.text = row.title
            is PickerRow.Item -> with((holder as RowHolder).binding) {
                title.text = row.title
                subtitle.text = row.subtitle
                subtitle.isVisible = !row.subtitle.isNullOrBlank()
                addIcon.visibility = View.GONE
                check.isVisible = selectedTitle != null && row.title == selectedTitle
                title.setTextColor(title.themeColor(com.google.android.material.R.attr.colorOnSurface))
            }
            is PickerRow.Add -> with((holder as RowHolder).binding) {
                title.text = "$addLabel “${row.text}”"
                subtitle.text = "Not in the list yet"
                subtitle.isVisible = true
                addIcon.visibility = View.VISIBLE
                check.isVisible = false
                title.setTextColor(title.themeColor(androidx.appcompat.R.attr.colorPrimary))
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
