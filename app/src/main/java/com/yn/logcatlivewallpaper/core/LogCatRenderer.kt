/*
* Renders logcat lines and wallpaper background on a canvas.
*/
package com.yn.logcatlivewallpaper.core

import android.content.Context
import android.graphics.*

class LogCatRenderer(
  private val context: Context,
) {
  private class LineEntry(
    val text: String,
    val color: Int,
  ) {
    var wrapped: List<String>? = null
    var wrapWidth: Float = -1f
  }

  private val lines = mutableListOf<LineEntry>()
  private val maxLines = 50
  private val pendingEntries = java.util.concurrent.ConcurrentLinkedQueue<LineEntry>()
  private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
  private val maxPendingLines = 50

  private val textPaint = Paint().apply { isAntiAlias = false }
  private val bgPaint = Paint()
  private var bgBitmap: Bitmap? = null
  private var lastBgPath = ""
  private var lastFontPath = ""
  private var lineHeight = 0f
  private var scrollOffset = 0f
  private var terminalScrollProgress = 0f
  private var lastFrameTimeNanos = 0L
  private var parsedBackgroundColor = Color.BLACK

  private val priorityRegex = Regex("(?:^|\\s)([VDIWEFS])(?:/|\\s)")

  private var parsedColorVerbose = Color.BLUE
  private var parsedColorDebug = Color.GREEN
  private var parsedColorInfo = Color.WHITE
  private var parsedColorWarning = Color.YELLOW
  private var parsedColorError = Color.RED
  private var parsedColorFatal = Color.RED
  private var parsedColorSilent = Color.CYAN

  private var settings: Preferences.Settings = Preferences.getSettings(context)

  init {
    updateSettings(settings)
  }

  fun updateSettings(newSettings: Preferences.Settings) {
    settings = newSettings
    if (settings.fontPath != lastFontPath) {
      textPaint.typeface = loadTypeface(settings.fontPath)
      lastFontPath = settings.fontPath
    }
    textPaint.textSize = settings.fontSizeSp * context.resources.displayMetrics.density
    lineHeight = textPaint.textSize * 1.25f

    parsedBackgroundColor = try {
      Color.parseColor(settings.backgroundColor)
    } catch (_: Exception) {
      Color.BLACK
    }

    parsedColorVerbose = parseColorOrDefault(settings.colorVerbose, Preferences.DEFAULT_COLOR_VERBOSE)
    parsedColorDebug = parseColorOrDefault(settings.colorDebug, Preferences.DEFAULT_COLOR_DEBUG)
    parsedColorInfo = parseColorOrDefault(settings.colorInfo, Preferences.DEFAULT_COLOR_INFO)
    parsedColorWarning = parseColorOrDefault(settings.colorWarning, Preferences.DEFAULT_COLOR_WARNING)
    parsedColorError = parseColorOrDefault(settings.colorError, Preferences.DEFAULT_COLOR_ERROR)
    parsedColorFatal = parseColorOrDefault(settings.colorFatal, Preferences.DEFAULT_COLOR_FATAL)
    parsedColorSilent = parseColorOrDefault(settings.colorSilent, Preferences.DEFAULT_COLOR_SILENT)

    // Reset wrap cache on settings change (font/size change)
    synchronized(lines) {
      for (entry in lines) {
        entry.wrapped = null
        entry.wrapWidth = -1f
      }
    }
  }

  private fun parseColorOrDefault(colorStr: String, defaultColor: String): Int = try {
    Color.parseColor(colorStr)
  } catch (_: Exception) {
    try {
      Color.parseColor(defaultColor)
    } catch (_: Exception) {
      Color.BLACK
    }
  }

  private fun loadTypeface(path: String): Typeface = when (path) {
    Preferences.FONT_CGA, "" -> {
      Typeface.createFromAsset(context.assets, "mx437_acer710_cga.ttf")
    }
    Preferences.FONT_DEFAULT -> Typeface.DEFAULT
    Preferences.FONT_UBUNTU -> {
      runCatching {
        Typeface.createFromAsset(context.assets, "ubuntu.ttf")
      }.getOrElse {
        Typeface.create("ubuntu", Typeface.NORMAL)
      }
    }
    else ->
      if (path.isNotEmpty()) {
        runCatching { Typeface.createFromFile(path) }.getOrElse { Typeface.DEFAULT }
      } else {
        Typeface.DEFAULT
      }
  }

  fun enqueueLine(line: String) {
    val color = lineColor(line)
    pendingEntries.add(LineEntry(line, color))
    if (pendingCount.incrementAndGet() > maxPendingLines) {
      pendingEntries.poll()
      pendingCount.decrementAndGet()
    }
  }

  private fun lineColor(line: String): Int {
    val priority =
      priorityRegex
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
    return when (priority) {
      "V" -> parsedColorVerbose
      "D" -> parsedColorDebug
      "I" -> parsedColorInfo
      "W" -> parsedColorWarning
      "E" -> parsedColorError
      "F" -> parsedColorFatal
      "S" -> parsedColorSilent
      else -> parsedColorInfo
    }
  }

  private fun logArea(
    canvasWidth: Float,
    canvasHeight: Float,
  ): RectF {
    val fullscreenTolerance = 96f * context.resources.displayMetrics.density
    val width =
      if (settings.logWidth > 0f && canvasWidth - settings.logWidth > fullscreenTolerance) {
        settings.logWidth
      } else {
        canvasWidth
      }
    val height =
      if (settings.logHeight > 0f && canvasHeight - settings.logHeight > fullscreenTolerance) {
        settings.logHeight
      } else {
        canvasHeight
      }
    val clampedWidth = width.coerceIn(1f, canvasWidth)
    val clampedHeight = height.coerceIn(1f, canvasHeight)
    val x = if (clampedWidth == canvasWidth) 0f else settings.logPositionX.coerceIn(0f, canvasWidth - clampedWidth)
    val y =
      if (clampedHeight ==
        canvasHeight
      ) {
        0f
      } else {
        settings.logPositionY.coerceIn(0f, canvasHeight - clampedHeight)
      }
    return RectF(x, y, x + clampedWidth, y + clampedHeight)
  }

  private fun appendNextPendingLine(
    maxWidth: Float,
    maxVisibleLines: Int,
    force: Boolean = false,
  ) {
    if (lineHeight <= 0f || (!force && scrollOffset < 0f) || pendingEntries.isEmpty()) return

    val currentPendingSize = pendingCount.get()
    val batchSize = when {
      currentPendingSize > 30 -> 4
      currentPendingSize > 15 -> 2
      else -> 1
    }

    var totalLinesAdded = 0
    var totalHeightAdded = 0f

    for (i in 0 until batchSize) {
      val entry = pendingEntries.poll() ?: break
      pendingCount.decrementAndGet()
      val count =
        if (settings.wrapWord && maxWidth > 0) {
          getWrappedLines(entry, maxWidth).size
        } else {
          1
        }
      while (lines.size >= maxVisibleLines) {
        lines.removeAt(0)
      }
      lines.add(entry)
      totalLinesAdded++
      totalHeightAdded += count * lineHeight
    }

    if (totalLinesAdded > 0) {
      scrollOffset =
        if (settings.scrollMode == Preferences.SCROLL_MODE_TERMINAL) {
          0f
        } else {
          -totalHeightAdded
        }
    }
  }

  private fun getWrappedLines(
    entry: LineEntry,
    maxWidth: Float,
  ): List<String> {
    if (entry.wrapWidth == maxWidth && entry.wrapped != null) {
      return entry.wrapped!!
    }
    val wrapped = wrapText(entry.text, maxWidth)
    entry.wrapped = wrapped
    entry.wrapWidth = maxWidth
    return wrapped
  }

  fun draw(
    canvas: Canvas,
    frameTimeNanos: Long = System.nanoTime(),
  ) {
    val w = canvas.width.toFloat()
    val h = canvas.height.toFloat()

    val deltaTime = if (lastFrameTimeNanos == 0L) 0.016f else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
    lastFrameTimeNanos = frameTimeNanos

    // Background
    if (settings.backgroundImage.isNotEmpty()) {
      if (settings.backgroundImage != lastBgPath) {
        bgBitmap = BitmapFactory.decodeFile(settings.backgroundImage)
        lastBgPath = settings.backgroundImage
      }
      bgBitmap?.let { bm ->
        val scale = maxOf(w / bm.width, h / bm.height)
        val srcW = (w / scale).toInt()
        val srcH = (h / scale).toInt()
        val srcX = (bm.width - srcW) / 2
        val srcY = (bm.height - srcH) / 2
        val src = Rect(srcX, srcY, srcX + srcW, srcY + srcH)
        val dst = Rect(0, 0, w.toInt(), h.toInt())
        canvas.drawBitmap(bm, src, dst, null)
      }
    } else {
      bgPaint.color = parsedBackgroundColor
      canvas.drawRect(0f, 0f, w, h, bgPaint)
    }

    synchronized(lines) {
      val area = logArea(w, h)
      val padding = 8f
      val fontMetrics = textPaint.fontMetrics
      val topInset = padding + kotlin.math.max(0f, -fontMetrics.ascent - lineHeight)
      val bottomInset = padding + kotlin.math.max(0f, fontMetrics.descent)
      val contentWidth = (area.width() - 2 * padding).coerceAtLeast(1f)
      val contentHeight = (area.height() - topInset - bottomInset).coerceAtLeast(lineHeight)
      val maxWidth = contentWidth.coerceAtLeast(100f)
      val maxVisibleLines =
        if (lineHeight > 0f) {
          maxOf(maxLines, kotlin.math.ceil(contentHeight / lineHeight).toInt() + 4)
        } else {
          maxLines
        }

      if (settings.scrollMode == Preferences.SCROLL_MODE_SMOOTH) {
        val currentPendingSize = pendingCount.get()
        val speedFactor = if (currentPendingSize > 10) (currentPendingSize / 5f).coerceAtMost(5f) else 1f
        var step = (lineHeight * settings.scrollSpeed * deltaTime * 1.5f * speedFactor).coerceAtLeast(0.1f * deltaTime)

        while (step > 0f && (scrollOffset < 0f || pendingEntries.isNotEmpty())) {
          if (scrollOffset < 0f) {
            val remaining = -scrollOffset
            if (step >= remaining) {
              scrollOffset = 0f
              step -= remaining
            } else {
              scrollOffset += step
              step = 0f
            }
          } else {
            appendNextPendingLine(maxWidth, maxVisibleLines)
            if (scrollOffset >= 0f) {
              break
            }
          }
        }
      } else if (settings.scrollMode == Preferences.SCROLL_MODE_TERMINAL) {
        scrollOffset = 0f
        terminalScrollProgress += lineHeight * settings.scrollSpeed * deltaTime * 1.5f
        while (terminalScrollProgress >= lineHeight && pendingEntries.isNotEmpty()) {
          terminalScrollProgress -= lineHeight
          appendNextPendingLine(maxWidth, maxVisibleLines, force = true)
        }
      }

      removeOffscreenLines(area.height())

      if (lines.isEmpty()) {
        scrollOffset = 0f
        return
      }

      canvas.save()
      canvas.rotate(settings.logRotation, area.centerX(), area.centerY())
      canvas.clipRect(area.left + padding, area.top + topInset, area.right - padding, area.bottom - bottomInset)
      canvas.translate(area.left, area.top + topInset)
      var y = contentHeight - scrollOffset
      for (idx in lines.indices.reversed()) {
        if (y < -lineHeight) break

        val entry = lines[idx]
        textPaint.color = entry.color

        if (settings.wrapWord) {
          val wrapped = getWrappedLines(entry, maxWidth)
          for (wIdx in wrapped.indices.reversed()) {
            if (y <= contentHeight + lineHeight && y >= -lineHeight) {
              canvas.drawText(wrapped[wIdx], padding, y, textPaint)
            }
            if (wIdx > 0) y -= lineHeight
          }
        } else {
          if (y <= contentHeight + lineHeight && y >= -lineHeight) {
            canvas.drawText(entry.text, padding, y, textPaint)
          }
        }
        y -= lineHeight
      }
      canvas.restore()
    }
  }

  private fun removeOffscreenLines(canvasHeight: Float) {
    if (lineHeight <= 0f || lines.isEmpty()) return
    while (lines.isNotEmpty()) {
      val oldestIndex = 0
      val newestToOldestOffset = lines.lastIndex - oldestIndex
      val oldestY = canvasHeight - scrollOffset - newestToOldestOffset * lineHeight
      if (oldestY >= -lineHeight) break
      lines.removeAt(oldestIndex)
    }
  }

  fun resetFrameClock() {
    lastFrameTimeNanos = 0L
  }

  private fun wrapText(
    text: String,
    maxWidth: Float,
  ): List<String> {
    val words = text.split(" ")
    val result = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
      val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
      if (textPaint.measureText(testLine) <= maxWidth) {
        currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
      } else {
        if (currentLine.isNotEmpty()) {
          result.add(currentLine.toString())
          currentLine = StringBuilder(word)
        } else {
          var start = 0
          while (start < word.length) {
            val count = textPaint.breakText(word, start, word.length, true, maxWidth, null)
            if (count <= 0) break
            result.add(word.substring(start, start + count))
            start += count
          }
        }
      }
    }
    if (currentLine.isNotEmpty()) result.add(currentLine.toString())
    return if (result.isEmpty()) listOf(text) else result
  }

  fun clear() {
    synchronized(lines) {
      lines.clear()
      pendingEntries.clear()
      pendingCount.set(0)
      scrollOffset = 0f
      terminalScrollProgress = 0f
    }
  }
}
