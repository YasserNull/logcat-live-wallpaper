/*
* Runs the live wallpaper engine and logcat reader.
*/
package com.yn.logcatlivewallpaper.service

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.yn.logcatlivewallpaper.core.LogCatRenderer
import com.yn.logcatlivewallpaper.core.PermissionManager
import com.yn.logcatlivewallpaper.core.Preferences
import java.io.BufferedReader
import java.io.InputStreamReader

class LogCatWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LogCatEngine()

    inner class LogCatEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val renderer = LogCatRenderer(this@LogCatWallpaperService)
        private var logcatHandle: PermissionManager.LogcatHandle? = null
        private var readerThread: Thread? = null

        @Volatile
        private var running = false
        @Volatile
        private var visible = false
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (running && visible) {
                    try {
                        if (readerThread?.isAlive != true) {
                            startLogcatReader()
                        }
                        drawFrame(frameTimeNanos)
                    } catch (_: Exception) {
                    } finally {
                        if (running && visible) {
                            Choreographer.getInstance().postFrameCallback(this)
                        }
                    }
                }
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            Preferences.observer(this@LogCatWallpaperService).registerOnSharedPreferenceChangeListener(this)
            running = true
            if (isVisible) {
                showWallpaper()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer.updateSettings(Preferences.getSettings(this@LogCatWallpaperService))
            if (running && isVisible) {
                showWallpaper()
            }
        }

        override fun onDestroy() {
            running = false
            Preferences.observer(this@LogCatWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            stopLogcatReader()
            renderer.clear()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            renderer.updateSettings(Preferences.getSettings(this@LogCatWallpaperService))
            if (key == "permission_method" || key == "logcat_command") {
                stopLogcatReader()
                renderer.clear()
                if (visible) {
                    startLogcatReader()
                }
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.updateSettings(Preferences.getSettings(this@LogCatWallpaperService))
            if (isVisible && !visible) {
                showWallpaper()
            }
            drawFrame(System.nanoTime())
        }

        override fun onVisibilityChanged(v: Boolean) {
            super.onVisibilityChanged(v)
            if (v && running) {
                showWallpaper()
            } else {
                hideWallpaper()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            stopLogcatReader()
            renderer.clear()
            super.onSurfaceDestroyed(holder)
        }

        private fun showWallpaper() {
            visible = true
            renderer.resetFrameClock()
            startLogcatReader()
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        private fun hideWallpaper() {
            visible = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            stopLogcatReader()
            renderer.clear()
        }

        private fun drawFrame(frameTimeNanos: Long) {
            val surface = surfaceHolder ?: return
            val canvas = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    surface.lockHardwareCanvas()
                } else {
                    surface.lockCanvas()
                }
            } catch (_: Exception) {
                return
            } ?: return
            try {
                renderer.draw(canvas, frameTimeNanos)
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {}
            }
        }

        private fun startLogcatReader() {
            if (!running || !visible || readerThread?.isAlive == true) return
            readerThread = Thread {
                try {
                    val handle = PermissionManager.startLogcat(this@LogCatWallpaperService) ?: return@Thread
                    logcatHandle = handle
                    val reader = BufferedReader(InputStreamReader(handle.inputStream))
                    var line: String? = null
                    while (running && visible && reader.readLine().also { line = it } != null) {
                        line?.let { ln ->
                            if (ln.isNotEmpty()) {
                                renderer.enqueueLine(ln)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (running && visible) {
                        renderer.clear()
                        renderer.enqueueLine("Logcat error: ${e.message}")
                        renderer.enqueueLine("Open app and select Shizuku or Root")
                        renderer.enqueueLine("in Settings to activate logcat access")
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun stopLogcatReader() {
            logcatHandle?.destroy()
            logcatHandle = null
            readerThread?.interrupt()
            readerThread = null
        }
    }
}
