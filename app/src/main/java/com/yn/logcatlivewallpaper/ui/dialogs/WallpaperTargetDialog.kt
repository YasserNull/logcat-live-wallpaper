/*
* Shows the dialog for choosing which screen to set the wallpaper on.
*/
package com.yn.logcatlivewallpaper.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yn.logcatlivewallpaper.R

@Composable
fun WallpaperTargetDialog(
  onSetAsLockScreen: () -> Unit,
  onSetAsHomeScreen: () -> Unit,
  onSetAsHomeAndLockScreen: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.menu_set_wallpaper)) },
    text = {
      Column(Modifier.fillMaxWidth()) {
        WallpaperTargetOption(R.string.set_as_lock_screen, onSetAsLockScreen)
        WallpaperTargetOption(R.string.set_as_home_screen, onSetAsHomeScreen)
        WallpaperTargetOption(R.string.set_as_home_and_lock_screen, onSetAsHomeAndLockScreen)
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
private fun WallpaperTargetOption(
  labelRes: Int,
  onClick: () -> Unit,
) {
  Text(
    text = stringResource(labelRes),
    modifier =
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp),
  )
}
