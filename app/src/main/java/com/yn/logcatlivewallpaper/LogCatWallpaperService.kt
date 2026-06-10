package com.yn.logcatlivewallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.io.BufferedReader
import java.io.InputStreamReader

class LogCatWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LogCatEngine()

    inner class LogCatEngine : Engine() {
        private val lines = mutableListOf<String>()
        private val colors = mutableListOf<Int>()
        private val maxLines = 2000
        private var logcatHandle: com.yn.logcatlivewallpaper.PermissionManager.LogcatHandle? = null
        private var readerThread: Thread? = null

        @Volatile
        private var running = false
        @Volatile
        private var visible = false
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (running && visible) {
                    drawFrame()
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        private val textPaint = Paint().apply {
            isAntiAlias = false
        }
        private val bgPaint = Paint()
        private var bgBitmap: Bitmap? = null
        private var lastBgPath = ""
        private var lastFontPath = ""
        private var lineHeight = 0f
        @Volatile
        private var scrollOffset = 0f

        private fun loadTypeface(path: String): Typeface {
            return if (path.isNotEmpty()) {
                Typeface.createFromFile(path)
            } else {
                Typeface.createFromAsset(this@LogCatWallpaperService.assets, "mx437_acer710_cga.ttf")
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            val s = SettingsManager.getSettings(this@LogCatWallpaperService)
            textPaint.typeface = loadTypeface(s.fontPath)
            lastFontPath = s.fontPath
            running = true
            visible = true
            startLogcatReader()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        override fun onDestroy() {
            running = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            stopLogcatReader()
            super.onDestroy()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            val s = SettingsManager.getSettings(this@LogCatWallpaperService)
            if (s.fontPath != lastFontPath) {
                textPaint.typeface = loadTypeface(s.fontPath)
                lastFontPath = s.fontPath
            }
            textPaint.textSize = s.fontSizeSp * resources.displayMetrics.density
            lineHeight = textPaint.textSize * 1.25f
            drawFrame()
        }

        override fun onVisibilityChanged(v: Boolean) {
            super.onVisibilityChanged(v)
            visible = v
            if (v && running) {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } else {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }

        private fun lineColor(line: String): Int {
            val priority = Regex("(?:^|\\s)([VDIWE])(?:/|\\s)")
                .find(line)?.groupValues?.getOrNull(1)
            return when (priority) {
                "V" -> Color.rgb(128, 128, 128)
                "D" -> Color.rgb(66, 133, 244)
                "I" -> Color.rgb(0, 200, 0)
                "W" -> Color.rgb(255, 193, 7)
                "E" -> Color.rgb(255, 68, 68)
                else -> Color.rgb(0, 200, 0)
            }
        }

        private fun drawFrame() {
            val surface = surfaceHolder ?: return
            val canvas = surface.lockCanvas() ?: return
            try {
                val s = SettingsManager.getSettings(this@LogCatWallpaperService)
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()

                if (s.backgroundImage.isNotEmpty()) {
                    if (s.backgroundImage != lastBgPath) {
                        bgBitmap = BitmapFactory.decodeFile(s.backgroundImage)
                        lastBgPath = s.backgroundImage
                    }
                    bgBitmap?.let { bm ->
                        val scale = maxOf(w / bm.width, h / bm.height)
                        val srcW = (w / scale).toInt()
                        val srcH = (h / scale).toInt()
                        val srcX = (bm.width - srcW) / 2
                        val srcY = (bm.height - srcH) / 2
                        val src = android.graphics.Rect(srcX, srcY, srcX + srcW, srcY + srcH)
                        val dst = android.graphics.Rect(0, 0, w.toInt(), h.toInt())
                        canvas.drawBitmap(bm, src, dst, null)
                    }
                } else {
                    bgPaint.color = Color.parseColor(s.backgroundColor)
                    canvas.drawRect(0f, 0f, w, h, bgPaint)
                }

                if (scrollOffset < 0f) {
                    val step = (lineHeight * s.scrollSpeed * 0.02f).coerceAtLeast(0.1f)
                    scrollOffset += minOf(-scrollOffset, step)
                }

                synchronized(lines) {
                    while (lines.isNotEmpty()) {
                        val oldestY = h - scrollOffset - (lines.size - 1) * lineHeight
                        if (oldestY + lineHeight >= 0f) break
                        lines.removeAt(0)
                        colors.removeAt(0)
                    }

                    if (lines.isEmpty()) {
                        scrollOffset = 0f
                        return
                    }

                    var y = h - scrollOffset
                    for (idx in lines.indices.reversed()) {
                        if (y + lineHeight < 0f) break
                        if (y <= h + lineHeight) {
                            textPaint.color = colors[idx]
                            canvas.drawText(lines[idx], 8f, y, textPaint)
                        }
                        y -= lineHeight
                    }
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        }

        private fun startLogcatReader() {
            readerThread = Thread {
                try {
                    PermissionManager.clearLogcat(this@LogCatWallpaperService)
                    val handle = PermissionManager.startLogcat(this@LogCatWallpaperService) ?: return@Thread
                    logcatHandle = handle
                    val reader = BufferedReader(InputStreamReader(handle.inputStream))
                    var line: String? = null
                    while (running && reader.readLine().also { line = it } != null) {
                        line?.let { ln ->
                            if (ln.isEmpty()) return@let
                            synchronized(lines) {
                                lines.add(ln)
                                colors.add(lineColor(ln))
                                scrollOffset -= lineHeight
                                if (scrollOffset < -lineHeight * 3f) scrollOffset = -lineHeight * 3f
                                if (lines.size > maxLines) {
                                    val excess = lines.size - maxLines
                                    repeat(excess) {
                                        lines.removeAt(0)
                                        colors.removeAt(0)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    synchronized(lines) {
                        lines.clear()
                        colors.clear()
                        val msg = "Logcat error: ${e.message}"
                        lines.add(msg)
                        colors.add(Color.rgb(255, 68, 68))
                        lines.add("Open app and select Shizuku or Root")
                        colors.add(Color.rgb(255, 193, 7))
                        lines.add("in Settings to activate logcat access")
                        colors.add(Color.rgb(0, 200, 0))
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun stopLogcatReader() {
            logcatHandle?.destroy()
            readerThread?.interrupt()
        }
    }
}
