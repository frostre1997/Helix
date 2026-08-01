package com.helix.browser.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class DownloadEntry(
    val id: Long,
    val title: String,
    val description: String,
    val status: Int,
    val bytesTotal: Long,
    val bytesSoFar: Long,
    val localUri: String?,
    val mimeType: String?,
    val dateAdded: Long
)

class DownloadsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var emptyView: TextView
    private lateinit var downloadManager: DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadDownloads()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(this).apply {
            text = "Downloads"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        val refreshBtn = Button(this).apply {
            text = "Refresh"
            setOnClickListener { loadDownloads() }
        }
        header.addView(refreshBtn)
        root.addView(header)

        emptyView = TextView(this).apply {
            text = "No downloads yet.\nDownloads will appear here."
            gravity = android.view.Gravity.CENTER
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 64 }
        }
        root.addView(emptyView)

        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(recyclerView)

        setContentView(root)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DownloadsAdapter(emptyList())
        recyclerView.adapter = adapter

        loadDownloads()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadDownloads() {
        val entries = mutableListOf<DownloadEntry>()
        try {
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_PENDING or
                    DownloadManager.STATUS_RUNNING or
                    DownloadManager.STATUS_PAUSED or
                    DownloadManager.STATUS_SUCCESSFUL or
                    DownloadManager.STATUS_FAILED
            )
            downloadManager.query(query).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    entries.add(
                        DownloadEntry(
                            id = id,
                            title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)),
                            description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)),
                            status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                            bytesSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)),
                            dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
                        )
                    )
                }
            }
            entries.sortByDescending { it.dateAdded }
        } catch (_: Exception) {
            // Ignore query errors
        }

        adapter.update(entries)
        emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recyclerView.visibility = if (entries.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun openDownload(entry: DownloadEntry) {
        val uri = entry.localUri?.let { Uri.parse(it) } ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entry.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDownload(entry: DownloadEntry) {
        val uri = entry.localUri?.let { Uri.parse(it) } ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share download"))
        } catch (_: Exception) {
            Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteDownload(entry: DownloadEntry) {
        downloadManager.remove(entry.id)
        loadDownloads()
        Toast.makeText(this, "Download removed", Toast.LENGTH_SHORT).show()
    }

    inner class DownloadsAdapter(private var items: List<DownloadEntry>) :
        RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(12, 12, 12, 12)
                isClickable = true
                isFocusable = true
            }
            val progress = ProgressBar(parent.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(card, progress)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.title.text = entry.title.ifBlank { entry.description.ifBlank { "Download" } }
            holder.subtitle.text = statusText(entry)
            if (entry.status == DownloadManager.STATUS_RUNNING ||
                entry.status == DownloadManager.STATUS_PAUSED ||
                entry.status == DownloadManager.STATUS_PENDING
            ) {
                holder.progress.visibility = android.view.View.VISIBLE
                val percent = if (entry.bytesTotal > 0) (entry.bytesSoFar * 100 / entry.bytesTotal).toInt() else 0
                holder.progress.progress = percent
            } else {
                holder.progress.visibility = android.view.View.GONE
            }
            holder.itemView.setOnClickListener {
                when (entry.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> openDownload(entry)
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING ->
                        Toast.makeText(this@DownloadsActivity, "Download in progress", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this@DownloadsActivity, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
            holder.itemView.setOnLongClickListener {
                val options = arrayOf("Open", "Share", "Delete")
                androidx.appcompat.app.AlertDialog.Builder(this@DownloadsActivity)
                    .setTitle(entry.title)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> openDownload(entry)
                            1 -> shareDownload(entry)
                            2 -> deleteDownload(entry)
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size

        fun update(newItems: List<DownloadEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        private fun statusText(entry: DownloadEntry): String {
            val size = if (entry.bytesTotal > 0) formatBytes(entry.bytesSoFar) + " / " + formatBytes(entry.bytesTotal) else formatBytes(entry.bytesSoFar)
            val status = when (entry.status) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                else -> "Unknown"
            }
            val date = if (entry.dateAdded > 0) {
                DateFormat.getDateTimeInstance().format(Date(entry.dateAdded))
            } else ""
            return "$status · $size\n$date"
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.size - 1) {
                value /= 1024
                unit++
            }
            return String.format(Locale.US, "%.1f %s", value, units[unit])
        }

        inner class ViewHolder(
            val card: LinearLayout,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(card) {
            val title: TextView
            val subtitle: TextView

            init {
                title = TextView(card.context).apply {
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                }
                subtitle = TextView(card.context).apply {
                    textSize = 13f
                    setTextColor(android.graphics.Color.GRAY)
                    maxLines = 2
                }
                card.addView(title)
                card.addView(subtitle)
                card.addView(progress)
            }
        }
    }
}
