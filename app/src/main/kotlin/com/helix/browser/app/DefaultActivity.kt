// Add these imports
import androidx.lifecycle.lifecycleScope
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.Bookmark
import com.helix.browser.app.data.History
import kotlinx.coroutines.launch

// In onCreateOptionsMenu, add new items:
override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    // Update badge
    val tabItem = menu?.findItem(R.id.action_tabs)
    tabItem?.title = "Tabs (${getTabCount()})"
    // Update bookmark icon state
    val bookmarkItem = menu?.findItem(R.id.action_bookmark)
    val currentUrl = getCurrentTab()?.url ?: ""
    lifecycleScope.launch {
        val bookmark = AppDatabase.getInstance(this@DefaultActivity).bookmarkDao().getBookmarkByUrl(currentUrl)
        bookmarkItem?.setIcon(if (bookmark != null) 
            android.R.drawable.btn_star_big_on 
            else android.R.drawable.btn_star_big_off)
    }
    return true
}

// Handle bookmark click
R.id.action_bookmark -> {
    toggleBookmark()
    return true
}
R.id.action_bookmarks -> {
    startActivityForResult(Intent(this, BookmarksActivity::class.java), REQUEST_BOOKMARKS)
    return true
}
R.id.action_history -> {
    startActivityForResult(Intent(this, HistoryActivity::class.java), REQUEST_HISTORY)
    return true
}

// Add toggle function
private fun toggleBookmark() {
    val tab = getCurrentTab() ?: return
    val url = tab.webView.url ?: return
    val title = supportActionBar?.title?.toString() ?: url
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

// Save history in onPageFinished
webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        urlInput.setText(url)
        supportActionBar?.title = view?.title
        swipeRefresh.isRefreshing = false
        // Save history
        url?.let { nonNullUrl ->
            val title = view?.title ?: nonNullUrl
            lifecycleScope.launch {
                AppDatabase.getInstance(this@DefaultActivity).historyDao()
                    .insert(History(url = nonNullUrl, title = title))
            }
        }
    }
}

// Handle activity result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == RESULT_OK) {
        val url = data?.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            getCurrentTab()?.loadUrl(url)
        }
    }
}

// Add constants
companion object {
    private const val REQUEST_BOOKMARKS = 1001
    private const val REQUEST_HISTORY = 1002
}
