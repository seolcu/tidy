package app.tidy.android.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tidy.android.R
import app.tidy.android.data.AppEntry
import app.tidy.android.data.IconCache
import app.tidy.android.launcher.AppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app's icon, decoded off the main thread; a blank rounded square until then. */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(IconCache.peek(entry), entry) {
        if (value == null) value = withContext(Dispatchers.IO) { IconCache.load(context, entry) }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, filterQuality = FilterQuality.Medium, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

/** Icon and label. Tap opens the app; long-press offers move / app info / uninstall. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(item: AppItem, onMove: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val entry = item.entry

    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = { AppLauncher.launch(context, entry) },
                    onLongClickLabel = stringResource(R.string.app_menu_label),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = 6.dp),
        ) {
            AppIcon(entry, 56.dp)
            Text(
                entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_move)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_info)) },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    AppLauncher.openAppInfo(context, entry)
                },
            )
            if (!entry.isSystem) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_menu_uninstall)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        AppLauncher.uninstall(context, entry)
                    },
                )
            }
        }
    }
}
