/*
* Formats settings labels and normalizes color values.
*/
package com.yassernull.logcatlivewallpaper.utils

import android.content.Context
import com.yassernull.logcatlivewallpaper.R
import com.yassernull.logcatlivewallpaper.core.Preferences
import java.io.File

fun normalizeHex(value: String): String? {
  val raw = value.trim().removePrefix("#")
  val argb =
    when (raw.length) {
      6 -> "FF$raw"
      8 -> raw
      else -> return null
    }
  if (!argb.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
  return "#${argb.uppercase()}"
}

fun defaultColorForTarget(target: String): String = when (target) {
  "verbose" -> Preferences.DEFAULT_COLOR_VERBOSE
  "debug" -> Preferences.DEFAULT_COLOR_DEBUG
  "info" -> Preferences.DEFAULT_COLOR_INFO
  "warning" -> Preferences.DEFAULT_COLOR_WARNING
  "error" -> Preferences.DEFAULT_COLOR_ERROR
  "fatal" -> Preferences.DEFAULT_COLOR_FATAL
  "silent" -> Preferences.DEFAULT_COLOR_SILENT
  else -> "#FF000000"
}

fun fontLabel(
  context: Context,
  fontPath: String,
  customFontName: String,
): String = when (fontPath) {
  Preferences.FONT_CGA -> context.getString(R.string.font_int10h)
  Preferences.FONT_DEFAULT -> context.getString(R.string.font_default)
  Preferences.FONT_UBUNTU -> context.getString(R.string.font_ubuntu_bold)
  else ->
    customFontName.ifBlank {
      File(fontPath).name.ifBlank { context.getString(R.string.font_custom_fallback) }
    }
}

fun imageLabel(
  context: Context,
  backgroundImage: String,
  backgroundImageName: String,
): String = if (backgroundImage.isNotEmpty()) {
  backgroundImageName.ifBlank { File(backgroundImage).name }
} else {
  context.getString(R.string.settings_no_background_image)
}

fun videoLabel(
  context: Context,
  wallpaperVideo: String,
  wallpaperVideoName: String,
): String = if (wallpaperVideo.isNotEmpty()) {
  wallpaperVideoName.ifBlank { File(wallpaperVideo).name }
} else {
  context.getString(R.string.settings_no_wallpaper_video)
}

fun permissionLabel(context: Context, permission: String): String = when (permission) {
  "none" -> context.getString(R.string.permission_none)
  "shizuku" -> context.getString(R.string.permission_shizuku)
  "root" -> context.getString(R.string.permission_root)
  else -> permission.replaceFirstChar { it.uppercase() }
}

fun languageLabel(language: String): String = when (language) {
  "ar" -> "العربية"
  "fr" -> "Français"
  "hi" -> "हिन्दी"
  "zh" -> "中文"
  "ja" -> "日本語"
  "es" -> "Español"
  else -> "English"
}
