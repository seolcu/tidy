package app.tidy.android.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidy.android.R
import app.tidy.android.domain.Category
import app.tidy.android.domain.Folder
import app.tidy.android.domain.matchesQuery
import app.tidy.android.launcher.AppLauncher

/**
 * The apps screen: search, a folders / A–Z switch, and the grid. Drawn
 * over the wallpaper (the window shows it), with a mostly opaque wash so
 * labels stay readable on any wallpaper.
 */
@Composable
fun AppsScreen(vm: AppsViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val moving by vm.moving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.installNotices.collect { notice ->
            val folder = vm.state.value?.folderName(context, notice.category) ?: context.getString(notice.category.label)
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.install_notice, notice.label, folder),
                actionLabel = context.getString(R.string.install_notice_change),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.moving.value = notice.key
        }
    }

    BackHandler(enabled = query.isNotEmpty()) { vm.query.value = "" }

    val results = remember(state, query) {
        state?.all?.filter { matchesQuery(query, it.entry.label, it.entry.packageName) }.orEmpty()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SearchRow(
                query = query,
                onQueryChange = { vm.query.value = it },
                onGo = { results.firstOrNull()?.let { AppLauncher.launch(context, it.entry) } },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )

            val s = state
            when {
                s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                query.isNotBlank() -> if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.apps_no_results, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    )
                } else {
                    AppGrid(results, onMove = { vm.moving.value = it.entry.key }, topPadding = 16)
                }
                else -> {
                    ModeRow(
                        mode = viewMode,
                        onModeChange = { vm.viewMode.value = it },
                        appCount = s.all.size,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    when (viewMode) {
                        ViewMode.FOLDERS -> FolderGrid(s, onOpen = { vm.openFolder.value = it })
                        ViewMode.ALL -> AppGrid(s.all, onMove = { vm.moving.value = it.entry.key }, topPadding = 4)
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) { data ->
            Snackbar(
                data,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }

    val s = state
    openFolder?.let { category ->
        val folder = s?.folders?.firstOrNull { it.category == category }
        if (folder == null) {
            // Emptied (last app moved out or uninstalled) while open.
            LaunchedEffect(category) { vm.openFolder.value = null }
        } else {
            FolderDialog(
                folder = folder,
                name = s.folderName(context, category),
                onRename = { vm.renameFolder(category, it) },
                onMove = { vm.moving.value = it.entry.key },
                onDismiss = { vm.openFolder.value = null },
            )
        }
    }
    moving?.let { key ->
        val item = s?.all?.firstOrNull { it.entry.key == key }
        if (item == null) {
            LaunchedEffect(key) { vm.moving.value = null }
        } else {
            MoveSheet(item, s, onPick = { vm.moveTo(key, it) }, onDismiss = { vm.moving.value = null })
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.apps_search_hint)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f).height(48.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onGo() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.apps_clear_search))
                    }
                } else {
                    Spacer(Modifier.size(12.dp))
                }
            }
        }
        Surface(onClick = onOpenSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.apps_settings))
            }
        }
    }
}

@Composable
private fun ModeRow(mode: ViewMode, onModeChange: (ViewMode) -> Unit, appCount: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
        ) {
            ViewMode.entries.forEach { option ->
                val selected = option == mode
                Surface(
                    onClick = { onModeChange(option) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.Tab
                    },
                ) {
                    Text(
                        stringResource(if (option == ViewMode.FOLDERS) R.string.apps_view_folders else R.string.apps_view_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Text(
            pluralStringResource(R.plurals.apps_summary, appCount, appCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private val gridBottomPadding = 96.dp

@Composable
private fun FolderGrid(state: AppsState, onOpen: (Category) -> Unit) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = gridBottomPadding),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        items(state.folders, key = { it.category.name }) { folder ->
            FolderTile(folder, state.folderName(context, folder.category), onClick = { onOpen(folder.category) })
        }
    }
}

@Composable
private fun FolderTile(folder: Folder<AppItem>, name: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
        ) {
            FolderPreview(folder.apps)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.apps_count, folder.apps.size, folder.apps.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The first four icons in a 2×2 grid, like a folder on a home screen. */
@Composable
private fun FolderPreview(apps: List<AppItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 2) {
                    val app = apps.getOrNull(row * 2 + col)
                    if (app != null) AppIcon(app.entry, 28.dp) else Spacer(Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppItem>, onMove: (AppItem) -> Unit, topPadding: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding.dp, bottom = gridBottomPadding),
        modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
    ) {
        items(apps, key = { it.entry.key }) { item ->
            AppTile(item, onMove = { onMove(item) })
        }
    }
}
