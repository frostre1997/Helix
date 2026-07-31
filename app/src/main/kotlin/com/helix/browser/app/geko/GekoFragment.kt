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

    fun reload() {
        session.reload()
    }

    fun goBack(): Boolean {
        return try {
            session.goBack()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun goForward(): Boolean {
        return try {
            session.goForward()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun canGoBack(): Boolean {
        return try {
            session.canGoBack()
        } catch (_: Exception) {
            false
        }
    }

    fun canGoForward(): Boolean {
        return try {
            session.canGoForward()
        } catch (_: Exception) {
            false
        }
    }
}
