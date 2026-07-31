package com.helix.browser.app.geko

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GekoFragment : Fragment() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var url: String = ""

    // ---- Custom history stack ----
    private val history = mutableListOf<String>()
    private var currentIndex = -1
    private var isBackForward = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        geckoView = GeckoView(requireContext())
        geckoView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return geckoView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val runtime = GeckoRuntime.create(requireContext())
        session = GeckoSession()
        session.open(runtime)
        geckoView.setSession(session)

        // ---- Track page loads via ProgressDelegate ----
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!isBackForward) {
                    val currentUrl = session.getUri()   // ← FIXED: .getUri() not .uri
                    if (currentUrl != null && (history.isEmpty() || history.last() != currentUrl)) {
                        // Remove forward history if we navigated back
                        if (currentIndex < history.size - 1) {
                            history.subList(currentIndex + 1, history.size).clear()
                        }
                        history.add(currentUrl)
                        currentIndex = history.size - 1
                    }
                }
                isBackForward = false
            }
        })

        if (url.isNotEmpty()) {
            session.loadUri(url)
        } else {
            session.loadUri("https://www.google.com")
        }
    }

    fun loadUrl(url: String) {
        this.url = url
        if (::session.isInitialized) {
            session.loadUri(url)
        }
    }

    fun goBack(): Boolean {
        if (currentIndex > 0) {
            isBackForward = true
            currentIndex--
            session.loadUri(history[currentIndex])
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        if (currentIndex < history.size - 1) {
            isBackForward = true
            currentIndex++
            session.loadUri(history[currentIndex])
            return true
        }
        return false
    }

    fun reload() {
        session.reload()
    }

    fun canGoBack(): Boolean = currentIndex > 0
    fun canGoForward(): Boolean = currentIndex < history.size - 1
}
