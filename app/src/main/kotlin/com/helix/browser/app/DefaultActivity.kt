package com.helix.browser.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.Bookmark
import com.helix.browser.app.data.History
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: TabAdapter
    private lateinit var domainText: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var starButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var floatingSearchBar: FloatingSearchBar
    private lateinit var menuButton: ImageButton
    private val tabTitles = mutableMapOf<TabFragment, String>()
    lateinit var pluginManager: PluginManager

    private var immersiveActive = false
    private var toolbarHidden = false

    companion object {
        private const val REQ_BOOKMARKS = 1
        private const val REQ_HISTORY = 2
        private const val REQ_SETTINGS = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ----- Root layout -----
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        // ----- Main content (vertical) -----
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ----- Toolbar -----
        val toolbar = Toolbar(this).apply {
            val height = dpToPx(40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            setBackgroundColor(Color.BLACK)
        }

        // ---- Toolbar content (FrameLayout for perfect centering) ----
        val toolbarContent = FrameLayout(this).apply {
            layoutParams = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16), 0, dpToPx(16), 0)   // edge spacing
        }

        // Helper to create icon buttons (with 8dp spacing between them)
        fun createIconButton(drawableRes: Int, contentDesc: String, onClick: () -> Unit): ImageButton {
            val size = dpToPx(24)
            return ImageButton(this).apply {
                setImageResource(drawableRes)
                // use selectable item background borderless (ripple) from theme:
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                val params = LinearLayout.LayoutParams(size, size)
                params.setMargins(0, 0, dpToPx(16), 0)
                layoutParams = params
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener { onClick() }
                contentDescription = contentDesc
                // default tint
                imageTintList = ColorStateList.valueOf(Color.GRAY)
            }
        }

        // ----- LEFT: Home, Back, Forward, Refresh -----
        val leftGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        }
        leftGroup.addView(createIconButton(R.drawable.ic_home, "Home") {
            loadUrlInCurrentTab(Prefs.homeUrl(this@DefaultActivity))
        })
        leftGroup.addView(createIconButton(R.drawable.ic_back, "Back") {
            getCurrentTab()?.goBack()
        })
        leftGroup.addView(createIconButton(R.drawable.ic_forward, "Forward") {
            getCurrentTab()?.goForward()
        })
        refreshButton = createIconButton(R.drawable.ic_refresh, "Refresh") {
            val tab = getCurrentTab()
            if (tab != null) {
                if (tab.isPageLoading()) tab.stopLoading() else tab.reload()
            }
        }
        leftGroup.addView(refreshButton)
        toolbarContent.addView(leftGroup)

        // ----- CENTER: Domain (perfectly centered) -----
        domainText = TextView(this).apply {
            text = "Helix"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            contentDescription = "Current domain"
            setOnClickListener {
                val currentUrl = getCurrentTab()?.webView?.url ?: ""
                floatingSearchBar.show(currentUrl)
            }
        }
        toolbarContent.addView(domainText)

        // ----- RIGHT: Extensions, Star, Download, Menu -----
        val rightGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        }
        rightGroup.addView(createIconButton(R.drawable.ic_extension, "Extensions") {
            startActivity(Intent(this@DefaultActivity, ExtensionsActivity::class.java))
        })
        starButton = createIconButton(R.drawable.ic_star, "Bookmark") {
            toggleBookmark()
        }
        rightGroup.addView(starButton)
        rightGroup.addView(createIconButton(R.drawable.ic_download, "Downloads") {
            startActivity(Intent(this@DefaultActivity, DownloadsActivity::class.java))
        })
        menuButton = createIconButton(R.drawable.ic_menu, "Menu") {
            showMainMenu()
        }
        rightGroup.addView(menuButton)
        // Remove margin from the last icon (menu) if present
        if (rightGroup.childCount > 0) {
            (rightGroup.getChildAt(rightGroup.childCount - 1) as? ImageButton)?.let { btn ->
                (btn.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, 0, 0)
            }
        }
        toolbarContent.addView(rightGroup)

        toolbar.addView(toolbarContent)
        content.addView(toolbar)

        // Set toolbar as support ActionBar so title updates work
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

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
        content.addView(swipeRefresh)

        root.addView(content)

        // ----- Floating Search Bar -----
        floatingSearchBar = FloatingSearchBar(this) { query ->
            loadUrlInCurrentTab(query)
        }
        root.addView(floatingSearchBar.getView())

        setContentView(root)

        // Bottom insets
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            swipeRefresh.setPadding(0, 0, 0, bottomInset)
            insets
        }

        adapter = TabAdapter(this)
        viewPager.adapter = adapter
        addNewTab(Prefs.homeUrl(this))

        swipeRefresh.setOnRefreshListener {
            getCurrentTab()?.reload()
            swipeRefresh.isRefreshing = false
        }

        pluginManager = PluginManager(this)
        updateStarIcon(null)

        // Apply fullscreen-mode setting on startup
        if (Prefs.fullscreenMode(this)) {
            setSystemBarsImmersive(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh plugin list in case Extensions/Plugin Store changed anything
        pluginManager.loadPlugins()
    }

    // ----- helpers -----
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun setStarTint(colorInt: Int) {
        starButton.imageTintList = ColorStateList.valueOf(colorInt)
    }

    // ----- Bookmark toggle -----
    private fun toggleBookmark() {
        val tab = getCurrentTab() ?: return
        val url = tab.webView.url ?: return
        val title = tab.webView.title ?: url
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@DefaultActivity)
                val existing = withContext(Dispatchers.IO) { db.bookmarkDao().getBookmarkByUrl(url) }
                if (existing != null) {
                    withContext(Dispatchers.IO) { db.bookmarkDao().delete(existing) }
                    withContext(Dispatchers.Main) { setStarTint(Color.GRAY) }
                    Toast.makeText(this@DefaultActivity, "Bookmark removed", Toast.LENGTH_SHORT).show()
                } else {
                    withContext(Dispatchers.IO) { db.bookmarkDao().insert(Bookmark(url = url, title = title)) }
                    withContext(Dispatchers.Main) { setStarTint(Color.YELLOW) }
                    Toast.makeText(this@DefaultActivity, "Bookmark added", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DefaultActivity, "Bookmark error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
            setStarTint(Color.GRAY)
            return
        }
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@DefaultActivity)
                val bookmark = withContext(Dispatchers.IO) { db.bookmarkDao().getBookmarkByUrl(url) }
                withContext(Dispatchers.Main) {
                    setStarTint(if (bookmark != null) Color.YELLOW else Color.GRAY)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { setStarTint(Color.GRAY) }
            }
        }
    }

    // ----- Progress (refresh / stop toggle) -----
    fun onTabProgressChanged(tab: TabFragment, progress: Int) {
        if (getCurrentTab() !== tab) return
        val loading = progress in 1..99
        refreshButton.setImageResource(if (loading) R.drawable.ic_stop else R.drawable.ic_refresh)
        refreshButton.contentDescription = if (loading) "Stop" else "Refresh"
    }

    // ----- Main menu (PopupMenu anchored to the menu button) -----
    private fun showMainMenu() {
        val popup = PopupMenu(this, menuButton)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            performMenuAction(item.itemId)
            true
        }
        popup.setForceShowIcon(true)
        try {
            popup.show()
        } catch (_: Exception) {
            Toast.makeText(this, "Menu unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performMenuAction(itemId: Int) {
        when (itemId) {
            R.id.action_new_tab -> addNewTab()
            R.id.action_home -> loadUrlInCurrentTab(Prefs.homeUrl(this))
            R.id.action_refresh -> getCurrentTab()?.reload()
            R.id.action_share -> shareCurrentPage()
            R.id.action_bookmarks -> startActivityForResult(
                Intent(this, BookmarksActivity::class.java), REQ_BOOKMARKS
            )
            R.id.action_history -> startActivityForResult(
                Intent(this, HistoryActivity::class.java), REQ_HISTORY
            )
            R.id.action_downloads -> startActivity(Intent(this, DownloadsActivity::class.java))
            R.id.action_extensions -> startActivity(Intent(this, ExtensionsActivity::class.java))
            R.id.action_plugins -> startActivity(Intent(this, PluginStoreActivity::class.java))
            R.id.action_fullscreen -> toggleAppFullscreen()
            R.id.action_settings -> startActivityForResult(
                Intent(this, SettingsActivity::class.java), REQ_SETTINGS
            )
        }
    }

    private fun shareCurrentPage() {
        val tab = getCurrentTab() ?: return
        val url = tab.webView.url ?: return
        val title = tab.webView.title ?: url
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            }
            startActivity(Intent.createChooser(intent, "Share page"))
        } catch (_: Exception) {
            Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ----- Fullscreen handling -----
    private fun setSystemBarsImmersive(enabled: Boolean) {
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowInsetsControllerCompat(window, decor)
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = if (enabled) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    fun enterFullscreenMode() {
        setSystemBarsImmersive(true)
        supportActionBar?.hide()
        toolbarHidden = true
    }

    fun exitFullscreenMode() {
        if (!immersiveActive) {
            setSystemBarsImmersive(false)
            supportActionBar?.show()
        }
        toolbarHidden = false
    }

    private fun toggleAppFullscreen() {
        immersiveActive = !immersiveActive
        setSystemBarsImmersive(immersiveActive)
        Toast.makeText(this, if (immersiveActive) "Fullscreen on" else "Fullscreen off", Toast.LENGTH_SHORT).show()
    }

    // ----- Options menu (hardware menu / system) -----
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return false
    }

    override fun onBackPressed() {
        if (floatingSearchBar.isShowing()) {
            floatingSearchBar.hide()
            return
        }
        val currentTab = getCurrentTab()
        if (currentTab?.isFullscreen() == true) {
            currentTab.exitFullscreen()
            return
        }
        if (immersiveActive) {
            immersiveActive = false
            exitFullscreenMode()
            return
        }
        if (currentTab?.canGoBack() == true) {
            currentTab.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val url = data.getStringExtra("url")
            if (!url.isNullOrEmpty() && (requestCode == REQ_BOOKMARKS || requestCode == REQ_HISTORY)) {
                getCurrentTab()?.loadUrl(url)
            }
        }
        if (requestCode == REQ_SETTINGS) {
            getTabs().forEach { it.applySettings() }
        }
    }

    // ----- Tab management -----
    fun addNewTab(url: String = Prefs.homeUrl(this)) {
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
            try {
                val db = AppDatabase.getInstance(this@DefaultActivity)
                withContext(Dispatchers.IO) { db.historyDao().insert(History(url = url, title = title)) }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun injectPlugins(webView: WebView, url: String) {
        pluginManager.getPluginsForUrl(url).forEach { plugin ->
            pluginManager.injectPlugin(webView, plugin)
        }
    }

    private fun loadUrlInCurrentTab(input: String) {
        if (input.isBlank()) return
        val trimmed = input.trim()
        val url = if (Prefs.isUrl(trimmed)) {
            Prefs.toUrl(trimmed)
        } else {
            Prefs.searchUrl(this, trimmed)
        }
        getCurrentTab()?.loadUrl(url)
    }
}
