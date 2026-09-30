package com.muhgoub.hud

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "muhgoub_hud_prefs"

        // ترتيب الـ 12 زر: Ping,Signal,Data,Bandwth / Packets,Core,Wi-Fi,BT Link / Hotspot,Light,Dark Mod,Active
        val TOOL_KEYS = arrayOf(
            "tool_ping", "tool_signal", "tool_data", "tool_bandwidth",
            "tool_packets", "tool_core", "tool_wifi", "tool_bt_link",
            "tool_hotspot", "tool_light", "tool_dark_mode", "tool_active"
        )

        private const val KEY_CORE_ALIGN = "core_align"     // bottom | mid | top | off
        private const val KEY_STATUS_MODE = "status_mode"   // prcs | fill | off
        private const val KEY_OVERLAY_EXPANDED = "overlay_expanded"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_APP_MODE = "app_mode"         // normal | turbo
        private const val KEY_PANEL_THEME = "panel_theme"   // dark | light | system
    }
    fun getPanelTheme(): String = prefs.getString(KEY_PANEL_THEME, "dark") ?: "dark"

fun setPanelTheme(value: String) {
    prefs.edit().putString(KEY_PANEL_THEME, value).apply()
}
    fun isToolOn(index: Int): Boolean = prefs.getBoolean(TOOL_KEYS[index], false)

    fun setToolOn(index: Int, on: Boolean) {
        prefs.edit().putBoolean(TOOL_KEYS[index], on).apply()
    }

    fun getCoreAlign(): String = prefs.getString(KEY_CORE_ALIGN, "top") ?: "top"

    fun setCoreAlign(value: String) {
        prefs.edit().putString(KEY_CORE_ALIGN, value).apply()
    }

    fun getStatusMode(): String = prefs.getString(KEY_STATUS_MODE, "fill") ?: "fill"

    fun setStatusMode(value: String) {
        prefs.edit().putString(KEY_STATUS_MODE, value).apply()
    }

    fun isOverlayExpanded(): Boolean = prefs.getBoolean(KEY_OVERLAY_EXPANDED, true)

    fun setOverlayExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_EXPANDED, expanded).apply()
    }

    fun getOverlayPosition(): Pair<Int, Int> =
        Pair(prefs.getInt(KEY_OVERLAY_X, 0), prefs.getInt(KEY_OVERLAY_Y, 100))

    fun setOverlayPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
    }

    fun getAppMode(): String = prefs.getString(KEY_APP_MODE, "normal") ?: "normal"

    fun setAppMode(mode: String) {
        prefs.edit().putString(KEY_APP_MODE, mode).apply()
    }
}
