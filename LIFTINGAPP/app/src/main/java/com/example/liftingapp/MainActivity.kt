package com.example.liftingapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.databinding.ActivityMainBinding
import com.example.liftingapp.ui.HistoryTab
import com.example.liftingapp.ui.LeaderboardTab
import com.example.liftingapp.ui.LogTab
import com.google.android.material.tabs.TabLayout

/**
 * Views + ViewBinding, like project4. The Log tab is Garrett's spinner/chart/FAB screen;
 * History and Leaderboard are separate tabs. Each tab's wiring lives in ui/ to keep this file short.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LiftViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LiftViewModel::class.java]

        LogTab(this, binding.logTab, viewModel)
        HistoryTab(this, binding.historyTab, viewModel)
        LeaderboardTab(this, binding.leaderboardTab, viewModel)

        setupTabs(savedInstanceState?.getInt(KEY_TAB) ?: 0)
    }

    private fun setupTabs(initialTab: Int) {
        val pages: List<View> = listOf(binding.logTab.root, binding.historyTab.root, binding.leaderboardTab.root)
        listOf("Log", "History", "Leaderboard").forEach { binding.tabs.addTab(binding.tabs.newTab().setText(it)) }

        fun show(index: Int) = pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.tabs.getTabAt(initialTab)?.select()
        show(initialTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, binding.tabs.selectedTabPosition)
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
    }
}
