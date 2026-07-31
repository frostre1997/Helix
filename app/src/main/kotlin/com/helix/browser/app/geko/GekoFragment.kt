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

    private lateinit var geckoView: GeckoView   // ← fixed typo
    private lateinit var session: GeckoSession
    private var url: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        geckoView = GeckoView(requireContext())   // ← fixed typo
        geckoView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return geckoView   // ← fixed typo
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val runtime = GeckoRuntime.create(requireContext())
        session = GeckoSession()
        session.open(runtime)
        geckoView.setSession(session)   // ← fixed typo

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
        if (session.canGoBack()) {   // ← correct method
            session.goBack()
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        if (session.canGoForward()) {   // ← correct method
            session.goForward()
            return true
        }
        return false
    }

    fun reload() {
        session.reload()
    }

    fun canGoBack(): Boolean = session.canGoBack()
    fun canGoForward(): Boolean = session.canGoForward()
}
