package com.helix.browser.app

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.History
import kotlinx.coroutines.launch

class DefaultActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: TabAdapter
    private lateinit var domainText: TextView      // <-- shows just the domain
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val tabTitles = mutableMapOf<TabFragment, String>()
    lateinit var pluginManager: PluginManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ----- Root layout -----
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        // ----- Custom Toolbar -----
        val toolbar = Toolbar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // ---- Toolbar content (horizontal layout) ----
        val toolbarContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 0)
        }

        // ---- Back button ----
        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                getCurrentTab()?.goBack()
            }
        }
        toolbarContent.addView(backBtn)

        // ---- Forward button ----
        val forwardBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // We'll add forward later (WebView doesn't have goForward() in TabFragment yet)
                Toast.makeText(this@DefaultActivity, "Forward not implemented", Toast.LENGTH_SHORT).show()
            }
        }
        toolbarContent.addView(forwardBtn)

        // ---- Domain TextView (centered) ----
        domainText = TextView(this).apply {
            text = "Helix"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f  // weight 1 -> takes remaining space, centers text
            )
            setOnClickListener {
                showUrlEditor()
            }
        }
        toolbarContent.addView(domainText)

        // ---- Refresh button ----
        val refreshBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_rotate)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                getCurrentTab()?.reload()
            }
        }
        toolbarContent.addView(refreshBtn)

        // ---- Tabs button ----
        val tabsBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda) // or any icon
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                showTabSwitcher()
            }
        }
        toolbarContent.addView(tabsBtn)

        toolbar.addView(toolbarContent)
        root.addView(toolbar)

        // ----- SwipeRefreshLayout + ViewPager -----
        swipeRefresh = SwipeRefreshLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setColorSchemeResources(android.R.color.holo_blue_bright)
        }

        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val tab = getCurrentTab()
            val webView = tab?.webView
            webView?.scrollY == 0
        }

        viewPager = ViewPager2(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        swipeRefresh.addView(viewPager)
        root.addView(swipeRefresh)

        setContentView(root)

        // ----- Init adapter and first tab -----
        adapter = TabAdapter(this)
        viewPager.adapter = adapter
        addNewTab("https://google.com")

        swipeRefresh.setOnRefreshListener {
            getCurrentTab()?.reload()
            swipeRefresh.isRefreshing = false
        }

        pluginManager = PluginManager(this)
    }

    // ---------- Show URL editor dialog ----------
    private fun showUrlEditor() {
        val currentTab = getCurrentTab()
        val currentUrl = currentTab?.webView?.url ?: ""
        val input = EditText(this).apply {
            setText(currentUrl)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Search on Helix")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val url = input.text.toString()
                if (url.isNotEmpty()) {
                    loadUrlInCurrentTab(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Update domain text when page changes ----------
    fun updateDomain(url: String?) {
        if (url.isNullOrEmpty()) {
            domainText.text = "Helix"
            return
        }
        val domain = try {
            val host = Uri.parse(url).host ?: url
            host.removePrefix("www.")
        } catch (_: Exception) {
            url
        }
        domainText.text = domain
    }

    // ---------- Menu ----------
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Refresh")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 2, 1, "Bookmarks")
        menu?.add(0, 3, 2, "History")
        menu?.add(0, 4, 3, "Plugin Store")
        menu?.add(0, 5, 4, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> getCurrentTab()?.reload()
            2 -> startActivity(android.content.Intent(this, BookmarksActivity::class.java))
            3 -> startActivity(android.content.Intent(this, HistoryActivity::class.java))
            4 -> startActivity(android.content.Intent(this, PluginStoreActivity::class.java))
            5 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    // ---------- Back button ----------
    override fun onBackPressed() {
        val tab = getCurrentTab()
        if (tab != null && tab.canGoBack()) {
            tab.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Tab management ----------
    fun addNewTab(url: String = "https://google.com") {
        val fragment = TabFragment().apply { this.url = url }
        adapter.addTab(fragment)
        viewPager.setCurrentItem(adapter.getTabCount() - 1, true)
        invalidateOptionsMenu()
    }

    fun closeTab(position: Int) {
        if (adapter.getTabCount() <= 1) {
            getCurrentTab()?.loadUrl("about:blank")
            return
        }
        adapter.removeTab(position)
        if (position >= adapter.getTabCount()) {
            viewPager.setCurrentItem(adapter.getTabCount() - 1, false)
        }
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
        return (0 until adapter.getTabCount()).map { adapter.getTab(it) }
    }

    fun getTabTitle(tab: TabFragment): String {
        return tabTitles[tab] ?: "Tab"
    }

    fun updateTabTitle(tab: TabFragment, title: String) {
        tabTitles[tab] = title
        if (getCurrentTab() == tab) {
            supportActionBar?.title = title
            // Update the domain text with the actual URL
            val url = tab.webView.url
            updateDomain(url)
        }
        invalidateOptionsMenu()
    }

    fun saveHistory(url: String, title: String) {
        lifecycleScope.launch {
            AppDatabase.getInstance(this@DefaultActivity).historyDao()
                .insert(History(url = url, title = title))
        }
    }

    // ---------- Plugin injection ----------
    fun injectPlugins(webView: WebView, url: String) {
        pluginManager.getPluginsForUrl(url).forEach { plugin ->
            pluginManager.injectPlugin(webView, plugin)
        }
    }

    private fun showTabSwitcher() {
        val bottomSheet = TabSwitcherBottomSheet()
        bottomSheet.show(supportFragmentManager, "TabSwitcher")
    }

    private fun loadUrlInCurrentTab(input: String) {
        if (input.isBlank()) return
        val trimmed = input.trim()
        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${trimmed.replace(' ', '+')}"
        }
        getCurrentTab()?.loadUrl(url)
    }
}
