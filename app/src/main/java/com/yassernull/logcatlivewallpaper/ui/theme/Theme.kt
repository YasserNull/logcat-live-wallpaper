/*
* Provides the Compose theme and color scheme.
*/
package com.yassernull.logcatlivewallpaper.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
  )

@Composable
fun LogCatLiveWallpaperTheme(content: @Composable () -> Unit) {
  val colorScheme =
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        dynamicDarkColorScheme(context)
      }
      else -> DarkColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
