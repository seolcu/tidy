package app.tidy.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidy.android.R
import app.tidy.android.oneui.Work
import app.tidy.android.organize.DrawerFolder
import app.tidy.android.organize.Step
import app.tidy.android.ui.components.SectionCard

/** Tidy's proposal for the apps screen, editable, then "apply". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewScreen(vm: TidyViewModel, onBack: () -> Unit, onApplied: () -> Unit) {
    val work by vm.work.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleLarge)
        }

        val p = preview
        if (work == Work.Reading || p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (work == Work.ReadFailed) {
                    Text(stringResource(R.string.work_read_failed), modifier = Modifier.padding(24.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.work_reading))
                    }
                }
            }
            return@Column
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { vm.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(stringResource(if (m == Mode.KEEP) R.string.mode_keep else R.string.mode_rebuild)) }
                }
            }
            Text(
                stringResource(if (mode == Mode.KEEP) R.string.mode_keep_hint else R.string.mode_rebuild_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val dissolves = p.steps.count { it is Step.Dissolve }
            val creates = p.steps.count { it is Step.CreateFolder }
            Text(
                if (p.steps.isEmpty()) stringResource(R.string.preview_nothing)
                else stringResource(R.string.preview_summary, p.changedApps.size, creates, dissolves),
                style = MaterialTheme.typography.titleSmall,
            )

            // In "keep", only the folders that change are worth showing.
            val shown = if (mode == Mode.KEEP) p.target.folders.filter { f -> f.apps.any { it in p.changedApps } } else p.target.folders
            shown.forEach { folder ->
                FolderCard(
                    folder = folder,
                    isNew = folder.name in p.newFolders,
                    changed = p.changedApps,
                    onApp = { moving = it },
                    onRename = { renaming = folder.name }.takeIf { folder.name in p.newFolders },
                )
            }
            if (p.target.loose.isNotEmpty()) {
                FolderCard(
                    folder = DrawerFolder(stringResource(R.string.preview_loose), p.target.loose),
                    isNew = false,
                    changed = p.changedApps,
                    onApp = { moving = it },
                    onRename = null,
                )
            }
        }

        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = { confirm = true },
                enabled = p.steps.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(pluralStringResource(R.plurals.preview_apply, p.steps.size, p.steps.size)) }
        }

        moving?.let { app ->
            MoveSheet(
                app = app,
                folders = p.target.folders.map { it.name },
                current = p.target.folders.firstOrNull { app in it.apps }?.name,
                onPick = {
                    vm.moveApp(app, it)
                    moving = null
                },
                onDismiss = { moving = null },
            )
        }
        renaming?.let { name ->
            RenameDialog(name, onDone = {
                vm.renameFolder(name, it)
                renaming = null
            }, onDismiss = { renaming = null })
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.preview_confirm_title)) },
                text = { Text(stringResource(R.string.preview_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        if (vm.apply()) onApplied()
                    }) { Text(stringResource(R.string.preview_confirm_go)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderCard(folder: DrawerFolder, isNew: Boolean, changed: Set<String>, onApp: (String) -> Unit, onRename: (() -> Unit)?) {
    SectionCard(null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isNew) {
                    Text(
                        stringResource(R.string.preview_new_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                onRename?.let { TextButton(onClick = it) { Text(stringResource(R.string.preview_rename)) } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                folder.apps.forEach { app ->
                    FilterChip(
                        selected = app in changed,
                        onClick = { onApp(app) },
                        label = { Text(app) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(app: String, folders: List<String>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.move_title, app),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            (folders.map { it to it } + (null to stringResource(R.string.preview_loose))).forEach { (folder, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPick(folder) }
                        .padding(horizontal = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (folder == current) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.move_current), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(name: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_rename)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(stringResource(R.string.folder_name)) })
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(stringResource(R.string.folder_rename_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
