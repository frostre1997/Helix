package com.helix.browser.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView

class SettingsActivity : AppCompatActivity() {

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
            text = "Settings"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        // ----- Enable JavaScript -----
        val jsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val jsLabel = TextView(this).apply {
            text = "Enable JavaScript"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val jsSwitch = Switch(this).apply {
            isChecked = Prefs.isJavaScriptEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setJavaScriptEnabled(this@SettingsActivity, isChecked)
                Toast.makeText(this@SettingsActivity, "JavaScript ${if (isChecked) "enabled" else "disabled"} (applies to new pages)", Toast.LENGTH_SHORT).show()
            }
        }
        jsLayout.addView(jsLabel)
        jsLayout.addView(jsSwitch)
        root.addView(jsLayout)

        // ----- Block popups -----
        val popupLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val popupLabel = TextView(this).apply {
            text = "Block pop-ups"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val popupSwitch = Switch(this).apply {
            isChecked = Prefs.blockPopups(this@SettingsActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setBlockPopups(this@SettingsActivity, isChecked)
            }
        }
        popupLayout.addView(popupLabel)
        popupLayout.addView(popupSwitch)
        root.addView(popupLayout)

        // ----- Fullscreen mode -----
        val fullscreenLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val fullscreenLabel = TextView(this).apply {
            text = "Fullscreen mode"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val fullscreenSwitch = Switch(this).apply {
            isChecked = Prefs.fullscreenMode(this@SettingsActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setFullscreenMode(this@SettingsActivity, isChecked)
                Toast.makeText(this@SettingsActivity, "Restart the app to apply", Toast.LENGTH_SHORT).show()
            }
        }
        fullscreenLayout.addView(fullscreenLabel)
        fullscreenLayout.addView(fullscreenSwitch)
        root.addView(fullscreenLayout)

        // ----- Search engine -----
        val engineLabel = TextView(this).apply {
            text = "Search engine"
            textSize = 16f
        }
        root.addView(engineLabel)
        val engineSpinner = Spinner(this)
        val engines = arrayOf("Google", "Bing", "DuckDuckGo")
        val engineKeys = arrayOf("google", "bing", "duckduckgo")
        engineSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            engines
        )
        val currentEngine = Prefs.searchEngine(this)
        engineSpinner.setSelection(engineKeys.indexOf(currentEngine).coerceAtLeast(0))
        engineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Prefs.setSearchEngine(this@SettingsActivity, engineKeys[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(engineSpinner)

        // ----- Text zoom -----
        val zoomLabel = TextView(this).apply {
            text = "Text zoom"
            textSize = 16f
        }
        root.addView(zoomLabel)
        val zoomSeek = SeekBar(this).apply {
            max = 40
            progress = Prefs.textZoom(this@SettingsActivity) - 80
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val zoomValue = TextView(this).apply {
            text = "${Prefs.textZoom(this@SettingsActivity)}%"
            textSize = 13f
            setTextColor(android.graphics.Color.GRAY)
        }
        zoomSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                zoomValue.text = "${progress + 80}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Prefs.setTextZoom(this@SettingsActivity, zoomSeek.progress + 80)
                Toast.makeText(this@SettingsActivity, "Text zoom: ${zoomSeek.progress + 80}%", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(zoomSeek)
        root.addView(zoomValue)

        // ----- Clear browsing data -----
        val clearBtn = Button(this).apply {
            text = "Clear Cache & Data"
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Clear browsing data")
                    .setMessage("This will clear the cache, cookies and web storage.")
                    .setPositiveButton("Clear") { _, _ ->
                        WebView(this@SettingsActivity).apply {
                            clearCache(true)
                            destroy()
                        }
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.WebStorage.getInstance().deleteAllData()
                        Toast.makeText(this@SettingsActivity, "Cache cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        root.addView(clearBtn)

        setContentView(root)
    }
}
