package com.helix.browser.app

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

data class InstalledPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val matches: List<String>,
    val css: String?,
    val js: String?,
    val enabled: Boolean,
    val folder: File
)

class PluginManager(private val context: Context) {
    private val pluginsDir = File(context.filesDir, "HelixPlugins")
    private var plugins = mutableListOf<InstalledPlugin>()

    init { loadPlugins() }

    fun loadPlugins() {
        plugins.clear()
        if (!pluginsDir.exists()) return
        pluginsDir.listFiles()?.forEach { pluginFolder ->
            if (pluginFolder.isDirectory) {
                val manifestFile = File(pluginFolder, "manifest.json")
                if (manifestFile.exists()) {
                    try {
                        val json = JSONObject(manifestFile.readText())
                        val id = pluginFolder.name
                        val matchesArray = json.getJSONArray("matches")
                        val matches = (0 until matchesArray.length()).map { matchesArray.getString(it) }
                        val plugin = InstalledPlugin(
                            id = id,
                            name = json.getString("name"),
                            version = json.getString("version"),
                            description = json.optString("description", ""),
                            matches = matches,
                            css = json.optString("css", null)?.let { File(pluginFolder, it).takeIf { it.exists() }?.readText() },
                            js = json.optString("js", null)?.let { File(pluginFolder, it).takeIf { it.exists() }?.readText() },
                            enabled = json.optBoolean("enabled", true),
                            folder = pluginFolder
                        )
                        plugins.add(plugin)
                    } catch (_: Exception) {
                        // Ignore malformed plugins
                    }
                }
            }
        }
    }

    fun getInstalledPlugins(): List<InstalledPlugin> = plugins
    fun getPluginsForUrl(url: String): List<InstalledPlugin> =
        plugins.filter { it.enabled && it.matches.any { pattern -> pattern == "*://*/*" || url.contains(pattern.replace("*", "")) } }

    fun injectPlugin(webView: WebView, plugin: InstalledPlugin) {
        plugin.css?.let { css ->
            val escapedCss = css.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("(function() { var style = document.createElement('style'); style.innerHTML = '$escapedCss'; document.head.appendChild(style); })();", null)
        }
        plugin.js?.let { webView.evaluateJavascript(it, null) }
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        val plugin = plugins.find { it.id == id } ?: return
        val manifestFile = File(plugin.folder, "manifest.json")
        if (manifestFile.exists()) {
            try {
                val json = JSONObject(manifestFile.readText())
                json.put("enabled", enabled)
                manifestFile.writeText(json.toString())
                loadPlugins()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
