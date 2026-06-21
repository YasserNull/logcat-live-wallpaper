/*
* Shows the main wallpaper preview and top level actions.
*/
package com.yn.logcatlivewallpaper.ui.activities

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowInsetsController
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
import com.yn.logcatlivewallpaper.R
import com.yn.logcatlivewallpaper.core.LogCatRenderer
import com.yn.logcatlivewallpaper.core.PermissionManager
import com.yn.logcatlivewallpaper.core.Preferences
import com.yn.logcatlivewallpaper.service.LogCatWallpaperService
import com.yn.logcatlivewallpaper.ui.theme.LogCatLiveWallpaperTheme
import com.yn.logcatlivewallpaper.utils.ApplyStatusBarColor
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
  private var currentLanguage = "en"

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(com.yn.logcatlivewallpaper.utils.LocaleHelper.wrap(newBase))
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
        Box(Modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize()) {
            Toolbar(
              onSettingsClick = {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
              },
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
  private fun Toolbar(onSettingsClick: () -> Unit) {
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
                setWallpaper()
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
) : View(context),
  SharedPreferences.OnSharedPreferenceChangeListener {
  private val renderer = LogCatRenderer(context)
  private var logcatHandle: PermissionManager.LogcatHandle? = null
  private var readerThread: Thread? = null
  private var running = false
  private val frameCallback =
    object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (running) {
          invalidate()
          Choreographer.getInstance().postFrameCallback(this)
        }
      }
    }

  init {
    Preferences.observer(context).registerOnSharedPreferenceChangeListener(this)
    running = true
    resumeRendering()
    startLogcatReader()
  }

  private fun resumeRendering() {
    if (!running) return
    renderer.resetFrameClock()
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    Choreographer.getInstance().postFrameCallback(frameCallback)
    postInvalidateOnAnimation()
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
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    stopLogcatReader()
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    if (hasWindowFocus) {
      resumeRendering()
    }
  }

  override fun onVisibilityChanged(
    changedView: View,
    visibility: Int,
  ) {
    super.onVisibilityChanged(changedView, visibility)
    if (visibility == VISIBLE) {
      resumeRendering()
    }
  }

  override fun onSharedPreferenceChanged(
    sharedPreferences: SharedPreferences?,
    key: String?,
  ) {
    renderer.updateSettings(Preferences.getSettings(context))
    if (key == "permission" || key == "logcat_command") {
      stopLogcatReader()
      renderer.clear()
      startLogcatReader()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    renderer.draw(canvas)
  }
}
