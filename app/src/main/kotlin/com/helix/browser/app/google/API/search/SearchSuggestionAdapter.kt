package com.helix.browser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchSuggestionAdapter(
    private var suggestions: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SearchSuggestionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
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
        val textView: TextView = itemView.findViewById(R.id.suggestionText)
    }
}
