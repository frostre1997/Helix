package com.helix.browser.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class TabFragment : Fragment() {

    lateinit var webView: WebView
    var url: String = ""

    // For file chooser
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        webView = WebView(requireContext())
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return webView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        // ---------- WebViewClient ----------
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Save history
                url?.let {
                    (activity as? DefaultActivity)?.saveHistory(it, view?.title ?: it)
                }
                // Update tab title
                (activity as? DefaultActivity)?.updateTabTitle(
                    this@TabFragment,
                    view?.title ?: url ?: ""
                )
            }

            // Handle external links (tel:, mailto:, etc.)
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        // No app found – ignore
                    }
                    return true
                }
                return false
            }
        }

        // ---------- WebChromeClient ----------
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, title ?: "")
            }

            // File upload support
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@TabFragment.filePathCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                return true
            }
        }

        // ---------- Download Listener ----------
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
            downloadFile(downloadUrl, userAgent, contentDisposition, mimetype)
        }

        // Load initial URL
        if (url.isNotEmpty()) {
            webView.loadUrl(url)
        } else {
            webView.loadUrl("https://www.google.com")
        }
    }

    // ---------- Download helper ----------
    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            val fileName = if (contentDisposition.contains("filename=")) {
                contentDisposition.substringAfter("filename=").trim('"')
            } else {
                Uri.parse(url).lastPathSegment ?: "download"
            }
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType(mimetype)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            addRequestHeader("User-Agent", userAgent)
        }
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }

    // ---------- Handle file chooser result ----------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uris = if (data.data != null) arrayOf(data.data!!) else null
                filePathCallback?.onReceiveValue(uris)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }
    }

    // ---------- Public methods ----------
    fun loadUrl(url: String) {
        this.url = url
        webView.loadUrl(url)
    }

    fun goBack(): Boolean {
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun canGoBack() = webView.canGoBack()
    fun reload() = webView.reload()
}
