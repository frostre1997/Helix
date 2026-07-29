package com.helix.browser.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.app.data.AppDatabase
import com.helix.browser.app.data.Bookmark
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BookmarkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        recyclerView = findViewById(R.id.bookmarksRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = BookmarkAdapter(emptyList()) { bookmark ->
            // Open bookmark in browser (finish this activity and load URL)
            finishWithResult(bookmark.url)
        } onDelete = { bookmark ->
            lifecycleScope.launch {
                AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().delete(bookmark)
                loadBookmarks()
            }
        }
        recyclerView.adapter = adapter

        loadBookmarks()

        findViewById<View>(R.id.clearBookmarksButton).setOnClickListener {
            lifecycleScope.launch {
                AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().clearAll()
                loadBookmarks()
                Toast.makeText(this@BookmarksActivity, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val bookmarks = AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().getAll()
            adapter.updateData(bookmarks)
        }
    }

    private fun finishWithResult(url: String) {
        intent.putExtra("url", url)
        setResult(RESULT_OK, intent)
        finish()
    }

    inner class BookmarkAdapter(
        private var items: List<Bookmark>,
        private val onItemClick: (Bookmark) -> Unit,
        private val onDelete: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

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
            holder.itemView.setOnLongClickListener {
                onDelete(item)
                true
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Bookmark>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}
