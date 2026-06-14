/*
* Stores and loads app settings from shared preferences.
*/
package com.yn.logcatlivewallpaper.core

import android.content.Context
import android.content.SharedPreferences

object Preferences {
    private const val PREFS_NAME = "logcat_wallpaper_prefs"

    data class Settings(
        val scrollSpeed: Float = 1.8f,
        val scrollMode: String = SCROLL_MODE_SMOOTH,
        val logWidth: Float = 0f,
        val logHeight: Float = 0f,
        val logPositionX: Float = 0f,
        val logPositionY: Float = 0f,
        val logRotation: Float = 0f,
        val fontSizeSp: Int = 14,
        val logcatCommand: String = DEFAULT_LOGCAT_COMMAND,
        val permissionMethod: String = "none",
        val backgroundColor: String = "#FF000000",
        val backgroundImage: String = "",
        val backgroundImageName: String = "",
        val fontPath: String = FONT_CGA,
        val customFontName: String = "",
        val wrapWord: Boolean = false,
        val colorVerbose: String = DEFAULT_COLOR_VERBOSE,
        val colorDebug: String = DEFAULT_COLOR_DEBUG,
        val colorInfo: String = DEFAULT_COLOR_INFO,
        val colorWarning: String = DEFAULT_COLOR_WARNING,
        val colorError: String = DEFAULT_COLOR_ERROR,
        val colorFatal: String = DEFAULT_COLOR_FATAL,
        val colorSilent: String = DEFAULT_COLOR_SILENT
    )

    const val DEFAULT_LOGCAT_COMMAND = "logcat -c && logcat -v tag"
    const val SCROLL_MODE_SMOOTH = "smooth"
    const val SCROLL_MODE_TERMINAL = "terminal"
    const val FONT_CGA = "__cga__"
    const val FONT_DEFAULT = "__default__"
    const val FONT_UBUNTU = "__ubuntu__"
    const val DEFAULT_COLOR_VERBOSE = "#FF0000FF"
    const val DEFAULT_COLOR_DEBUG = "#FF00FF00"
    const val DEFAULT_COLOR_INFO = "#FFFFFFFF"
    const val DEFAULT_COLOR_WARNING = "#FF4F00A6"
    const val DEFAULT_COLOR_ERROR = "#FFFF0000"
    const val DEFAULT_COLOR_FATAL = "#FFFDFF09"
    const val DEFAULT_COLOR_SILENT = "#FF06C8B8"

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
        val savedCommand = prefs.getString("logcat_command", null)
        val command = when (savedCommand) {
            null -> DEFAULT_LOGCAT_COMMAND
            else -> savedCommand
        }
        return Settings(
            scrollSpeed = prefs.getFloat("scroll_speed", 1.8f),
            scrollMode = prefs.getString("scroll_mode", SCROLL_MODE_SMOOTH) ?: SCROLL_MODE_SMOOTH,
            logWidth = prefs.getFloat("log_width", 0f),
            logHeight = prefs.getFloat("log_height", 0f),
            logPositionX = prefs.getFloat("log_position_x", 0f),
            logPositionY = prefs.getFloat("log_position_y", 0f),
            logRotation = prefs.getFloat("log_rotation", 0f),
            fontSizeSp = prefs.getInt("font_size", 14),
            logcatCommand = command,
            permissionMethod = prefs.getString("permission_method", "none") ?: "none",
            backgroundColor = prefs.getString("background_color", "#FF000000") ?: "#FF000000",
            backgroundImage = prefs.getString("background_image", "") ?: "",
            backgroundImageName = prefs.getString("background_image_name", "") ?: "",
            fontPath = when (val savedFontPath = prefs.getString("font_path", FONT_CGA)) {
                null, "" -> FONT_CGA
                "__thin__" -> FONT_DEFAULT
                else -> savedFontPath
            },
            customFontName = prefs.getString("custom_font_name", "") ?: "",
            wrapWord = prefs.getBoolean("wrap_word", false),
            colorVerbose = prefs.getString("color_verbose", DEFAULT_COLOR_VERBOSE) ?: DEFAULT_COLOR_VERBOSE,
            colorDebug = prefs.getString("color_debug", DEFAULT_COLOR_DEBUG) ?: DEFAULT_COLOR_DEBUG,
            colorInfo = prefs.getString("color_info", DEFAULT_COLOR_INFO) ?: DEFAULT_COLOR_INFO,
            colorWarning = prefs.getString("color_warning", DEFAULT_COLOR_WARNING) ?: DEFAULT_COLOR_WARNING,
            colorError = prefs.getString("color_error", DEFAULT_COLOR_ERROR) ?: DEFAULT_COLOR_ERROR,
            colorFatal = prefs.getString("color_fatal", DEFAULT_COLOR_FATAL) ?: DEFAULT_COLOR_FATAL,
            colorSilent = prefs.getString("color_silent", DEFAULT_COLOR_SILENT) ?: DEFAULT_COLOR_SILENT
        )
    }

    fun saveSettings(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("scroll_speed", settings.scrollSpeed)
            .putString("scroll_mode", settings.scrollMode)
            .putFloat("log_width", settings.logWidth)
            .putFloat("log_height", settings.logHeight)
            .putFloat("log_position_x", settings.logPositionX)
            .putFloat("log_position_y", settings.logPositionY)
            .putFloat("log_rotation", settings.logRotation)
            .putInt("font_size", settings.fontSizeSp)
            .putString("logcat_command", settings.logcatCommand)
            .putString("permission_method", settings.permissionMethod)
            .putString("background_color", settings.backgroundColor)
            .putString("background_image", settings.backgroundImage)
            .putString("background_image_name", settings.backgroundImageName)
            .putString("font_path", settings.fontPath)
            .putString("custom_font_name", settings.customFontName)
            .putBoolean("wrap_word", settings.wrapWord)
            .putString("color_verbose", settings.colorVerbose)
            .putString("color_debug", settings.colorDebug)
            .putString("color_info", settings.colorInfo)
            .putString("color_warning", settings.colorWarning)
            .putString("color_error", settings.colorError)
            .putString("color_fatal", settings.colorFatal)
            .putString("color_silent", settings.colorSilent)
            .commit()
    }

    fun observer(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
