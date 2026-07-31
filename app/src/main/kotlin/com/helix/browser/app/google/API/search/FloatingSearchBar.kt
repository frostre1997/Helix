package com.helix.browser.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class FloatingSearchBar(
    private val context: Context,
    private val onSearch: (String) -> Unit
) {

    private val overlay: FrameLayout
    private val searchInput: EditText
    private val suggestionsList: RecyclerView
    private val suggestionAdapter: SearchSuggestionAdapter
    private val suggestionClient = OkHttpClient()
    private var isShowing = false

    init {
        // ---- Root overlay (full screen, dim background) ----
        overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE
            setOnClickListener { hide() } // tap outside to close
        }

        // ---- Centered card ----
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(
                    (32 * context.resources.displayMetrics.density).toInt(),
                    0,
                    (32 * context.resources.displayMetrics.density).toInt(),
                    0
                )
            }
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            elevation = 16f
            setPadding(
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt()
            )
        }

        // ---- Search input ----
        searchInput = EditText(context).apply {
            hint = "Search or enter URL"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            // Prevent card click from closing overlay
            setOnTouchListener { _, _ -> false }
        }
        card.addView(searchInput)

        // ---- Suggestions list ----
        suggestionsList = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            setBackgroundColor(Color.TRANSPARENT)
            layoutManager = LinearLayoutManager(context)
        }
        suggestionAdapter = SearchSuggestionAdapter(emptyList()) { suggestion ->
            searchInput.setText(suggestion)
            searchInput.setSelection(suggestion.length)
            onSearch(suggestion)
            hide()
        }
        suggestionsList.adapter = suggestionAdapter
        card.addView(suggestionsList)

        overlay.addView(card)

        // ---- Text watcher for suggestions ----
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.length >= 2) {
                    fetchSuggestions(query)
                } else {
                    suggestionsList.visibility = View.GONE
                    suggestionAdapter.updateSuggestions(emptyList())
                }
            }
        })

        // ---- Enter key ----
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val query = searchInput.text.toString()
                if (query.isNotEmpty()) {
                    onSearch(query)
                    hide()
                }
                true
            } else false
        }
    }

    // ----- Show overlay -----
    fun show(initialText: String = "") {
        if (isShowing) return
        searchInput.setText(initialText)
        searchInput.setSelection(searchInput.text.length)
        overlay.visibility = View.VISIBLE
        isShowing = true
        // Fade in
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(200).start()
        searchInput.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // ----- Hide overlay -----
    fun hide() {
        if (!isShowing) return
        // Fade out
        overlay.animate().alpha(0f).setDuration(200).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                overlay.visibility = View.GONE
                isShowing = false
                suggestionsList.visibility = View.GONE
                suggestionAdapter.updateSuggestions(emptyList())
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                searchInput.clearFocus()
            }
        }).start()
    }

    // ----- Toggle visibility -----
    fun toggle(initialText: String = "") {
        if (isShowing) hide() else show(initialText)
    }

    // ----- Smart suggestions -----
    private fun fetchSuggestions(query: String) {
        // Use lifecycleScope – we need to pass a lifecycle owner. We'll use a callback or get from context.
        // Since we don't have a lifecycle owner here, we'll use a handler or pass one in.
        // Quick fix: use a Handler to avoid blocking.
        Handler(Looper.getMainLooper()).post {
            try {
                val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=${Uri.encode(query)}"
                val request = Request.Builder().url(url).build()
                val response = suggestionClient.newCall(request).execute()
                val json = response.body?.string()
                if (json != null) {
                    val array = JSONArray(json)
                    val suggestionsArray = array.getJSONArray(1)
                    val suggestions = mutableListOf<String>()
                    for (i in 0 until suggestionsArray.length()) {
                        suggestions.add(suggestionsArray.getString(i))
                    }
                    suggestionAdapter.updateSuggestions(suggestions)
                    suggestionsList.visibility = if (suggestions.isNotEmpty()) View.VISIBLE else View.GONE
                }
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    // ----- Get the root view to add to activity -----
    fun getView(): View = overlay

    // ----- Check if showing -----
    fun isShowing(): Boolean = isShowing

    // ----- Clean up -----
    fun destroy() {
        overlay.removeAllViews()
    }
}
