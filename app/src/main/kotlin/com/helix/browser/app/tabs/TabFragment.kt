package com.helix.browser.app

import com.helix.browser.app.DefaultActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class TabFragment : Fragment() {

    lateinit var webView: WebView
    var url: String = ""

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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Update tab title when page loads
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, view?.title ?: url ?: "")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onPageFinished(wiev, url)
                url?.let {
                    (activity as? DefaultActivity)?saveHistory(it, view?.title ?: it)
                }   
                (activity as? DefaultActivity)?.updateTabTitle(this@TabFragment, title ?: "")
            }
        }

        if (url.isNotEmpty()) {
            webView.loadUrl(url)
        } else {
            // load homepage or about:blank
            webView.loadUrl("https://www.google.com")
        }
    }

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
