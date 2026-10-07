/*
* Shows the dialog for editing the logcat command.
*/
package com.yassernull.logcatlivewallpaper.ui.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yassernull.logcatlivewallpaper.R

@Composable
fun CommandEditorDialog(
  command: String,
  onCommandChanged: (String) -> Unit,
  onReset: () -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_customize_command)) },
    text = {
      OutlinedTextField(
        value = command,
        onValueChange = onCommandChanged,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(onClick = onSave) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onReset) {
          Text(stringResource(R.string.action_reset))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.action_cancel))
        }
      }
    },
  )
}
