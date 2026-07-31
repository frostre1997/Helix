package com.helix.browser.app

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchSuggestionAdapter(
    private var suggestions: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SearchSuggestionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Create TextView programmatically
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                (16 * parent.context.resources.displayMetrics.density).toInt(),
                (14 * parent.context.resources.displayMetrics.density).toInt(),
                (16 * parent.context.resources.displayMetrics.density).toInt(),
                (14 * parent.context.resources.displayMetrics.density).toInt()
            )
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            // Optional: add a divider or ripple effect
            isClickable = true
            isFocusable = true
            // Set background with ripple (optional)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#333333")),
                null,
                null
            )
        }
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = suggestions[position]
        holder.textView.text = item
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = suggestions.size

    fun updateSuggestions(newSuggestions: List<String>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }
}
