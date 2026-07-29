package com.helix.browser.app

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

/**
 * Data class representing a plugin loaded from the local filesystem.
 */
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

/**
 * Manages loading, enabling/disabling, and injecting plugins into WebViews.
 * Plugins are stored in app's internal storage: /data/data/.../files/HelixPlugins/<plugin-id>/
 */
class PluginManager(private val context: Context) {

    private val pluginsDir = File(context.filesDir, "HelixPlugins")
    private var plugins = mutableListOf<InstalledPlugin>()

    init {
        loadPlugins()
    }

    /**
     * Scans the plugins directory and loads all valid plugin manifests.
     */
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
                        val plugin = InstalledPlugin(
                            id = id,
                            name = json.getString("name"),
                            version = json.getString("version"),
                            description = json.optString("description", ""),
                            matches = json.getJSONArray("matches").map { it.toString() },
                            css = json.optString("css", null)?.let { cssFile ->
                                File(pluginFolder, cssFile).takeIf { it.exists() }?.readText()
                            },
                            js = json.optString("js", null)?.let { jsFile ->
                                File(pluginFolder, jsFile).takeIf { it.exists() }?.readText()
                            },
                            enabled = json.optBoolean("enabled", true),
                            folder = pluginFolder
                        )
                        plugins.add(plugin)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Returns all installed plugins (including disabled ones).
     */
    fun getInstalledPlugins(): List<InstalledPlugin> = plugins

    /**
     * Returns plugins that match the given URL and are enabled.
     */
    fun getPluginsForUrl(url: String): List<InstalledPlugin> {
        return plugins.filter { plugin ->
            plugin.enabled && plugin.matches.any { pattern ->
                // Simple wildcard match: *://*/* matches everything
                pattern == "*://*/*" || url.contains(pattern.replace("*", ""))
            }
        }
    }

    /**
     * Injects a plugin's CSS and JS into the given WebView.
     * Should be called after page load (e.g., in onPageFinished).
     */
    fun injectPlugin(webView: WebView, plugin: InstalledPlugin) {
        plugin.css?.let { css ->
            val escapedCss = css.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript(
                "(function() { " +
                "var style = document.createElement('style'); " +
                "style.innerHTML = '$escapedCss'; " +
                "document.head.appendChild(style); " +
                "})();",
                null
            )
        }
        plugin.js?.let { js ->
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Toggle enable/disable status of a plugin by updating its manifest.json.
     */
    fun setPluginEnabled(id: String, enabled: Boolean) {
        val plugin = plugins.find { it.id == id } ?: return
        val manifestFile = File(plugin.folder, "manifest.json")
        if (manifestFile.exists()) {
            try {
                val json = JSONObject(manifestFile.readText())
                json.put("enabled", enabled)
                manifestFile.writeText(json.toString())
                loadPlugins() // Reload to reflect changes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
