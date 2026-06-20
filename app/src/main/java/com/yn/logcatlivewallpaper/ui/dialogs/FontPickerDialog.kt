/*
* Shows the dialog for choosing a built in or custom font.
*/
package com.yn.logcatlivewallpaper.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yn.logcatlivewallpaper.R
import com.yn.logcatlivewallpaper.core.Preferences

@Composable
fun FontPickerDialog(
  selectedFontPath: String,
  onBuiltInFontSelected: (String) -> Unit,
  onCustomFontSelected: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_change_font)) },
    text = {
      Column {
        val options =
          listOf(
            Preferences.FONT_CGA to stringResource(R.string.font_int10h),
            Preferences.FONT_DEFAULT to stringResource(R.string.font_default),
            Preferences.FONT_UBUNTU to stringResource(R.string.font_ubuntu_bold),
          )
        options.forEach { (path, label) ->
          Row(
            modifier =
            Modifier
              .fillMaxWidth()
              .clickable { onBuiltInFontSelected(path) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = path == selectedFontPath,
              onClick = { onBuiltInFontSelected(path) },
            )
            Text(text = label)
          }
        }
        Row(
          modifier =
          Modifier
            .fillMaxWidth()
            .clickable { onCustomFontSelected() }
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = selectedFontPath.isNotEmpty() && !selectedFontPath.startsWith("__"),
            onClick = onCustomFontSelected,
          )
          Text(text = stringResource(R.string.font_custom))
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
