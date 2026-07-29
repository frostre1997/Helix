package com.helix.browser.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipFile

class PluginStoreActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PluginStoreAdapter
    private val client = OkHttpClient()
    private val PLUGIN_API_URL = "https://helixplugins.onrender.com/plugins.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin_store)

        recyclerView = findViewById(R.id.pluginStoreRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = PluginStoreAdapter(emptyList()) { plugin ->
            installPlugin(plugin)
        }
        recyclerView.adapter = adapter

        fetchPlugins()
    }

    private fun fetchPlugins() {
        lifecycleScope.launch {
            try {
                val request = Request.Builder().url(PLUGIN_API_URL).build()
                val response = client.newCall(request).execute()
                val jsonString = response.body?.string()
                if (jsonString != null) {
                    val jsonArray = JSONArray(jsonString)
                    val plugins = mutableListOf<Plugin>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        plugins.add(
                            Plugin(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                version = obj.getString("version"),
                                description = obj.getString("description"),
                                author = obj.getString("author"),
                                icon = obj.getString("icon"),
                                downloadUrl = obj.getString("downloadUrl"),
                                tags = obj.getJSONArray("tags").map { it.toString() }
                            )
                        )
                    }
                    adapter.updatePlugins(plugins)
                }
            } catch (e: Exception) {
                Toast.makeText(this@PluginStoreActivity, "Failed to load plugins: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun installPlugin(plugin: Plugin) {
        lifecycleScope.launch {
            try {
                // Download ZIP
                val request = Request.Builder().url("https://helixplugins.onrender.com${plugin.downloadUrl}").build()
                val response = client.newCall(request).execute()
                val zipFile = File(filesDir, "${plugin.id}.zip")
                response.body?.let { body ->
                    zipFile.writeBytes(body.bytes())
                }

                // Extract ZIP
                val destDir = File(filesDir, "HelixPlugins/${plugin.id}")
                destDir.mkdirs()
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val target = File(destDir, entry.name)
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                zipFile.delete()

                Toast.makeText(this@PluginStoreActivity, "${plugin.name} installed!", Toast.LENGTH_SHORT).show()

                // Mark as installed in adapter
                adapter.markInstalled(plugin.id)

            } catch (e: Exception) {
                Toast.makeText(this@PluginStoreActivity, "Install failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class Plugin(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val author: String,
        val icon: String,
        val downloadUrl: String,
        val tags: List<String>
    )

    inner class PluginStoreAdapter(
        private var plugins: List<Plugin>,
        private val onInstall: (Plugin) -> Unit
    ) : RecyclerView.Adapter<PluginStoreAdapter.ViewHolder>() {

        private val installedIds = mutableSetOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plugin = plugins[position]
            holder.text1.text = "${plugin.icon} ${plugin.name} v${plugin.version}"
            holder.text2.text = plugin.description
            holder.itemView.setOnClickListener {
                if (installedIds.contains(plugin.id)) {
                    Toast.makeText(holder.itemView.context, "Already installed", Toast.LENGTH_SHORT).show()
                } else {
                    onInstall(plugin)
                }
            }
        }

        override fun getItemCount() = plugins.size

        fun updatePlugins(newPlugins: List<Plugin>) {
            plugins = newPlugins
            notifyDataSetChanged()
        }

        fun markInstalled(id: String) {
            installedIds.add(id)
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}
