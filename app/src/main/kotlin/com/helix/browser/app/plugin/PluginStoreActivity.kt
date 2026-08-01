package com.helix.browser.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipFile

data class StorePlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val icon: String,
    val downloadUrl: String,
    val tags: List<String>
)

class PluginStoreActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PluginStoreAdapter
    private val client = OkHttpClient()
    private val PLUGIN_API_URL = "https://helixplugins.onrender.com/plugins.json"

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
            text = "Plugin Store"
            textSize = 24f
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
        setContentView(root)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PluginStoreAdapter(emptyList()) { plugin -> installPlugin(plugin) }
        recyclerView.adapter = adapter

        fetchPlugins()
    }

    private fun fetchPlugins() {
        lifecycleScope.launch {
            try {
                // Do network call on IO dispatcher to avoid blocking the main thread
                val jsonString = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(PLUGIN_API_URL).build()).execute().use { resp ->
                        resp.body?.string()
                    }
                }

                if (!jsonString.isNullOrEmpty()) {
                    // Parse on IO as well (could be slightly heavy)
                    val plugins = withContext(Dispatchers.IO) {
                        val jsonArray = JSONArray(jsonString)
                        val list = mutableListOf<StorePlugin>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val tagsArray = obj.getJSONArray("tags")
                            val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
                            list.add(
                                StorePlugin(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    version = obj.getString("version"),
                                    description = obj.getString("description"),
                                    author = obj.getString("author"),
                                    icon = obj.getString("icon"),
                                    downloadUrl = obj.getString("downloadUrl"),
                                    tags = tags
                                )
                            )
                        }
                        list
                    }

                    // Update UI on main thread
                    adapter.updatePlugins(plugins)
                    adapter.markInstalled(PluginManager(this@PluginStoreActivity).getInstalledPlugins().map { it.id }.toSet())
                }
            } catch (e: Exception) {
                Toast.makeText(this@PluginStoreActivity, "Failed to load plugins", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun installPlugin(plugin: StorePlugin) {
        lifecycleScope.launch {
            try {
                val baseUrl = "https://helixplugins.onrender.com"
                val downloadUrl = baseUrl + plugin.downloadUrl

                // Download and unzip on IO dispatcher
                val zipBytes = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(downloadUrl).build()).execute().use { resp ->
                        resp.body?.bytes()
                    }
                }

                if (zipBytes == null) {
                    Toast.makeText(this@PluginStoreActivity, "Install failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val zipFile = File(filesDir, "${plugin.id}.zip")

                withContext(Dispatchers.IO) {
                    zipFile.writeBytes(zipBytes)

                    val destDir = File(filesDir, "HelixPlugins/${plugin.id}")
                    destDir.mkdirs()
                    ZipFile(zipFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            val target = File(destDir, entry.name)
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                        }
                    }
                    zipFile.delete()
                }

                Toast.makeText(this@PluginStoreActivity, "${plugin.name} installed!", Toast.LENGTH_SHORT).show()
                adapter.markInstalled(PluginManager(this@PluginStoreActivity).getInstalledPlugins().map { it.id }.toSet())
            } catch (e: Exception) {
                Toast.makeText(this@PluginStoreActivity, "Install failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class PluginStoreAdapter(
        private var plugins: List<StorePlugin>,
        private val onInstall: (StorePlugin) -> Unit
    ) : RecyclerView.Adapter<PluginStoreAdapter.ViewHolder>() {

        private val installedIds = mutableSetOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
                textSize = 16f
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plugin = plugins[position]
            holder.textView.text = "${plugin.icon} ${plugin.name} v${plugin.version}\n${plugin.description}"
            holder.textView.setBackgroundColor(if (installedIds.contains(plugin.id)) 0x2200FF00 else 0x00000000)
            holder.textView.setOnClickListener {
                if (installedIds.contains(plugin.id)) {
                    Toast.makeText(holder.textView.context, "Already installed", Toast.LENGTH_SHORT).show()
                } else {
                    onInstall(plugin)
                }
            }
        }

        override fun getItemCount() = plugins.size

        fun updatePlugins(newPlugins: List<StorePlugin>) { plugins = newPlugins; notifyDataSetChanged() }
        fun markInstalled(ids: Set<String>) { installedIds.clear(); installedIds.addAll(ids); notifyDataSetChanged() }

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
