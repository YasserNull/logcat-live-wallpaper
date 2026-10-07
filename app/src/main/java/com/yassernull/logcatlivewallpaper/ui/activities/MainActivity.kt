/*
* Shows the main wallpaper preview and top level actions.
*/
package com.yassernull.logcatlivewallpaper.ui.activities

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.yassernull.logcatlivewallpaper.R
import com.yassernull.logcatlivewallpaper.core.LogCatRenderer
import com.yassernull.logcatlivewallpaper.core.PermissionManager
import com.yassernull.logcatlivewallpaper.core.Preferences
import com.yassernull.logcatlivewallpaper.service.LogCatWallpaperService
import com.yassernull.logcatlivewallpaper.ui.dialogs.WallpaperTargetDialog
import com.yassernull.logcatlivewallpaper.ui.theme.LogCatLiveWallpaperTheme
import com.yassernull.logcatlivewallpaper.utils.ApplyStatusBarColor
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
  private var currentLanguage = "en"

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(com.yassernull.logcatlivewallpaper.utils.LocaleHelper.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    currentLanguage = Preferences.getSettings(this).language
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    hideNavigationBar()
    setContent {
      LogCatLiveWallpaperTheme {
        ApplyStatusBarColor(MaterialTheme.colorScheme.surface)
        LaunchedEffect(Unit) {
          autoSelectPermissionMethod()
        }
        val statusBarColor = MaterialTheme.colorScheme.surface
        var showWallpaperDialog by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize()) {
            Toolbar(
              onSettingsClick = {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
              },
              onSetWallpaperClick = { showWallpaperDialog = true },
            )
            AndroidView(
              factory = { ctx -> LogCatPreviewView(ctx) },
              modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            )
          }
          Box(
            Modifier
              .fillMaxWidth()
              .windowInsetsTopHeight(WindowInsets.statusBars)
              .background(statusBarColor)
              .align(Alignment.TopCenter),
          )
        }
        // Android doesn't let a third-party app choose home/lock for a live wallpaper,
        // so every option opens the system picker, which shows the final screen choice.
        if (showWallpaperDialog) {
          WallpaperTargetDialog(
            onSetAsLockScreen = {
              showWallpaperDialog = false
              setWallpaper()
            },
            onSetAsHomeScreen = {
              showWallpaperDialog = false
              setWallpaper()
            },
            onSetAsHomeAndLockScreen = {
              showWallpaperDialog = false
              setWallpaper()
            },
            onDismiss = { showWallpaperDialog = false },
          )
        }
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideNavigationBar()
  }

  override fun onResume() {
    super.onResume()
    val savedLang = Preferences.getSettings(this).language
    if (savedLang != currentLanguage) {
      recreate()
    }
  }

  private fun hideNavigationBar() {
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility =
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      window.decorView.windowInsetsController?.let {
        it.hide(
          android.view.WindowInsets.Type
            .navigationBars(),
        )
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    }
  }

  private fun setWallpaper() {
    val intent =
      Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
          WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
          ComponentName(this@MainActivity, LogCatWallpaperService::class.java),
        )
      }
    runCatching { startActivity(intent) }
      .onFailure {
        startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
      }
  }

  private fun openSourceCode() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_source_code))))
  }

  private fun openDonate() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_donate))))
  }

  private fun autoSelectPermissionMethod() {
    PermissionManager.sanitizeSavedMethod(this)
    val preferredMethod = PermissionManager.preferredAvailableMethod()
    if (preferredMethod == "none") return

    val current = Preferences.getSettings(this)
    if (current.permission == preferredMethod) return

    val grantedNow =
      PermissionManager.activate(preferredMethod) { granted ->
        if (granted) {
          runOnUiThread {
            Preferences.saveSettings(
              this,
              Preferences.getSettings(this).copy(permission = preferredMethod),
            )
          }
        }
      }
    if (grantedNow) {
      Preferences.saveSettings(this, current.copy(permission = preferredMethod))
    }
  }

  @Composable
  private fun Toolbar(
    onSettingsClick: () -> Unit,
    onSetWallpaperClick: () -> Unit,
  ) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().statusBarsPadding().height(60.dp),
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          Modifier
            .weight(1f)
            .padding(horizontal = 12.dp)
            .align(Alignment.CenterVertically),
        ) {
          Text(
            text = stringResource(R.string.app_name),
            fontSize = 18.sp,
          )
        }
        Box {
          IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_set_wallpaper)) },
              onClick = {
                showMenu = false
                onSetWallpaperClick()
              },
              leadingIcon = { Icon(Icons.Default.Star, null) },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_settings)) },
              onClick = {
                showMenu = false
                onSettingsClick()
              },
              leadingIcon = { Icon(Icons.Default.Settings, null) },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_source_code)) },
              onClick = {
                showMenu = false
                openSourceCode()
              },
              leadingIcon = { Icon(Icons.Default.Code, null) },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_donate)) },
              onClick = {
                showMenu = false
                openDonate()
              },
              leadingIcon = { Icon(Icons.Default.Favorite, null) },
            )
          }
        }
      }
    }
  }
}

private class LogCatPreviewView(
  context: Context,
) : FrameLayout(context),
  SharedPreferences.OnSharedPreferenceChangeListener {
  private val renderer = LogCatRenderer(context)
  private var logcatHandle: PermissionManager.LogcatHandle? = null
  private var readerThread: Thread? = null
  private var running = false
  private var mediaPlayer: MediaPlayer? = null
  private var currentVideoPath = ""
  private var videoSurface: Surface? = null
  private var textureView: TextureView? = null
  private var currentVideoWidth = 0
  private var currentVideoHeight = 0

  private val canvasView = object : View(context) {
    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      renderer.draw(canvas)
    }
  }

  private val frameCallback =
    object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (running) {
          canvasView.invalidate()
          Choreographer.getInstance().postFrameCallback(this)
        }
      }
    }

  init {
    Preferences.observer(context).registerOnSharedPreferenceChangeListener(this)
    val settings = Preferences.getSettings(context)
    renderer.updateSettings(settings)

    setupViews(settings.wallpaperVideo)

    running = true
    resumeRendering()
    startLogcatReader()
  }

  private fun setupViews(videoPath: String) {
    removeAllViews()
    if (videoPath.isNotEmpty()) {
      val tv = TextureView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
          override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            videoSurface = Surface(surface)
            mediaPlayer?.setSurface(videoSurface)
            updateTextureTransform(width, height)
            if (mediaPlayer == null) {
              startVideoPlayback(videoPath)
            }
          }

          override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            updateTextureTransform(width, height)
          }

          override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            videoSurface?.release()
            videoSurface = null
            mediaPlayer?.setSurface(null)
            return true
          }

          override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
      }
      textureView = tv
      addView(tv)
    } else {
      stopVideoPlayback()
      textureView = null
    }

    canvasView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    addView(canvasView)
  }

  private fun updateTextureTransform(viewWidth: Int, viewHeight: Int) {
    val tv = textureView ?: return
    if (viewWidth == 0 || viewHeight == 0 || currentVideoWidth == 0 || currentVideoHeight == 0) return

    val viewRatio = viewWidth.toFloat() / viewHeight
    val videoRatio = currentVideoWidth.toFloat() / currentVideoHeight

    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
      scaleX = videoRatio / viewRatio
      scaleY = 1.0f
    } else {
      scaleX = 1.0f
      scaleY = viewRatio / videoRatio
    }

    val pivotX = viewWidth / 2f
    val pivotY = viewHeight / 2f

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, pivotX, pivotY)
    tv.setTransform(matrix)
  }

  private fun startVideoPlayback(videoPath: String) {
    val file = File(videoPath)
    if (!file.exists() || file.length() == 0L) {
      return
    }

    stopVideoPlayback()
    try {
      val mp = MediaPlayer().apply {
        videoSurface?.let { setSurface(it) }
        FileInputStream(file).use { fis ->
          setDataSource(fis.fd, 0, file.length())
        }
        isLooping = true
        setVolume(0f, 0f)
        setOnVideoSizeChangedListener { _, w, h ->
          if (w > 0 && h > 0) {
            currentVideoWidth = w
            currentVideoHeight = h
            textureView?.let { tv ->
              updateTextureTransform(tv.width, tv.height)
            }
          }
        }
        setOnPreparedListener { player ->
          if (running) {
            player.start()
          }
        }
        setOnErrorListener { _, _, _ ->
          stopVideoPlayback()
          true
        }
        prepareAsync()
      }
      mediaPlayer = mp
      currentVideoPath = videoPath
    } catch (_: Exception) {
      stopVideoPlayback()
    }
  }

  private fun stopVideoPlayback() {
    try {
      mediaPlayer?.let { mp ->
        if (mp.isPlaying) {
          mp.stop()
        }
        mp.reset()
        mp.release()
      }
    } catch (_: Exception) {
    }
    mediaPlayer = null
    currentVideoPath = ""
  }

  private fun resumeRendering() {
    if (!running) return
    renderer.resetFrameClock()
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    Choreographer.getInstance().postFrameCallback(frameCallback)
    canvasView.postInvalidateOnAnimation()
    try {
      if (mediaPlayer?.isPlaying != true && currentVideoPath.isNotEmpty()) {
        mediaPlayer?.start()
      }
    } catch (_: Exception) {}
  }

  private fun pauseRendering() {
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    try {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    } catch (_: Exception) {}
  }

  private fun startLogcatReader() {
    readerThread =
      Thread {
        try {
          val handle = PermissionManager.startLogcat(context) ?: return@Thread
          logcatHandle = handle
          val reader = BufferedReader(InputStreamReader(handle.inputStream))
          var line: String? = null
          while (running && reader.readLine().also { line = it } != null) {
            line?.let { ln ->
              if (ln.isNotEmpty()) {
                renderer.enqueueLine(ln)
              }
            }
          }
        } catch (_: Exception) {
        }
      }.apply {
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

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    Preferences.observer(context).unregisterOnSharedPreferenceChangeListener(this)
    running = false
    pauseRendering()
    stopVideoPlayback()
    stopLogcatReader()
    videoSurface?.release()
    videoSurface = null
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    if (hasWindowFocus) {
      resumeRendering()
    } else {
      pauseRendering()
    }
  }

  override fun onVisibilityChanged(
    changedView: View,
    visibility: Int,
  ) {
    super.onVisibilityChanged(changedView, visibility)
    if (visibility == VISIBLE) {
      resumeRendering()
    } else {
      pauseRendering()
    }
  }

  override fun onSharedPreferenceChanged(
    sharedPreferences: SharedPreferences?,
    key: String?,
  ) {
    val settings = Preferences.getSettings(context)
    renderer.updateSettings(settings)
    if (key == "wallpaper_video") {
      if (settings.wallpaperVideo != currentVideoPath) {
        setupViews(settings.wallpaperVideo)
      }
    }
    if (key == "permission" || key == "logcat_command") {
      stopLogcatReader()
      renderer.clear()
      startLogcatReader()
    }
  }
}
