package com.helix.browser.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "Bookmarks"
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
                    AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().clearAll()
                    loadBookmarks()
                    Toast.makeText(this@BookmarksActivity, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(clearBtn)

        setContentView(root)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BookmarkAdapter(
            emptyList(),
            onItemClick = { bookmark ->
                intent.putExtra("url", bookmark.url)
                setResult(RESULT_OK, intent)
                finish()
            },
            onDelete = { bookmark ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().delete(bookmark)
                    loadBookmarks()
                }
            }
        )
        recyclerView.adapter = adapter

        loadBookmarks()
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val bookmarks = AppDatabase.getInstance(this@BookmarksActivity).bookmarkDao().getAll()
            adapter.updateData(bookmarks)
        }
    }

    inner class BookmarkAdapter(
        private var items: List<Bookmark>,
        private val onItemClick: (Bookmark) -> Unit,
        private val onDelete: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

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
            holder.textView.setOnLongClickListener {
                onDelete(item)
                true
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Bookmark>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
