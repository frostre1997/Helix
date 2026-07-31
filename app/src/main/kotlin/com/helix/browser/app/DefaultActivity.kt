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
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.Bookmark
import com.helix.browser.app.data.History
import kotlinx.coroutines.launch

class DefaultActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: TabAdapter
    private lateinit var domainText: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var starButton: ImageButton
    private val tabTitles = mutableMapOf<TabFragment, String>()
    lateinit var pluginManager: PluginManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        // ----- Toolbar (40dp height) -----
        val toolbar = Toolbar(this).apply {
            val height = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            setBackgroundColor(Color.BLACK)
        }

        val toolbarContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 0)
        }

        // ----- Helper to create perfectly centered 24dp icons -----
        fun createIconButton(drawableRes: Int, onClick: () -> Unit): ImageButton {
            val dp = resources.displayMetrics.density
            return ImageButton(this).apply {
                setImageResource(drawableRes)
                setBackgroundColor(Color.TRANSPARENT)
                val size = (24 * dp).toInt()
                val params = LinearLayout.LayoutParams(size, size)
                params.setMargins(0, 0, (4 * dp).toInt(), 0) // 4dp spacing
                layoutParams = params
                scaleType = ImageView.ScaleType.CENTER   // ← ensures perfect centering
                setOnClickListener { onClick() }
            }
        }

        // ----- LEFT GROUP: Home, Back, Forward, Refresh -----
        val leftGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        leftGroup.addView(createIconButton(R.drawable.ic_home) {
            loadUrlInCurrentTab("https://www.google.com")
        })
        leftGroup.addView(createIconButton(R.drawable.ic_back) {
            getCurrentTab()?.goBack()
        })
        leftGroup.addView(createIconButton(R.drawable.ic_forward) {
            getCurrentTab()?.goForward()
        })
        leftGroup.addView(createIconButton(R.drawable.ic_refresh) {
            getCurrentTab()?.reload()
        })

        toolbarContent.addView(leftGroup)

        // ----- CENTER: Domain (search bar) -----
        domainText = TextView(this).apply {
            text = "Helix"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener { showSearchDialog() }
        }
        toolbarContent.addView(domainText)

        // ----- RIGHT GROUP: Extensions, Star, Download, Menu -----
        val rightGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        rightGroup.addView(createIconButton(R.drawable.ic_extension) {
            Toast.makeText(this@DefaultActivity, "Extensions coming soon", Toast.LENGTH_SHORT).show()
        })

        starButton = createIconButton(R.drawable.ic_star) {
            toggleBookmark()
        }
        rightGroup.addView(starButton)

        rightGroup.addView(createIconButton(R.drawable.ic_download) {
            Toast.makeText(this@DefaultActivity, "Download manager", Toast.LENGTH_SHORT).show()
        })

        rightGroup.addView(createIconButton(R.drawable.ic_menu) {
            openOptionsMenu()
        })

        // Remove margin from the last button (menu) so it aligns with the edge
        (rightGroup.getChildAt(rightGroup.childCount - 1) as ImageButton).apply {
            val params = layoutParams as LinearLayout.LayoutParams
            params.setMargins(0, 0, 0, 0)
            layoutParams = params
        }

        toolbarContent.addView(rightGroup)

        toolbar.addView(toolbarContent)
        root.addView(toolbar)

        // ----- SwipeRefresh + ViewPager -----
        swipeRefresh = SwipeRefreshLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setColorSchemeResources(android.R.color.holo_blue_bright)
        }
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            getCurrentTab()?.canScrollUp() == true
        }

        viewPager = ViewPager2(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
        }
        swipeRefresh.addView(viewPager)
        root.addView(swipeRefresh)

        setContentView(root)

        // Fix bottom cut-off (system navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            swipeRefresh.setPadding(0, 0, 0, bottomInset)
            insets
        }

        adapter = TabAdapter(this)
        viewPager.adapter = adapter
        addNewTab("https://shields.io")

        swipeRefresh.setOnRefreshListener {
            getCurrentTab()?.reload()
            swipeRefresh.isRefreshing = false
        }

        pluginManager = PluginManager(this)
        updateStarIcon(null) // initial state
    }

    // ----- Bookmark toggle -----
    private fun toggleBookmark() {
        val tab = getCurrentTab() ?: return
        val url = tab.webView.url ?: return
        val title = tab.webView.title ?: url
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@DefaultActivity)
            val existing = db.bookmarkDao().getBookmarkByUrl(url)
            if (existing != null) {
                db.bookmarkDao().delete(existing)
                starButton.setColorFilter(Color.GRAY)
                Toast.makeText(this@DefaultActivity, "Bookmark removed", Toast.LENGTH_SHORT).show()
            } else {
                db.bookmarkDao().insert(Bookmark(url = url, title = title))
                starButton.setColorFilter(Color.YELLOW)
                Toast.makeText(this@DefaultActivity, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ----- Floating search dialog -----
    private fun showSearchDialog() {
        val currentTab = getCurrentTab()
        val currentUrl = currentTab?.webView?.url ?: ""
        val input = EditText(this).apply {
            setText(currentUrl)
            setSelection(text.length)
            setHint("Search or enter URL")
            setHintTextColor(Color.GRAY)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Search on Helix")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val url = input.text.toString()
                if (url.isNotEmpty()) loadUrlInCurrentTab(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ----- Update domain -----
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
        updateStarIcon(url)
    }

    private fun updateStarIcon(url: String?) {
        if (url.isNullOrEmpty()) {
            starButton.setColorFilter(Color.GRAY)
            return
        }
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@DefaultActivity)
            val bookmark = db.bookmarkDao().getBookmarkByUrl(url)
            starButton.setColorFilter(if (bookmark != null) Color.YELLOW else Color.GRAY)
        }
    }

    // ----- Menu (3-dot) -----
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Refresh")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 2, 1, "Back")
        menu?.add(0, 3, 2, "Forward")
        menu?.add(0, 4, 3, "Home")
        menu?.add(0, 5, 4, "Bookmarks")
        menu?.add(0, 6, 5, "History")
        menu?.add(0, 7, 6, "Plugin Store")
        menu?.add(0, 8, 7, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> getCurrentTab()?.reload()
            2 -> getCurrentTab()?.goBack()
            3 -> getCurrentTab()?.goForward()
            4 -> loadUrlInCurrentTab("https://www.google.com")
            5 -> startActivity(android.content.Intent(this, BookmarksActivity::class.java))
            6 -> startActivity(android.content.Intent(this, HistoryActivity::class.java))
            7 -> startActivity(android.content.Intent(this, PluginStoreActivity::class.java))
            8 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onBackPressed() {
        if (getCurrentTab()?.canGoBack() == true) {
            getCurrentTab()?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ----- Tab management -----
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
            updateDomain(tab.webView.url)
        }
        invalidateOptionsMenu()
    }

    fun saveHistory(url: String, title: String) {
        lifecycleScope.launch {
            AppDatabase.getInstance(this@DefaultActivity).historyDao()
                .insert(History(url = url, title = title))
        }
    }

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
