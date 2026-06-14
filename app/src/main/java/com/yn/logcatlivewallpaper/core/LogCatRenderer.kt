/*
* Renders logcat lines and wallpaper background on a canvas.
*/
package com.yn.logcatlivewallpaper.core

import android.content.Context
import android.graphics.*
import java.util.ArrayDeque

class LogCatRenderer(private val context: Context) {
    private class LineEntry(val text: String, val color: Int) {
        var wrapped: List<String>? = null
        var wrapWidth: Float = -1f
    }

    private val lines = mutableListOf<LineEntry>()
    private val maxLines = 50
    private val pendingEntries = ArrayDeque<LineEntry>()
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
        
        // Reset wrap cache on settings change (font/size change)
        synchronized(lines) {
            for (entry in lines) {
                entry.wrapped = null
                entry.wrapWidth = -1f
            }
        }
    }

    private fun loadTypeface(path: String): Typeface {
        return when (path) {
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
            else -> if (path.isNotEmpty()) {
                runCatching { Typeface.createFromFile(path) }.getOrElse { Typeface.DEFAULT }
            } else {
                Typeface.DEFAULT
            }
        }
    }

    fun enqueueLine(line: String) {
        synchronized(lines) {
            while (pendingEntries.size >= maxPendingLines) {
                pendingEntries.removeFirst()
            }
            pendingEntries.addLast(LineEntry(line, lineColor(line)))
        }
    }

    private fun lineColor(line: String): Int {
        val priority = Regex("(?:^|\\s)([VDIWEFS])(?:/|\\s)")
            .find(line)?.groupValues?.getOrNull(1)
        return when (priority) {
            "V" -> Color.parseColor(settings.colorVerbose)
            "D" -> Color.parseColor(settings.colorDebug)
            "I" -> Color.parseColor(settings.colorInfo)
            "W" -> Color.parseColor(settings.colorWarning)
            "E" -> Color.parseColor(settings.colorError)
            "F" -> Color.parseColor(settings.colorFatal)
            "S" -> Color.parseColor(settings.colorSilent)
            else -> Color.parseColor(settings.colorInfo)
        }
    }

    private fun logArea(canvasWidth: Float, canvasHeight: Float): RectF {
        val fullscreenTolerance = 96f * context.resources.displayMetrics.density
        val width = if (settings.logWidth > 0f && canvasWidth - settings.logWidth > fullscreenTolerance) {
            settings.logWidth
        } else {
            canvasWidth
        }
        val height = if (settings.logHeight > 0f && canvasHeight - settings.logHeight > fullscreenTolerance) {
            settings.logHeight
        } else {
            canvasHeight
        }
        val clampedWidth = width.coerceIn(1f, canvasWidth)
        val clampedHeight = height.coerceIn(1f, canvasHeight)
        val x = if (clampedWidth == canvasWidth) 0f else settings.logPositionX.coerceIn(0f, canvasWidth - clampedWidth)
        val y = if (clampedHeight == canvasHeight) 0f else settings.logPositionY.coerceIn(0f, canvasHeight - clampedHeight)
        return RectF(x, y, x + clampedWidth, y + clampedHeight)
    }

    private fun appendNextPendingLine(maxWidth: Float, maxVisibleLines: Int, force: Boolean = false) {
        if (lineHeight <= 0f || (!force && scrollOffset < 0f) || pendingEntries.isEmpty()) return

        val entry = pendingEntries.removeFirst()
        
        val count = if (settings.wrapWord && maxWidth > 0) {
            getWrappedLines(entry, maxWidth).size
        } else {
            1
        }

        while (lines.size >= maxVisibleLines) {
            lines.removeAt(0)
        }
        lines.add(entry)
        scrollOffset = if (settings.scrollMode == Preferences.SCROLL_MODE_TERMINAL) {
            0f
        } else {
            -(count * lineHeight)
        }
    }

    private fun getWrappedLines(entry: LineEntry, maxWidth: Float): List<String> {
        if (entry.wrapWidth == maxWidth && entry.wrapped != null) {
            return entry.wrapped!!
        }
        val wrapped = wrapText(entry.text, maxWidth)
        entry.wrapped = wrapped
        entry.wrapWidth = maxWidth
        return wrapped
    }

    fun draw(canvas: Canvas, frameTimeNanos: Long = System.nanoTime()) {
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
            bgPaint.color = Color.parseColor(settings.backgroundColor)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        }

        // Scrolling
        if (settings.scrollMode == Preferences.SCROLL_MODE_SMOOTH && scrollOffset < 0f) {
            val step = (lineHeight * settings.scrollSpeed * deltaTime * 1.5f).coerceAtLeast(0.1f * deltaTime)
            scrollOffset += minOf(-scrollOffset, step)
        } else if (settings.scrollMode == Preferences.SCROLL_MODE_TERMINAL) {
            scrollOffset = 0f
            terminalScrollProgress += lineHeight * settings.scrollSpeed * deltaTime * 1.5f
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
            val maxVisibleLines = if (lineHeight > 0f) {
                maxOf(maxLines, kotlin.math.ceil(contentHeight / lineHeight).toInt() + 4)
            } else {
                maxLines
            }
            
            if (settings.scrollMode == Preferences.SCROLL_MODE_TERMINAL) {
                while (terminalScrollProgress >= lineHeight && pendingEntries.isNotEmpty()) {
                    terminalScrollProgress -= lineHeight
                    appendNextPendingLine(maxWidth, maxVisibleLines, force = true)
                }
            } else {
                terminalScrollProgress = 0f
                appendNextPendingLine(maxWidth, maxVisibleLines)
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

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val result = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
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
            scrollOffset = 0f
            terminalScrollProgress = 0f
        }
    }
}
