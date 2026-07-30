package com.helix.browser.app

import android.graphics.Color
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
    private lateinit var urlInput: EditText
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

        // ----- Toolbar with URL input (black background) -----
        val toolbar = Toolbar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.BLACK)
        }

        urlInput = EditText(this).apply {
            hint = "Enter URL or search..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.WHITE)   // <-- changed to pure white
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        toolbar.addView(urlInput)
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

        // ---- Fix: refresh only when scrolled to top ----
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val tab = getCurrentTab()
            val webView = tab?.webView
            webView?.canScrollVertically(-1) == true
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
        addNewTab() // Desktop UA is set inside TabFragment

        // ----- Swipe refresh -----
        swipeRefresh.setOnRefreshListener {
            getCurrentTab()?.reload()
            swipeRefresh.isRefreshing = false
        }

        // ----- URL enter key -----
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrlInCurrentTab(urlInput.text.toString())
                true
            } else false
        }

        // ----- Plugin manager -----
        pluginManager = PluginManager(this)
    }

    // ---------- Menu ----------
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Refresh")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 1, "Bookmarks")
        menu?.add(0, 3, 2, "History")
        menu?.add(0, 4, 3, "Plugin Store")
        menu?.add(0, 5, 4, "Settings")
        menu?.add(0, 6, 5, "Tabs")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> getCurrentTab()?.reload()
            2 -> startActivity(android.content.Intent(this, BookmarksActivity::class.java))
            3 -> startActivity(android.content.Intent(this, HistoryActivity::class.java))
            4 -> startActivity(android.content.Intent(this, PluginStoreActivity::class.java))
            5 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
            6 -> showTabSwitcher()
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
    fun addNewTab(url: String = "https://www.google.com") {
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
            urlInput.setText(tab.webView.url ?: "")
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
        val url = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
        getCurrentTab()?.loadUrl(url)
    }
}
