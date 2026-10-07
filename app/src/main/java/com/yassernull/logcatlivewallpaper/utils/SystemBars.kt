/*
* Applies system bar colors for activity screens.
*/
package com.yassernull.logcatlivewallpaper.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
@Suppress("DEPRECATION")
fun ApplyStatusBarColor(statusBarColor: ComposeColor) {
  val activity = LocalContext.current as? Activity
  LaunchedEffect(statusBarColor) {
    val window = activity?.window ?: return@LaunchedEffect
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = statusBarColor.toArgb()
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.setNavigationBarContrastEnforced(false)
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
    }
  }
}
