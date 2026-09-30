package com.muhgoub.hud

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class HudApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Re-apply the last chosen Panel Theme (Dark / Light / System) to the app's
        // own night mode so the choice survives process restarts.
        when (PrefsManager(this).getPanelTheme()) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}
