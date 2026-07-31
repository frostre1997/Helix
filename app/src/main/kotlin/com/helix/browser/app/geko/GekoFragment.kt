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

        // Listen for page loads to update history
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?) {
                url?.let {
                    // Add to history if it's a new navigation
                    if (history.isEmpty() || history.last() != it) {
                        // Remove forward history if we navigated back
                        if (currentIndex < history.size - 1) {
                            history.subList(currentIndex + 1, history.size).clear()
                        }
                        history.add(it)
                        currentIndex = history.size - 1
                    }
                }
            }
        })

        if (url.isNotEmpty()) {
            session.loadUri(url)
        } else {
            session.loadUri("https://www.google.com")
        }
    }

    // ---- Navigation methods using custom history ----
    fun loadUrl(url: String) {
        this.url = url
        if (::session.isInitialized) {
            session.loadUri(url)
        }
    }

    fun goBack(): Boolean {
        if (currentIndex > 0) {
            currentIndex--
            session.loadUri(history[currentIndex])
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        if (currentIndex < history.size - 1) {
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
