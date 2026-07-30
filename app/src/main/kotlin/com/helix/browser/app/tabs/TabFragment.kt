package com.helix.browser.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment

class TabFragment : Fragment() {

    lateinit var webView: WebView
    var url: String = ""

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST = 100

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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

        // ---- Force page to fit screen width ----
        webView.settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS)
        webView.settings.setSupportZoom(true)
        webView.settings.setBuiltInZoomControls(true)
        webView.settings.setDisplayZoomControls(false) // remove zoom buttons

        // ---- Hardware acceleration ----
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // ---- Fix touch interference with ViewPager2 ----
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    webView.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    webView.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // ---- Scrollbars and overscroll ----
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true
        webView.overScrollMode = WebView.OVER_SCROLL_ALWAYS

        // ---- Desktop User Agent ----
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.scrollTo(0, 0)

                // ---- Force viewport to device width ----
                view?.evaluateJavascript(
                    "var meta = document.createElement('meta');" +
                    "meta.name = 'viewport';" +
                    "meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0';" +
                    "document.head.appendChild(meta);",
                    null
                )

                url?.let { (activity as? DefaultActivity)?.saveHistory(it, view?.title ?: it) }
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, view?.title ?: url ?: "")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val errorHtml = """
                    <html><body style='text-align:center;padding:40px;font-family:sans-serif;'>
                    <h2>🌐 Oops!</h2>
                    <p>Could not load the page.<br>${error?.description}</p>
                    <p style='color:#888;'>Check your URL or internet connection.</p>
                    </body></html>
                """
                view?.loadData(errorHtml, "text/html", "UTF-8")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, title ?: "")
            }
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

        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                val fileName = if (contentDisposition.contains("filename=")) {
                    contentDisposition.substringAfter("filename=").trim('"')
                } else {
                    Uri.parse(downloadUrl).lastPathSegment ?: "download"
                }
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType(mimetype)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(downloadUrl) ?: "")
                addRequestHeader("User-Agent", userAgent)
            }
            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        if (url.isNotEmpty()) webView.loadUrl(url) else webView.loadUrl("https://www.google.com")
    }

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

    fun loadUrl(url: String) { this.url = url; webView.loadUrl(url) }
    fun goBack(): Boolean { if (webView.canGoBack()) { webView.goBack(); return true }; return false }
    fun canGoBack() = webView.canGoBack()
    fun reload() = webView.reload()
}
