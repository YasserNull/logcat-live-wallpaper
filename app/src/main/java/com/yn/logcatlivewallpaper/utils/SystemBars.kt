/*
* Applies system bar colors for activity screens.
*/
package com.yn.logcatlivewallpaper.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun ApplyStatusBarColor(statusBarColor: ComposeColor) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(statusBarColor) {
        val window = activity?.window ?: return@LaunchedEffect
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.setNavigationBarContrastEnforced(false)
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
    }
}
