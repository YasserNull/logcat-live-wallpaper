/*
* Formats settings labels and normalizes color values.
*/
package com.yn.logcatlivewallpaper.utils

import android.content.Context
import com.yn.logcatlivewallpaper.R
import com.yn.logcatlivewallpaper.core.Preferences
import java.io.File

fun normalizeHex(value: String): String? {
    val raw = value.trim().removePrefix("#")
    val argb = when (raw.length) {
        6 -> "FF$raw"
        8 -> raw
        else -> return null
    }
    if (!argb.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#${argb.uppercase()}"
}

fun defaultColorForTarget(target: String): String {
    return when (target) {
        "verbose" -> Preferences.DEFAULT_COLOR_VERBOSE
        "debug" -> Preferences.DEFAULT_COLOR_DEBUG
        "info" -> Preferences.DEFAULT_COLOR_INFO
        "warning" -> Preferences.DEFAULT_COLOR_WARNING
        "error" -> Preferences.DEFAULT_COLOR_ERROR
        "fatal" -> Preferences.DEFAULT_COLOR_FATAL
        "silent" -> Preferences.DEFAULT_COLOR_SILENT
        else -> "#FF000000"
    }
}

fun fontLabel(context: Context, fontPath: String, customFontName: String): String {
    return when (fontPath) {
        Preferences.FONT_CGA -> context.getString(R.string.font_int10h)
        Preferences.FONT_DEFAULT -> context.getString(R.string.font_default)
        Preferences.FONT_UBUNTU -> context.getString(R.string.font_ubuntu_bold)
        else -> customFontName.ifBlank {
            File(fontPath).name.ifBlank { context.getString(R.string.font_custom_fallback) }
        }
    }
}

fun imageLabel(context: Context, backgroundImage: String, backgroundImageName: String): String {
    return if (backgroundImage.isNotEmpty()) {
        backgroundImageName.ifBlank { File(backgroundImage).name }
    } else {
        context.getString(R.string.settings_no_background_image)
    }
}

fun permissionLabel(permissionMethod: String): String {
    return permissionMethod.replaceFirstChar { it.uppercase() }
}
