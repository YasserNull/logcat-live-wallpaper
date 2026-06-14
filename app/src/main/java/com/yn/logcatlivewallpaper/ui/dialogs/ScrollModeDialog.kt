/*
* Shows the dialog for choosing the scroll mode.
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
fun ScrollModeDialog(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_scroll_mode)) },
        text = {
            Column {
                listOf(
                    Preferences.SCROLL_MODE_SMOOTH to stringResource(R.string.scroll_mode_smooth),
                    Preferences.SCROLL_MODE_TERMINAL to stringResource(R.string.scroll_mode_terminal)
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == selectedMode,
                            onClick = { onModeSelected(mode) }
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
        }
    )
}
