/*
* Renders logcat lines and wallpaper background on a canvas.
*/
package com.yn.logcatlivewallpaper.core

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.yn.logcatlivewallpaper.utils.ImageUtils
import java.io.File

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

  private val lines = ArrayDeque<LineEntry>()
  private val maxLines = 50
  private val pendingEntries = java.util.concurrent.ConcurrentLinkedQueue<LineEntry>()
  private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
  private val maxPendingLines = 100

  @Volatile
  private var isAsleep = false
  private var wakeupGraceFrames = 0

  private val textPaint = Paint().apply {
    isAntiAlias = true
    isSubpixelText = true
  }
  private val bgPaint = Paint()
  private var bgBitmap: Bitmap? = null
  private var animatedDrawable: Drawable? = null

  @Suppress("DEPRECATION")
  private var movie: Movie? = null
  private var movieBitmap: Bitmap? = null
  private var movieCanvas: Canvas? = null
  private var mainHandler: Handler? = null
  private var lastBgPath = ""
  private var lastFontPath = ""

  private fun getHandler(): Handler {
    mainHandler?.let { return it }
    val looper = Looper.getMainLooper() ?: Looper.myLooper()
    val handler = if (looper != null) Handler(looper) else Handler(Looper.getMainLooper())
    mainHandler = handler
    return handler
  }

  private val drawableCallback = object : Drawable.Callback {
    override fun invalidateDrawable(who: Drawable) {}

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
      getHandler().postAtTime(what, who, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
      getHandler().removeCallbacks(what, who)
    }
  }

  private fun loadBackground(path: String) {
    releaseBackground()
    lastBgPath = path
    if (path.isEmpty()) return

    val file = File(path)
    if (!file.exists()) return

    if (ImageUtils.isGif(file)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
          val source = ImageDecoder.createSource(file)
          val drawable = ImageDecoder.decodeDrawable(source)
          if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.callback = drawableCallback
            if (!isAsleep) {
              drawable.start()
            }
            animatedDrawable = drawable
            return
          } else if (drawable is BitmapDrawable) {
            bgBitmap = drawable.bitmap
            return
          } else {
            animatedDrawable = drawable
            return
          }
        } catch (_: Throwable) {
          // Fall back to Movie or BitmapFactory
        }
      }

      try {
        @Suppress("DEPRECATION")
        val m = Movie.decodeFile(path)
        if (m != null && m.duration() > 0) {
          movie = m
          val mW = m.width().coerceAtLeast(1)
          val mH = m.height().coerceAtLeast(1)
          val bmp = Bitmap.createBitmap(mW, mH, Bitmap.Config.ARGB_8888)
          movieBitmap = bmp
          movieCanvas = Canvas(bmp)
          return
        }
      } catch (_: Throwable) {
        // Fall back to BitmapFactory
      }
    }

    try {
      bgBitmap = BitmapFactory.decodeFile(path)
    } catch (_: Throwable) {
      bgBitmap = null
    }
  }

  private fun releaseBackground() {
    (animatedDrawable as? Animatable)?.stop()
    animatedDrawable?.callback = null
    animatedDrawable = null

    movie = null
    movieBitmap?.recycle()
    movieBitmap = null
    movieCanvas = null

    bgBitmap?.recycle()
    bgBitmap = null

    mainHandler?.removeCallbacksAndMessages(null)
  }
  private var lineHeight = 0f
  private var scrollOffset = 0f
  private var terminalScrollProgress = 0f
  private var lastFrameTimeNanos = 0L
  private var smoothedSpeedMultiplier = 1.0f
  private var parsedBackgroundColor = Color.BLACK

  private var parsedColorVerbose = Color.BLUE
  private var parsedColorDebug = Color.GREEN
  private var parsedColorInfo = Color.WHITE
  private var parsedColorWarning = Color.YELLOW
  private var parsedColorError = Color.RED
  private var parsedColorFatal = Color.RED
  private var parsedColorSilent = Color.CYAN

  private val cachedLogArea = RectF()
  private val cachedBgSrcRect = Rect()
  private val cachedBgDstRect = Rect()
  private val cachedFontMetrics = Paint.FontMetrics()
  private var cachedTopInset = 0f
  private var cachedBottomInset = 0f

  private var settings: Preferences.Settings = Preferences.getSettings(context)

  init {
    updateSettings(settings)
  }

  fun updateSettings(newSettings: Preferences.Settings) {
    settings = newSettings
    if (settings.backgroundImage != lastBgPath) {
      loadBackground(settings.backgroundImage)
    }
    if (settings.fontPath != lastFontPath) {
      textPaint.typeface = loadTypeface(settings.fontPath)
      lastFontPath = settings.fontPath
    }
    textPaint.textSize = settings.fontSizeSp * context.resources.displayMetrics.density
    lineHeight = textPaint.textSize * 1.25f

    textPaint.getFontMetrics(cachedFontMetrics)
    val padding = 8f
    cachedTopInset = padding + maxOf(0f, -cachedFontMetrics.ascent - lineHeight)
    cachedBottomInset = padding + maxOf(0f, cachedFontMetrics.descent)

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
    val sanitized = if (line.length > 1000) line.substring(0, 1000) else line
    val color = lineColor(sanitized)
    val entry = LineEntry(sanitized, color)

    if (isAsleep) {
      synchronized(lines) {
        while (lines.size >= maxLines) {
          lines.removeFirst()
        }
        lines.add(entry)
      }
      return
    }

    pendingEntries.add(entry)
    if (pendingCount.incrementAndGet() > maxPendingLines) {
      if (pendingEntries.poll() != null) {
        pendingCount.decrementAndGet()
      }
    }
  }

  fun onSleep() {
    isAsleep = true
    (animatedDrawable as? Animatable)?.stop()
    mainHandler?.removeCallbacksAndMessages(null)
    synchronized(lines) {
      pendingEntries.clear()
      pendingCount.set(0)
      scrollOffset = 0f
      terminalScrollProgress = 0f
      lastFrameTimeNanos = 0L
    }
  }

  fun onWakeup() {
    isAsleep = false
    resetFrameClock()
    (animatedDrawable as? Animatable)?.start()
  }

  private fun extractPriority(line: String): Char? {
    val len = line.length
    var i = 0
    while (i < len) {
      val c = line[i]
      if (c == 'V' || c == 'D' || c == 'I' || c == 'W' || c == 'E' || c == 'F' || c == 'S') {
        val isStart = (i == 0 || line[i - 1].isWhitespace())
        if (isStart && i + 1 < len) {
          val next = line[i + 1]
          if (next == '/' || next.isWhitespace()) {
            return c
          }
        }
      }
      i++
    }
    return null
  }

  private fun lineColor(line: String): Int = when (extractPriority(line)) {
    'V' -> parsedColorVerbose
    'D' -> parsedColorDebug
    'I' -> parsedColorInfo
    'W' -> parsedColorWarning
    'E' -> parsedColorError
    'F' -> parsedColorFatal
    'S' -> parsedColorSilent
    else -> parsedColorInfo
  }

  private fun updateLogArea(
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
      if (clampedHeight == canvasHeight) {
        0f
      } else {
        settings.logPositionY.coerceIn(0f, canvasHeight - clampedHeight)
      }
    cachedLogArea.set(x, y, x + clampedWidth, y + clampedHeight)
    return cachedLogArea
  }

  private fun appendNextPendingLine(
    maxWidth: Float,
    maxVisibleLines: Int,
    force: Boolean = false,
  ) {
    if (lineHeight <= 0f || (!force && scrollOffset < 0f) || pendingEntries.isEmpty()) return

    val currentPendingSize = pendingCount.get()
    val batchSize = if (currentPendingSize > 40) 2 else 1

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
        lines.removeFirst()
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
  ): Boolean {
    val w = canvas.width.toFloat()
    val h = canvas.height.toFloat()

    val targetFrameDuration = 1f / 120f
    val rawDelta = if (lastFrameTimeNanos == 0L || wakeupGraceFrames > 0) {
      if (wakeupGraceFrames > 0) wakeupGraceFrames--
      targetFrameDuration
    } else {
      (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
    }
    val deltaTime = rawDelta.coerceIn(0.001f, 0.018f)
    lastFrameTimeNanos = frameTimeNanos

    // Background
    if (settings.backgroundImage.isNotEmpty()) {
      if (settings.backgroundImage != lastBgPath) {
        loadBackground(settings.backgroundImage)
      }

      bgPaint.color = parsedBackgroundColor
      canvas.drawRect(0f, 0f, w, h, bgPaint)

      val anim = animatedDrawable
      val mov = movie
      val bm = bgBitmap

      when {
        anim != null -> {
          val dWidth = anim.intrinsicWidth.toFloat()
          val dHeight = anim.intrinsicHeight.toFloat()
          if (dWidth > 0f && dHeight > 0f) {
            val scale = maxOf(w / dWidth, h / dHeight)
            val scaledW = dWidth * scale
            val scaledH = dHeight * scale
            val left = ((w - scaledW) / 2f).toInt()
            val top = ((h - scaledH) / 2f).toInt()
            anim.setBounds(left, top, (left + scaledW).toInt(), (top + scaledH).toInt())
            canvas.save()
            canvas.clipRect(0f, 0f, w, h)
            anim.draw(canvas)
            canvas.restore()
          } else {
            anim.setBounds(0, 0, w.toInt(), h.toInt())
            anim.draw(canvas)
          }
        }
        mov != null && movieBitmap != null && movieCanvas != null -> {
          val duration = mov.duration().let { if (it <= 0) 1000 else it }
          val relTime = (System.currentTimeMillis() % duration).toInt()
          mov.setTime(relTime)
          val mb = movieBitmap!!
          val mc = movieCanvas!!
          mb.eraseColor(Color.TRANSPARENT)
          mov.draw(mc, 0f, 0f)

          val scale = maxOf(w / mb.width, h / mb.height)
          val srcW = (w / scale).toInt()
          val srcH = (h / scale).toInt()
          val srcX = (mb.width - srcW) / 2
          val srcY = (mb.height - srcH) / 2
          cachedBgSrcRect.set(srcX, srcY, srcX + srcW, srcY + srcH)
          cachedBgDstRect.set(0, 0, w.toInt(), h.toInt())
          canvas.drawBitmap(mb, cachedBgSrcRect, cachedBgDstRect, null)
        }
        bm != null && !bm.isRecycled -> {
          val scale = maxOf(w / bm.width, h / bm.height)
          val srcW = (w / scale).toInt()
          val srcH = (h / scale).toInt()
          val srcX = (bm.width - srcW) / 2
          val srcY = (bm.height - srcH) / 2
          cachedBgSrcRect.set(srcX, srcY, srcX + srcW, srcY + srcH)
          cachedBgDstRect.set(0, 0, w.toInt(), h.toInt())
          canvas.drawBitmap(bm, cachedBgSrcRect, cachedBgDstRect, null)
        }
      }
    } else {
      if (lastBgPath.isNotEmpty()) {
        releaseBackground()
        lastBgPath = ""
      }
      bgPaint.color = parsedBackgroundColor
      canvas.drawRect(0f, 0f, w, h, bgPaint)
    }

    synchronized(lines) {
      val area = updateLogArea(w, h)
      val padding = 8f
      val topInset = cachedTopInset
      val bottomInset = cachedBottomInset
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
        val currentPending = pendingCount.get()
        val targetMultiplier = when {
          currentPending <= 3 -> 1.0f
          currentPending <= 20 -> 1.0f + (currentPending - 3) * 0.1f
          else -> 2.7f + (currentPending - 20) * 0.05f
        }.coerceIn(1.0f, 4.0f)

        val lerpFactor = (deltaTime * 8f).coerceIn(0.01f, 1.0f)
        smoothedSpeedMultiplier += (targetMultiplier - smoothedSpeedMultiplier) * lerpFactor

        var step = (lineHeight * settings.scrollSpeed * deltaTime * 1.5f * smoothedSpeedMultiplier).coerceAtLeast(0.1f * deltaTime)

        var iterations = 0
        while (step > 0f && (scrollOffset < 0f || pendingEntries.isNotEmpty()) && iterations < 3) {
          iterations++
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
        if (pendingEntries.isEmpty()) {
          terminalScrollProgress = 0f
        }
      }

      removeOffscreenLines(area.height())

      if (lines.isEmpty()) {
        scrollOffset = 0f
        return pendingEntries.isNotEmpty()
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

      return scrollOffset < -0.01f || pendingEntries.isNotEmpty()
    }
  }

  private fun removeOffscreenLines(canvasHeight: Float) {
    if (lineHeight <= 0f || lines.isEmpty()) return
    while (lines.isNotEmpty()) {
      val oldestIndex = 0
      val newestToOldestOffset = lines.lastIndex - oldestIndex
      val oldestY = canvasHeight - scrollOffset - newestToOldestOffset * lineHeight
      if (oldestY >= -lineHeight) break
      lines.removeFirst()
    }
  }

  fun resetFrameClock() {
    lastFrameTimeNanos = 0L
    smoothedSpeedMultiplier = 1.0f
    wakeupGraceFrames = 5
  }

  private fun wrapText(
    text: String,
    maxWidth: Float,
  ): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var currentLine = StringBuilder()
    var start = 0
    val len = text.length

    while (start < len) {
      var end = text.indexOf(' ', start)
      if (end == -1) end = len
      val word = text.substring(start, end)
      start = end + 1

      val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
      if (textPaint.measureText(testLine) <= maxWidth) {
        if (currentLine.isNotEmpty()) currentLine.append(' ')
        currentLine.append(word)
      } else {
        if (currentLine.isNotEmpty()) {
          result.add(currentLine.toString())
          currentLine = StringBuilder()
        }
        if (textPaint.measureText(word) <= maxWidth) {
          currentLine.append(word)
        } else {
          var charStart = 0
          while (charStart < word.length) {
            val count = textPaint.breakText(word, charStart, word.length, true, maxWidth, null)
            if (count <= 0) break
            result.add(word.substring(charStart, charStart + count))
            charStart += count
          }
        }
      }
    }
    if (currentLine.isNotEmpty()) {
      result.add(currentLine.toString())
    }
    return if (result.isEmpty()) listOf(text) else result
  }

  fun clear() {
    synchronized(lines) {
      lines.clear()
      pendingEntries.clear()
      pendingCount.set(0)
      scrollOffset = 0f
      terminalScrollProgress = 0f
      lastFrameTimeNanos = 0L
      smoothedSpeedMultiplier = 1.0f
      wakeupGraceFrames = 0
    }
  }
}
