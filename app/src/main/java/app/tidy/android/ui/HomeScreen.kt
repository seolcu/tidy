package app.tidy.android.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidy.android.R
import app.tidy.android.data.NewAppMode
import app.tidy.android.oneui.Work
import app.tidy.android.organize.RunResult
import app.tidy.android.ui.components.SectionCard
import app.tidy.android.ui.components.SettingsRow
import app.tidy.android.ui.components.TidyMark

@Composable
fun HomeScreen(vm: TidyViewModel, serviceReady: Boolean, onPreview: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val work by vm.work.collectAsStateWithLifecycle()
    val lastRead by vm.lastRead.collectAsStateWithLifecycle()
    val undo by vm.undoSnapshot.collectAsStateWithLifecycle()
    val newAppMode by vm.newAppMode.collectAsStateWithLifecycle()
    var confirmUndo by remember { mutableStateOf(false) }
    val busy = work is Work.Reading || work is Work.Organizing

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TidyMark(size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }

        if (!serviceReady) {
            SectionCard(stringResource(R.string.setup_section)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Accessibility, contentDescription = null)
                        Text(stringResource(R.string.setup_open_settings), modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.setup_restricted)) }
                }
            }
        }

        SectionCard(stringResource(R.string.home_apps_screen)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val status = when (val w = work) {
                    Work.Reading -> stringResource(R.string.work_reading)
                    is Work.Organizing -> stringResource(R.string.work_organizing, w.done, w.total)
                    Work.ReadFailed -> stringResource(R.string.work_read_failed)
                    is Work.Finished -> when (val r = w.result) {
                        RunResult.Done -> stringResource(if (w.undo) R.string.work_undone else R.string.work_done)
                        is RunResult.Stopped -> stringResource(R.string.work_stopped, r.done, r.total)
                        is RunResult.Failed -> stringResource(R.string.work_failed, r.done + 1, r.total)
                    }
                    Work.Idle -> lastRead?.let { l ->
                        pluralStringResource(R.plurals.home_layout_folders, l.folders.size, l.folders.size) + " · " +
                            pluralStringResource(R.plurals.home_layout_loose, l.loose.size, l.loose.size)
                    } ?: stringResource(R.string.home_not_read)
                }
                Text(status, style = MaterialTheme.typography.bodyLarge)
                (work as? Work.Organizing)?.let { w ->
                    LinearProgressIndicator(progress = { if (w.total == 0) 0f else w.done.toFloat() / w.total }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.organizing_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.organizing_stop)) }
                }
                if (!busy) {
                    Button(
                        onClick = { if (vm.read()) onPreview() },
                        enabled = serviceReady,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.home_start)) }
                    Text(
                        stringResource(R.string.home_start_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (undo != null && !busy) {
            SectionCard(null) {
                SettingsRow(
                    icon = Icons.Rounded.Undo,
                    title = stringResource(R.string.home_undo),
                    supporting = stringResource(R.string.home_undo_hint),
                    onClick = if (serviceReady) { { confirmUndo = true } } else null,
                )
            }
        }

        SectionCard(stringResource(R.string.new_app_section)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                listOf(
                    NewAppMode.ASK to R.string.new_app_mode_ask,
                    NewAppMode.AUTO to R.string.new_app_mode_auto,
                    NewAppMode.OFF to R.string.new_app_mode_off,
                ).forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = newAppMode == mode, role = Role.RadioButton, onClick = { vm.setNewAppMode(mode) })
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        RadioButton(selected = newAppMode == mode, onClick = null)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.new_app_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.home_undo_confirm_title)) },
            text = { Text(stringResource(R.string.home_undo_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    vm.undo()
                }) { Text(stringResource(R.string.home_undo)) }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
