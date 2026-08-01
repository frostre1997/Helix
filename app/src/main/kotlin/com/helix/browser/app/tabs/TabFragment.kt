package com.helix.browser.app

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment

class TabFragment : Fragment() {

    lateinit var webView: WebView
    var url: String = ""

    private lateinit var root: FrameLayout

    // ---- Fullscreen video ----
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST = 100
    private val WRITE_STORAGE_REQUEST = 101

    private var pendingDownloadUrl: String? = null
    private var pendingDownloadUserAgent: String? = null
    private var pendingDownloadContentDisposition: String? = null
    private var pendingDownloadMimeType: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(requireContext())
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(webView)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---- Settings from prefs ----
        val context = requireContext()
        webView.settings.javaScriptEnabled = Prefs.isJavaScriptEnabled(context)
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.textZoom = Prefs.textZoom(context)

        // ---- Enable autoplay ----
        webView.settings.setMediaPlaybackRequiresUserGesture(false)

        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // ---- Touch interceptor ----
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    webView.parent?.requestDisallowInterceptTouchEvent(canScrollUp())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    webView.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // Focus and scrollbars
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true
        webView.overScrollMode = WebView.OVER_SCROLL_ALWAYS

        // ---- Fullscreen container (sibling of webView, not a child) ----
        fullscreenContainer = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        root.addView(fullscreenContainer)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(
                    "var meta = document.createElement('meta');" +
                    "meta.name = 'viewport';" +
                    "meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0';" +
                    "document.head.appendChild(meta);",
                    null
                )
                url?.let { (activity as? DefaultActivity)?.saveHistory(it, view?.title ?: it) }
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, view?.title ?: url ?: "")
                (activity as? DefaultActivity)?.updateDomain(url)

                // ---- Inject plugins ----
                val act = activity as? DefaultActivity
                if (act != null && view != null && url != null) {
                    act.injectPlugins(view, url)
                }
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
                if (request?.isForMainFrame == true) {
                    val errorHtml = """
                        <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                        <body style='text-align:center;padding:40px;font-family:sans-serif;'>
                        <h2>🌐 Oops!</h2>
                        <p>Could not load the page.<br>${error?.description}</p>
                        <p style='color:#888;'>Check your URL or internet connection.</p>
                        </body></html>
                    """
                    view?.loadDataWithBaseURL(request.url.toString(), errorHtml, "text/html", "UTF-8", null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, title ?: "")
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                (activity as? DefaultActivity)?.onTabProgressChanged(this@TabFragment, newProgress)
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (Prefs.blockPopups(requireContext())) {
                    return false
                }
                // Allow popups: open them as new Helix tabs
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popupWebView = WebView(requireContext()).apply {
                    settings.javaScriptEnabled = Prefs.isJavaScriptEnabled(requireContext())
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            request?.url?.toString()?.let { (activity as? DefaultActivity)?.addNewTab(it) }
                            return true
                        }

                        override fun onPageFinished(v: WebView?, url: String?) {
                            if (!url.isNullOrEmpty()) {
                                (activity as? DefaultActivity)?.addNewTab(url)
                            }
                            v?.destroy()
                        }
                    }
                }
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
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

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                view?.let {
                    fullscreenContainer?.addView(it)
                    fullscreenContainer?.visibility = View.VISIBLE
                }
                webView.visibility = View.GONE
                (activity as? DefaultActivity)?.enterFullscreenMode()
            }

            override fun onHideCustomView() {
                if (customView == null) return
                customView?.let {
                    fullscreenContainer?.removeView(it)
                }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                fullscreenContainer?.visibility = View.GONE
                (activity as? DefaultActivity)?.exitFullscreenMode()
            }
        }

        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            beginDownload(downloadUrl, userAgent, contentDisposition, mimetype)
        }

        if (url.isNotEmpty()) webView.loadUrl(url) else webView.loadUrl(Prefs.homeUrl(context))
    }

    // ----- Downloads -----
    private fun beginDownload(downloadUrl: String, userAgent: String, contentDisposition: String, mimetype: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimetype)
            return
        }
        val activity = activity
        if (activity != null &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadUrl = downloadUrl
            pendingDownloadUserAgent = userAgent
            pendingDownloadContentDisposition = contentDisposition
            pendingDownloadMimeType = mimetype
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_STORAGE_REQUEST
            )
        } else {
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimetype)
        }
    }

    private fun enqueueDownload(downloadUrl: String, userAgent: String, contentDisposition: String, mimetype: String) {
        try {
            val fileName = parseFileName(contentDisposition, downloadUrl)
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle(fileName)
                setDescription(downloadUrl)
                setMimeType(mimetype)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(downloadUrl) ?: "")
                addRequestHeader("User-Agent", userAgent)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(requireContext(), "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseFileName(contentDisposition: String?, url: String): String {
        contentDisposition?.let { cd ->
            val match = Regex("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""").find(cd)
            if (match != null) {
                val name = match.groupValues[1]
                if (name.isNotBlank() && !name.contains("/") && !name.contains("\\")) return name
            }
        }
        return Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "download_${System.currentTimeMillis()}"
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_STORAGE_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pendingDownloadUrl?.let { url ->
                    enqueueDownload(url, pendingDownloadUserAgent ?: "", pendingDownloadContentDisposition ?: "", pendingDownloadMimeType ?: "")
                }
            }
            pendingDownloadUrl = null
            pendingDownloadUserAgent = null
            pendingDownloadContentDisposition = null
            pendingDownloadMimeType = null
        }
    }

    fun isFullscreen(): Boolean = customView != null

    fun exitFullscreen() {
        if (isFullscreen()) {
            webView.webChromeClient?.onHideCustomView()
        }
    }

    fun loadUrl(url: String) {
        this.url = url
        webView.loadUrl(url)
    }

    fun goBack(): Boolean {
        if (webView.canGoBack()) { webView.goBack(); return true }
        return false
    }

    fun goForward(): Boolean {
        if (webView.canGoForward()) { webView.goForward(); return true }
        return false
    }

    fun canGoBack() = webView.canGoBack()
    fun canScrollUp() = webView.scrollY > 0 || ViewCompat.canScrollVertically(webView, -1)

    fun isPageLoading(): Boolean = webView.progress in 1..99

    fun reload() {
        webView.reload()
    }

    fun stopLoading() {
        if (isPageLoading()) {
            webView.stopLoading()
            // Reset the refresh/stop button icon back to refresh
            (activity as? DefaultActivity)?.onTabProgressChanged(this@TabFragment, 100)
        }
    }

    fun applySettings() {
        webView.settings.javaScriptEnabled = Prefs.isJavaScriptEnabled(requireContext())
        webView.settings.textZoom = Prefs.textZoom(requireContext())
    }

    fun injectPluginCss(css: String) {
        val escaped = org.json.JSONObject.quote(css)
        webView.evaluateJavascript(
            "(function() { var style = document.createElement('style'); style.innerHTML = JSON.parse($escaped); document.head.appendChild(style); })();",
            null
        )
    }

    fun injectPluginJs(js: String) {
        webView.evaluateJavascript(js, null)
    }
}
