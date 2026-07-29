package com.helix.browser.app

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.inputmethod.EditorInfo

class DefaultActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root vertical layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        // Toolbar with URL input
        val toolbar = Toolbar(this).apply {
            id = R.id.toolbar
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(androidx.appcompat.R.attr.actionBarSize)
            )
            setBackgroundColor(resources.getColor(androidx.appcompat.R.color.material_grey_700, theme))
        }
        urlInput = EditText(this).apply {
            hint = "Enter URL or search..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#B3FFFFFF"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        toolbar.addView(urlInput)
        root.addView(toolbar)

        // SwipeRefreshLayout + WebView
        swipeRefresh = SwipeRefreshLayout(this).apply {
            id = R.id.swipeRefresh
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setColorSchemeResources(android.R.color.holo_blue_bright)
        }

        webView = WebView(this).apply {
            id = R.id.webView
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    urlInput.setText(url)
                    supportActionBar?.title = view?.title
                    swipeRefresh.isRefreshing = false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    supportActionBar?.title = title
                }
            }
            loadUrl("https://www.google.com")
        }
        swipeRefresh.addView(webView)
        root.addView(swipeRefresh)

        setContentView(root)

        // Events
        swipeRefresh.setOnRefreshListener { webView.reload() }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl(urlInput.text.toString())
                true
            } else false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Refresh")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 1, "Bookmarks")
        menu?.add(0, 3, 2, "History")
        menu?.add(0, 4, 3, "Plugin Store")
        menu?.add(0, 5, 4, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> webView.reload()
            2 -> startActivity(android.content.Intent(this, BookmarksActivity::class.java))
            3 -> startActivity(android.content.Intent(this, HistoryActivity::class.java))
            4 -> startActivity(android.content.Intent(this, PluginStoreActivity::class.java))
            5 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun loadUrl(input: String) {
        if (input.isBlank()) return
        val url = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        webView.loadUrl(url)
    }
}
