/*
* Shows the dialog for editing log area size and position.
*/
package com.yn.logcatlivewallpaper.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yn.logcatlivewallpaper.R

@Composable
fun SizePositionDialog(
  width: String,
  height: String,
  positionX: String,
  positionY: String,
  rotation: String,
  onWidthChanged: (String) -> Unit,
  onHeightChanged: (String) -> Unit,
  onPositionXChanged: (String) -> Unit,
  onPositionYChanged: (String) -> Unit,
  onRotationChanged: (String) -> Unit,
  onReset: () -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_change_size_position)) },
    text = {
      Column {
        NumberField(stringResource(R.string.settings_width), width, onWidthChanged)
        NumberField(stringResource(R.string.settings_height), height, onHeightChanged)
        NumberField(stringResource(R.string.settings_position_x), positionX, onPositionXChanged)
        NumberField(stringResource(R.string.settings_position_y), positionY, onPositionYChanged)
        NumberField(
          stringResource(R.string.settings_rotation),
          rotation,
          onRotationChanged,
          allowNegative = true,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onSave) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onReset) {
        Text(stringResource(R.string.action_reset))
      }
    },
  )
}

@Composable
private fun NumberField(
  label: String,
  value: String,
  onValueChanged: (String) -> Unit,
  allowNegative: Boolean = false,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { input ->
      onValueChanged(
        input.filterIndexed { index, char ->
          char.isDigit() || char == '.' || (allowNegative && char == '-' && index == 0)
        },
      )
    },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier =
    Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  )
}
