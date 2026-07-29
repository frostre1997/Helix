package com.helix.browser.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.Bookmark
import com.helix.browser.app.data.History
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class DefaultActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: TabAdapter
    private lateinit var urlInput: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val tabTitles = mutableMapOf<TabFragment, String>()

    companion object {
        private const val REQUEST_BOOKMARKS = 1001
        private const val REQUEST_HISTORY = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_tabs)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewPager = findViewById(R.id.viewPager)
        urlInput = findViewById(R.id.urlInput)  // keep if you have it
        swipeRefresh = findViewById(R.id.swipeRefresh)

        adapter = TabAdapter(this)
        viewPager.adapter = adapter

        // Add initial tab
        addNewTab()

        // Swipe to refresh – refreshes current tab
        swipeRefresh.setOnRefreshListener {
            getCurrentTab()?.reload()
            swipeRefresh.isRefreshing = false
        }

        // URL input – load in current tab
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrlInCurrentTab(urlInput.text.toString())
                true
            } else false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        // Tab count badge
        val tabItem = menu?.findItem(R.id.action_tabs)
        tabItem?.title = "Tabs (${adapter.getTabCount()})"

        // Bookmark icon state
        val bookmarkItem = menu?.findItem(R.id.action_bookmark)
        val currentUrl = getCurrentTab()?.webView?.url ?: ""
        lifecycleScope.launch {
            val bookmark = AppDatabase.getInstance(this@DefaultActivity)
                .bookmarkDao().getBookmarkByUrl(currentUrl)
            bookmarkItem?.setIcon(
                if (bookmark != null) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
        }
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
                if (tab?.canGoBack() == true) tab.goBack()
                return true
            }
            R.id.action_forward -> {
                // Implement forward if needed – we can add a forward method in TabFragment
                return true
            }
            R.id.action_bookmark -> {
                toggleBookmark()
                return true
            }
            R.id.action_bookmarks -> {
                startActivityForResult(
                    Intent(this, BookmarksActivity::class.java),
                    REQUEST_BOOKMARKS
                )
                return true
            }
            R.id.action_history -> {
                startActivityForResult(
                    Intent(this, HistoryActivity::class.java),
                    REQUEST_HISTORY
                )
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val url = data.getStringExtra("url")
            if (!url.isNullOrEmpty()) {
                getCurrentTab()?.loadUrl(url)
            }
        }
    }

    // ---------- Tab Management ----------
    fun addNewTab(url: String = "https://www.google.com") {
        val fragment = TabFragment().apply { this.url = url }
        adapter.addTab(fragment)
        viewPager.setCurrentItem(adapter.getTabCount() - 1, true)
        invalidateOptionsMenu()
    }

    fun closeTab(position: Int) {
        if (adapter.getTabCount() <= 1) {
            // Don't close the last tab; load blank
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

    fun getTabCount() = adapter.getTabCount()

    fun updateTabTitle(tab: TabFragment, title: String) {
        tabTitles[tab] = title
        if (getCurrentTab() == tab) {
            supportActionBar?.title = title
            // Also update URL bar? We can update with current URL from webView
            urlInput.setText(tab.webView.url ?: "")
        }
        invalidateOptionsMenu()
    }

    fun getTabTitle(tab: TabFragment): String {
        return tabTitles[tab] ?: "Tab"
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

    // ---------- Bookmark Toggle ----------
    private fun toggleBookmark() {
        val tab = getCurrentTab() ?: return
        val url = tab.webView.url ?: return
        val title = tab.webView.title ?: url
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@DefaultActivity)
            val existing = db.bookmarkDao().getBookmarkByUrl(url)
            if (existing != null) {
                db.bookmarkDao().delete(existing)
                Toast.makeText(this@DefaultActivity, "Bookmark removed", Toast.LENGTH_SHORT).show()
            } else {
                db.bookmarkDao().insert(Bookmark(url = url, title = title))
                Toast.makeText(this@DefaultActivity, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
            invalidateOptionsMenu()
        }
    }

    // ---------- Override to save history ----------
    // In TabFragment's WebViewClient we already call updateTabTitle, but we need to save history.
    // We'll modify TabFragment to add a callback when page finishes.
    // For simplicity, we can override onPageFinished in TabFragment's WebViewClient.
    // But we already have a method: in TabFragment we can add a listener.
    // Let's add a function in DefaultActivity to save history from TabFragment.
    fun saveHistory(url: String, title: String) {
        lifecycleScope.launch {
            AppDatabase.getInstance(this@DefaultActivity).historyDao()
                .insert(History(url = url, title = title))
        }
    }
}
