package com.helix.browser.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class FloatingSearchBar(
    private val context: Context,
    private val onSearch: (String) -> Unit
) {

    private val overlay: FrameLayout
    private val card: LinearLayout
    private val searchInput: EditText
    private val suggestionsList: RecyclerView
    private val suggestionAdapter: SearchSuggestionAdapter
    private val suggestionClient = OkHttpClient()
    private var isShowing = false

    init {
        // ---- Root overlay ----
        overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#00000000"))
            visibility = View.GONE
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val cardRect = android.graphics.Rect()
                    card.getGlobalVisibleRect(cardRect)
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    if (!cardRect.contains(touchX, touchY)) {
                        hide()
                    }
                }
                true
            }
        }

        // ---- Wider card ----
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                // Smaller margins = wider card
                val margin = (42 * context.resources.displayMetrics.density).toInt()
                setMargins(margin, 0, margin, 0)
            }
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            elevation = 16f
            val padding = (36 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnTouchListener { _, _ -> false }
        }

        // ---- Search input with icon on the right ----
        searchInput = EditText(context).apply {
            hint = "Search on Helix"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)

            // Bigger text
            textSize = 18f
            val inputPadding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(inputPadding, inputPadding, inputPadding, inputPadding)

            val icon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_search)
            icon?.setTint(Color.WHITE)
            setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null)
            compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()

            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
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

        // ---- Text watcher ----
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

    // ----- Show -----
    fun show(initialText: String = "") {
        if (isShowing) return
        searchInput.setText(initialText)
        searchInput.setSelection(searchInput.text.length)
        overlay.visibility = View.VISIBLE
        isShowing = true
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(200).start()
        searchInput.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // ----- Hide -----
    fun hide() {
        if (!isShowing) return
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

    // ----- Toggle -----
    fun toggle(initialText: String = "") {
        if (isShowing) hide() else show(initialText)
    }

    // ----- Get root view -----
    fun getView(): View = overlay

    // ----- Check if showing -----
    fun isShowing(): Boolean = isShowing

    // ----- Smart suggestions -----
    private fun fetchSuggestions(query: String) {
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

    // ----- Clean up -----
    fun destroy() {
        overlay.removeAllViews()
    }
}
