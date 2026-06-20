/*
* Shows the dialog for choosing the logcat permission method.
*/
package com.yn.logcatlivewallpaper.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yn.logcatlivewallpaper.R
import com.yn.logcatlivewallpaper.core.PermissionManager

@Composable
fun PermissionPickerDialog(
  selectedMethod: String,
  onMethodSelected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_permission_method)) },
    text = {
      Column {
        val available =
          mapOf(
            "none" to true,
            "shizuku" to PermissionManager.isShizukuAvailable(),
            "root" to PermissionManager.isRootAvailable(),
          )
        listOf("none", "shizuku", "root").forEach { method ->
          val enabled = available[method] ?: false
          Row(
            modifier =
            Modifier
              .fillMaxWidth()
              .clickable(enabled = enabled) { onMethodSelected(method) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = method == selectedMethod,
              enabled = enabled,
              onClick = {
                if (enabled) {
                  onMethodSelected(method)
                }
              },
            )
            Text(
              text = method.replaceFirstChar { it.uppercase() },
              color =
              if (enabled) {
                MaterialTheme.colorScheme.onSurface
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_close))
      }
    },
  )
}
