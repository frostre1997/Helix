package com.helix.browser.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.History
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "History"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(recyclerView)

        val clearBtn = Button(this).apply {
            text = "Clear All"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@HistoryActivity).historyDao().clearAll()
                    loadHistory()
                    Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(clearBtn)

        setContentView(root)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(emptyList()) { history ->
            intent.putExtra("url", history.url)
            setResult(RESULT_OK, intent)
            finish()
        }
        recyclerView.adapter = adapter
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = AppDatabase.getInstance(this@HistoryActivity).historyDao().getAll()
            adapter.updateData(history)
        }
    }

    inner class HistoryAdapter(
        private var items: List<History>,
        private val onItemClick: (History) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
                textSize = 16f
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = "${item.title}\n${item.url}"
            holder.textView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<History>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
