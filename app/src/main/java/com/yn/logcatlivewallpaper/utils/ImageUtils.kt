package com.yn.logcatlivewallpaper.utils

import java.io.File

object ImageUtils {
  fun isGif(file: File): Boolean {
    if (!file.exists() || !file.isFile) return false
    if (file.name.endsWith(".gif", ignoreCase = true)) return true
    if (file.length() < 6) return false
    return runCatching {
      file.inputStream().use { stream ->
        val header = ByteArray(6)
        val read = stream.read(header)
        if (read == 6) {
          val s = String(header, Charsets.US_ASCII)
          s == "GIF89a" || s == "GIF87a"
        } else {
          false
        }
      }
    }.getOrDefault(false)
  }

  fun isGif(path: String): Boolean {
    if (path.isEmpty()) return false
    return isGif(File(path))
  }
}
