package com.helix.browser.app

import android.os.Bundle
import android.widget.Toast
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
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = HistoryAdapter(emptyList()) { history ->
            finishWithResult(history.url)
        }
        recyclerView.adapter = adapter

        loadHistory()

        findViewById<View>(R.id.clearHistoryButton).setOnClickListener {
            lifecycleScope.launch {
                AppDatabase.getInstance(this@HistoryActivity).historyDao().clearAll()
                loadHistory()
                Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = AppDatabase.getInstance(this@HistoryActivity).historyDao().getAll()
            adapter.updateData(history)
        }
    }

    private fun finishWithResult(url: String) {
        intent.putExtra("url", url)
        setResult(RESULT_OK, intent)
        finish()
    }

    inner class HistoryAdapter(
        private var items: List<History>,
        private val onItemClick: (History) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.text1.text = item.title
            holder.text2.text = item.url
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<History>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}
