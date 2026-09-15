package dev.darn.spatiallauncher

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var surface: SpatialLauncherView
    private val prefs by lazy { LauncherPreferences(this) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.setDecorFitsSystemWindows(false)
        surface = SpatialLauncherView(this, AppRepository(this), prefs, ::showSettings)
        setContentView(surface)
    }

    override fun onResume() { super.onResume(); if (::surface.isInitialized) surface.reloadApps() }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); surface.goHome() }
    override fun onBackPressed() { if (!surface.closeOverlay()) super.onBackPressed() }

    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 12) }
        fun slider(title: String, initial: Float, changed: (Float) -> Unit) {
            box.addView(TextView(this).apply { text = title; textSize = 16f; setPadding(0, 24, 0, 4) })
            box.addView(SeekBar(this).apply {
                max = 100; progress = (initial * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(v: SeekBar?, p: Int, user: Boolean) { if (user) changed(p / 100f) }
                    override fun onStartTrackingTouch(v: SeekBar?) = Unit
                    override fun onStopTrackingTouch(v: SeekBar?) = Unit
                })
            })
        }
        slider("Animation energy", prefs.animation) { prefs.animation = it.coerceAtLeast(.25f); surface.refreshVisuals() }
        slider("Perspective depth", prefs.depth) { prefs.depth = it.coerceAtLeast(.1f); surface.refreshVisuals() }
        AlertDialog.Builder(this).setTitle("Spatial Home settings").setView(box)
            .setNeutralButton("Default Home") { _, _ -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .setNegativeButton("Close", null).show()
    }
}
