package app.tidy.android.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tidy.android.R
import app.tidy.android.domain.Folder

/** An open folder: its apps, and renaming it in place. */
@Composable
fun FolderDialog(
    folder: Folder<AppItem>,
    name: String,
    onRename: (String) -> Unit,
    onMove: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable(folder.category) { mutableStateOf(false) }
    var draft by rememberSaveable(folder.category) { mutableStateOf(name) }

    fun save() {
        onRename(draft)
        editing = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 20.dp),
            ) {
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.folder_name)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        trailingIcon = { TextButton(onClick = ::save) { Text(stringResource(R.string.folder_rename_save)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                pluralStringResource(R.plurals.apps_count, folder.apps.size, folder.apps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            draft = name
                            editing = true
                        }) { Text(stringResource(R.string.folder_rename)) }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    items(folder.apps, key = { it.entry.key }) { item ->
                        AppTile(item, onMove = { onMove(item) })
                    }
                }
                Text(
                    stringResource(R.string.folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
