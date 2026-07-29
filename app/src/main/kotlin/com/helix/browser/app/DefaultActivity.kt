package com.helix.browser.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DefaultActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: TabAdapter
    private val tabTitles = mutableMapOf<TabFragment, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_tabs)  // new layout with ViewPager2

        viewPager = findViewById(R.id.viewPager)
        adapter = TabAdapter(this)
        viewPager.adapter = adapter

        // Add initial tab
        addNewTab()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        // Update tab count badge
        val tabItem = menu?.findItem(R.id.action_tabs)
        val tabCount = adapter.getTabCount()
        tabItem?.title = if (tabCount > 0) "Tabs ($tabCount)" else "Tabs"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_tabs -> {
                showTabSwitcher()
                return true
            }
            R.id.action_refresh -> {
                getCurrentTab()?.reload()
                return true
            }
            R.id.action_back -> {
                val tab = getCurrentTab()
                if (tab?.canGoBack() == true) {
                    tab.goBack()
                }
                return true
            }
            R.id.action_forward -> {
                // We skipped forward for simplicity, but you can add later
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        val tab = getCurrentTab()
        if (tab != null && tab.canGoBack()) {
            tab.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Tab Management ----------
    fun addNewTab(url: String = "https://www.google.com") {
        val fragment = TabFragment().apply { this.url = url }
        adapter.addTab(fragment)
        viewPager.setCurrentItem(adapter.getTabCount() - 1, true)
        // Update the tab title (will be set via WebChromeClient)
        // We'll also update the menu badge after adding
        invalidateOptionsMenu()
    }

    fun closeTab(position: Int) {
        if (adapter.getTabCount() <= 1) {
            // Don't close the last tab; instead, load a blank page
            val tab = getCurrentTab()
            tab?.loadUrl("about:blank")
            return
        }
        adapter.removeTab(position)
        // If the current position was removed, adjust viewPager
        if (position >= adapter.getTabCount()) {
            viewPager.setCurrentItem(adapter.getTabCount() - 1, false)
        }
        // Clean up title map
        tabTitles.keys.removeIf { it !in adapter.fragments } // we don't expose fragments, we'll handle differently
        invalidateOptionsMenu()
    }

    fun switchToTab(position: Int) {
        if (position in 0 until adapter.getTabCount()) {
            viewPager.setCurrentItem(position, true)
        }
    }

    fun getCurrentTab(): TabFragment? {
        val pos = viewPager.currentItem
        return if (pos < adapter.getTabCount()) adapter.getTab(pos) else null
    }

    fun getTabs(): List<TabFragment> {
        // We need to get all fragments from adapter; but we can store list in adapter
        return (0 until adapter.getTabCount()).map { adapter.getTab(it) }
    }

    fun updateTabTitle(tab: TabFragment, title: String) {
        tabTitles[tab] = title
        // If this is the current tab, update action bar
        if (getCurrentTab() == tab) {
            supportActionBar?.title = title
        }
        // Update menu badge
        invalidateOptionsMenu()
    }

    fun getTabTitle(tab: TabFragment): String {
        return tabTitles[tab] ?: "Tab"
    }

    private fun showTabSwitcher() {
        val bottomSheet = TabSwitcherBottomSheet()
        bottomSheet.show(supportFragmentManager, "TabSwitcher")
    }

    // Helper to get number of tabs for badge
    fun getTabCount() = adapter.getTabCount()
}
