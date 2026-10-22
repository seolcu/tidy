package app.tidy.android.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tidy.android.BuildConfig
import app.tidy.android.R
import app.tidy.android.apps.AppsState
import app.tidy.android.data.SettingsRepository
import app.tidy.android.ui.components.SectionCard
import app.tidy.android.ui.components.SettingsRow
import app.tidy.android.update.AppUpdater
import app.tidy.android.update.UpdateCheckWorker
import app.tidy.android.update.UpdateDialog
import app.tidy.android.update.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: AppsState?,
    isDefaultHome: Boolean,
    takeoverAvailable: Boolean,
    takeoverEnabled: Boolean,
    onRequestHome: () -> Unit,
    onSetUpTakeover: () -> Unit,
    onResetCustomizations: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsRepository(context) }

    var confirmReset by remember { mutableStateOf(false) }
    var autoUpdateCheck by remember { mutableStateOf(true) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) { autoUpdateCheck = settings.autoUpdateCheck.first() }
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            SectionCard(stringResource(R.string.settings_home_section)) {
                SettingsRow(
                    icon = Icons.Rounded.Home,
                    title = stringResource(R.string.settings_default_home),
                    supporting = stringResource(
                        if (isDefaultHome) R.string.settings_default_home_on else R.string.settings_default_home_off
                    ),
                    onClick = onRequestHome,
                )
                if (takeoverAvailable) {
                    SettingsRow(
                        icon = Icons.Rounded.SwipeUp,
                        title = stringResource(R.string.settings_takeover),
                        supporting = stringResource(
                            if (takeoverEnabled) R.string.settings_takeover_on else R.string.settings_takeover_off
                        ),
                        onClick = onSetUpTakeover,
                    )
                }
            }

            SectionCard(stringResource(R.string.settings_sorting_section)) {
                val count = state?.customizationCount ?: 0
                SettingsRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.settings_reset),
                    supporting = if (count == 0) stringResource(R.string.settings_reset_none)
                    else stringResource(R.string.settings_reset_hint, count),
                    onClick = if (count == 0) null else { { confirmReset = true } },
                )
            }

            if (AppUpdater.isEnabled) {
                SectionCard(stringResource(R.string.settings_updates)) {
                    SettingsRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = stringResource(R.string.settings_check_update),
                        supporting = when {
                            checkingUpdate -> stringResource(R.string.settings_checking_update)
                            updateStatus != null -> updateStatus
                            else -> stringResource(R.string.settings_current_version, BuildConfig.VERSION_NAME)
                        },
                        onClick = if (checkingUpdate) null else {
                            {
                                checkingUpdate = true
                                updateStatus = null
                                scope.launch {
                                    try {
                                        val found = AppUpdater.checkForUpdate()
                                        if (found == null) {
                                            updateStatus = context.getString(R.string.settings_up_to_date, BuildConfig.VERSION_NAME)
                                        } else {
                                            availableUpdate = found
                                            updateStatus = context.getString(R.string.settings_update_available, found.versionName)
                                        }
                                    } catch (e: Exception) {
                                        updateStatus = context.getString(R.string.settings_update_check_failed, e.message ?: context.getString(R.string.settings_network_error))
                                    } finally {
                                        checkingUpdate = false
                                    }
                                }
                            }
                        },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Update,
                        title = stringResource(R.string.settings_auto_update),
                        supporting = stringResource(R.string.settings_auto_update_hint),
                        trailing = {
                            Switch(checked = autoUpdateCheck, onCheckedChange = { value ->
                                autoUpdateCheck = value
                                scope.launch { settings.setAutoUpdateCheck(value) }
                                if (value) UpdateCheckWorker.schedule(context) else UpdateCheckWorker.cancel(context)
                            })
                        },
                    )
                }
            } else {
                SectionCard(null) {
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_version),
                        supporting = BuildConfig.VERSION_NAME,
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetCustomizations()
                }) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    availableUpdate?.let { info ->
        UpdateDialog(info = info, onDismiss = { availableUpdate = null })
    }
}
