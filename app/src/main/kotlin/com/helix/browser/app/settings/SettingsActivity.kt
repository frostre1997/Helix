package com.helix.browser.app

import android.view.ViewGroup
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.webkit.WebView

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        // JavaScript toggle
        val jsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val jsLabel = TextView(this).apply { text = "Enable JavaScript"; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
        val jsSwitch = Switch(this).apply {
            isChecked = PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity).getBoolean("enable_javascript", true)
            setOnCheckedChangeListener { _, isChecked ->
                PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity).edit().putBoolean("enable_javascript", isChecked).apply()
            }
        }
        jsLayout.addView(jsLabel)
        jsLayout.addView(jsSwitch)
        root.addView(jsLayout)

        // Clear cache button
        val clearBtn = Button(this).apply {
            text = "Clear Cache"
            setOnClickListener {
                WebView(this@SettingsActivity).clearCache(true)
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
                Toast.makeText(this@SettingsActivity, "Cache cleared", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(clearBtn)

        setContentView(root)
    }
}
