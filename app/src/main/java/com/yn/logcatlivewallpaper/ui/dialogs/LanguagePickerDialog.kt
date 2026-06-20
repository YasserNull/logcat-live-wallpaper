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

@Composable
fun LanguagePickerDialog(
  selectedLanguage: String,
  onLanguageSelected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val languages = listOf(
    "en" to "English",
    "ar" to "العربية",
    "fr" to "Français",
    "hi" to "हिन्दी",
    "zh" to "中文",
    "ja" to "日本語",
    "es" to "Español",
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_language)) },
    text = {
      Column {
        languages.forEach { (code, label) ->
          Row(
            modifier =
            Modifier
              .fillMaxWidth()
              .clickable { onLanguageSelected(code) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = code == selectedLanguage,
              onClick = { onLanguageSelected(code) },
            )
            Text(text = label)
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
