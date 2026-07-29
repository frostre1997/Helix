package com.helix.browser.app

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class DefaultActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.default_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        urlInput = findViewById(R.id.urlInput)
        val goButton: Button = findViewById(R.id.goButton)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val forwardButton: ImageButton = findViewById(R.id.forwardButton)
        val refreshButton: ImageButton = findViewById(R.id.refreshButton)

        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlInput.setText(url)
                supportActionBar?.title = view?.title
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                supportActionBar?.title = title
            }
        }

        webView.loadUrl("https://www.google.com")

        goButton.setOnClickListener {
            loadUrl(urlInput.text.toString())
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl(urlInput.text.toString())
                true
            } else false
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        forwardButton.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }

        refreshButton.setOnClickListener {
            webView.reload()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadUrl(input: String) {
        if (input.isBlank()) return
        val url = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
        webView.loadUrl(url)
    }
}
