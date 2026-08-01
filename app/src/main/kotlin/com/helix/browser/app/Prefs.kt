package com.helix.browser.app

import android.content.Context
import androidx.preference.PreferenceManager
import java.net.URLEncoder

object Prefs {

    private const val KEY_JAVASCRIPT = "enable_javascript"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_BLOCK_POPUPS = "block_popups"
    private const val KEY_FULLSCREEN = "fullscreen_mode"
    private const val KEY_TEXT_ZOOM = "text_zoom"

    fun prefs(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun isJavaScriptEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_JAVASCRIPT, true)

    fun setJavaScriptEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_JAVASCRIPT, enabled).apply()
    }

    fun searchEngine(context: Context): String =
        prefs(context).getString(KEY_SEARCH_ENGINE, "google") ?: "google"

    fun setSearchEngine(context: Context, engine: String) {
        prefs(context).edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    fun blockPopups(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_POPUPS, true)

    fun setBlockPopups(context: Context, block: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_POPUPS, block).apply()
    }

    fun fullscreenMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FULLSCREEN, false)

    fun setFullscreenMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FULLSCREEN, enabled).apply()
    }

    fun textZoom(context: Context): Int =
        prefs(context).getInt(KEY_TEXT_ZOOM, 100)

    fun setTextZoom(context: Context, zoom: Int) {
        prefs(context).edit().putInt(KEY_TEXT_ZOOM, zoom).apply()
    }

    fun homeUrl(context: Context): String = when (searchEngine(context)) {
        "bing" -> "https://www.bing.com"
        "duckduckgo" -> "https://duckduckgo.com"
        else -> "https://www.google.com"
    }

    fun searchUrl(context: Context, query: String): String {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim()
        }
        return when (searchEngine(context)) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun isUrl(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            (trimmed.contains(".") && !trimmed.contains(" "))
    }

    fun toUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> trimmed
        }
    }
}
