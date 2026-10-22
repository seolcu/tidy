package app.tidy.android.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.tidy.android.R
import app.tidy.android.launcher.AppsScreenService

/**
 * Explains the apps-screen takeover (and the accessibility permission it
 * needs) before sending the user to turn it on.
 */
@Composable
fun TakeoverDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SwipeUp, contentDescription = null) },
        title = { Text(stringResource(R.string.takeover_title)) },
        text = { Text(stringResource(R.string.takeover_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching { context.startActivity(AppsScreenService.settingsIntent()) }
            }) { Text(stringResource(R.string.takeover_open_settings)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { runCatching { context.startActivity(AppsScreenService.appInfoIntent(context)) } }) {
                    Text(stringResource(R.string.takeover_open_app_info))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
