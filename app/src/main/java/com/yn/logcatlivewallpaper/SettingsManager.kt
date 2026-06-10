package com.yn.logcatlivewallpaper

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "logcat_wallpaper_prefs"

    data class Settings(
        val scrollSpeed: Float = 1.8f,
        val fontSizeSp: Int = 14,
        val logcatFormat: String = "threadtime",
        val permissionMethod: String = "none",
        val backgroundColor: String = "#FF000000",
        val backgroundImage: String = "",
        val fontPath: String = ""
    )

    val formats = listOf(
        "brief", "process", "tag", "thread", "raw", "time", "threadtime", "long"
    )

    val presetColors = listOf(
        "#FF000000" to "Black",
        "#FF1a1a2e" to "Navy",
        "#FF2d2d2d" to "Dark Gray",
        "#FF0d1117" to "GitHub Dark",
        "#FF1b2838" to "Steel",
        "#FF2b1b17" to "Dark Brown",
        "#FF003300" to "Dark Green",
        "#FF330000" to "Dark Red"
    )

    fun getSettings(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Settings(
            scrollSpeed = prefs.getFloat("scroll_speed", 1.8f),
            fontSizeSp = prefs.getInt("font_size", 14),
            logcatFormat = prefs.getString("logcat_format", "threadtime") ?: "threadtime",
            permissionMethod = prefs.getString("permission_method", "none") ?: "none",
            backgroundColor = prefs.getString("background_color", "#FF000000") ?: "#FF000000",
            backgroundImage = prefs.getString("background_image", "") ?: "",
            fontPath = prefs.getString("font_path", "") ?: ""
        )
    }

    fun saveSettings(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("scroll_speed", settings.scrollSpeed)
            .putInt("font_size", settings.fontSizeSp)
            .putString("logcat_format", settings.logcatFormat)
            .putString("permission_method", settings.permissionMethod)
            .putString("background_color", settings.backgroundColor)
            .putString("background_image", settings.backgroundImage)
            .putString("font_path", settings.fontPath)
            .apply()
    }

    fun observer(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
