/*
* Runs the live wallpaper engine and logcat reader.
*/
package com.yn.logcatlivewallpaper.service

import android.content.Context
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import com.yn.logcatlivewallpaper.core.LogCatRenderer
import com.yn.logcatlivewallpaper.core.PermissionManager
import com.yn.logcatlivewallpaper.core.Preferences
import java.io.BufferedReader
import java.io.InputStreamReader

class LogCatWallpaperService : WallpaperService() {
  override fun onCreateEngine(): Engine = LogCatEngine()

  inner class LogCatEngine :
    Engine(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val renderer = LogCatRenderer(this@LogCatWallpaperService)
    private var logcatHandle: PermissionManager.LogcatHandle? = null
    private var readerThread: Thread? = null
    private var cachedMaxRefreshRate = 120f

    @Volatile
    private var running = false

    @Volatile
    private var visible = false

    private val frameCallback =
      object : Choreographer.FrameCallback {
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
      cachedMaxRefreshRate = queryMaxDisplayRefreshRate()
      running = true
      startLogcatReader()
      if (isVisible) {
        showWallpaper()
      }
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      updateSurfaceFrameRate(holder)
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

    override fun onSharedPreferenceChanged(
      sharedPreferences: SharedPreferences?,
      key: String?,
    ) {
      renderer.updateSettings(Preferences.getSettings(this@LogCatWallpaperService))
      if (key == "permission" || key == "logcat_command") {
        stopLogcatReader()
        renderer.clear()
        startLogcatReader()
      }
    }

    override fun onSurfaceChanged(
      holder: SurfaceHolder,
      format: Int,
      width: Int,
      height: Int,
    ) {
      super.onSurfaceChanged(holder, format, width, height)
      updateSurfaceFrameRate(holder)
      if (isVisible && !visible) {
        showWallpaper()
      }
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
      super.onSurfaceDestroyed(holder)
    }

    private fun showWallpaper() {
      if (visible) return
      visible = true
      renderer.onWakeup()
      if (readerThread?.isAlive != true) {
        startLogcatReader()
      }
      Choreographer.getInstance().removeFrameCallback(frameCallback)
      Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun hideWallpaper() {
      visible = false
      Choreographer.getInstance().removeFrameCallback(frameCallback)
      renderer.onSleep()
    }

    private fun drawFrame(frameTimeNanos: Long) {
      val surface = surfaceHolder ?: return
      val canvas =
        try {
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
        } catch (_: Exception) {
        }
      }
    }

    private fun startLogcatReader() {
      if (!running || readerThread?.isAlive == true) return
      readerThread =
        Thread {
          try {
            val handle = PermissionManager.startLogcat(this@LogCatWallpaperService) ?: return@Thread
            logcatHandle = handle
            val reader = BufferedReader(InputStreamReader(handle.inputStream), 8192)
            var line: String? = null
            while (running && reader.readLine().also { line = it } != null) {
              line?.let { ln ->
                if (ln.isNotEmpty()) {
                  renderer.enqueueLine(ln)
                }
              }
            }
          } catch (e: Exception) {
            if (running) {
              renderer.clear()
              renderer.enqueueLine("Logcat error: ${e.message}")
              renderer.enqueueLine("Open app and select Shizuku or Root")
              renderer.enqueueLine("in Settings to activate logcat access")
            }
          }
        }.apply {
          name = "LogcatReaderThread"
          isDaemon = true
          start()
        }
    }

    private fun stopLogcatReader() {
      logcatHandle?.destroy()
      logcatHandle = null
      readerThread?.interrupt()
      readerThread = null
    }

    @Suppress("DEPRECATION")
    private fun queryMaxDisplayRefreshRate(): Float = try {
      val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        display ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
      } else {
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
      }

      val supported = display?.supportedModes?.map { it.refreshRate } ?: emptyList()
      val maxMode = supported.maxOrNull() ?: 0f
      val current = display?.refreshRate ?: 60f
      maxOf(maxMode, current, 120f)
    } catch (_: Exception) {
      120f
    }

    private fun updateSurfaceFrameRate(holder: SurfaceHolder) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        try {
          holder.surface?.let { surface ->
            if (surface.isValid) {
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                surface.setFrameRate(
                  cachedMaxRefreshRate,
                  Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                  Surface.CHANGE_FRAME_RATE_ALWAYS,
                )
              } else {
                surface.setFrameRate(
                  cachedMaxRefreshRate,
                  Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                )
              }
            }
          }
        } catch (_: Exception) {
        }
      }
    }
  }
}
