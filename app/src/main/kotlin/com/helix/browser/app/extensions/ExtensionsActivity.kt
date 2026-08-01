package com.helix.browser.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var pluginManager: PluginManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExtensionsAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pluginManager = PluginManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "Extensions"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Manage installed injection plugins"
            textSize = 13f
            setTextColor(android.graphics.Color.GRAY)
        }
        root.addView(subtitle)

        emptyView = TextView(this).apply {
            text = "No extensions installed.\nTap \"Browse Plugin Store\" below."
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

        val storeBtn = Button(this).apply {
            text = "Browse Plugin Store"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@ExtensionsActivity, PluginStoreActivity::class.java))
            }
        }
        root.addView(storeBtn)

        setContentView(root)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ExtensionsAdapter(emptyList())
        recyclerView.adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val plugins = pluginManager.getInstalledPlugins()
        adapter.update(plugins)
        emptyView.visibility = if (plugins.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recyclerView.visibility = if (plugins.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    inner class ExtensionsAdapter(private var items: List<InstalledPlugin>) :
        RecyclerView.Adapter<ExtensionsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(12, 12, 12, 12)
            }
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val name = TextView(parent.context).apply {
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggle = Switch(parent.context)
            row.addView(name)
            row.addView(toggle)
            card.addView(row)

            val details = TextView(parent.context).apply {
                textSize = 13f
                setTextColor(android.graphics.Color.GRAY)
            }
            card.addView(details)

            val deleteBtn = Button(parent.context).apply {
                text = "Delete"
            }
            card.addView(deleteBtn)

            return ViewHolder(card, name, toggle, details, deleteBtn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plugin = items[position]
            holder.name.text = "${plugin.name} v${plugin.version}"
            holder.toggle.isChecked = plugin.enabled
            holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                pluginManager.setPluginEnabled(plugin.id, isChecked)
                Toast.makeText(
                    this@ExtensionsActivity,
                    "${plugin.name} ${if (isChecked) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            holder.details.text = buildString {
                append(plugin.description)
                if (plugin.matches.isNotEmpty()) {
                    append("\nMatches: ")
                    append(plugin.matches.joinToString(", "))
                }
            }

            holder.deleteBtn.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@ExtensionsActivity)
                    .setTitle("Delete ${plugin.name}?")
                    .setMessage("This will remove the extension and cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        pluginManager.deletePlugin(plugin.id)
                        reload()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = items.size

        fun update(newItems: List<InstalledPlugin>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(
            val card: LinearLayout,
            val name: TextView,
            val toggle: Switch,
            val details: TextView,
            val deleteBtn: Button
        ) : RecyclerView.ViewHolder(card)
    }
}
