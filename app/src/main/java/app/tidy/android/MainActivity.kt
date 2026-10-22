package app.tidy.android

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tidy.android.data.SettingsRepository
import app.tidy.android.oneui.Organizer
import app.tidy.android.settings.SettingsScreen
import app.tidy.android.ui.HomeScreen
import app.tidy.android.ui.PreviewScreen
import app.tidy.android.ui.TidyViewModel
import app.tidy.android.ui.theme.TidyTheme
import app.tidy.android.update.AppUpdater
import app.tidy.android.update.UpdateDialog
import app.tidy.android.update.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { HOME, PREVIEW, SETTINGS }

class MainActivity : ComponentActivity() {
    private val vm: TidyViewModel by viewModels()

    /** Opened from the "new version available" notification: show the update dialog right away. */
    private var updateRequested by mutableStateOf(false)
    private var screen by mutableStateOf(Screen.HOME)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            updateRequested = intent?.getBooleanExtra(EXTRA_SHOW_UPDATE, false) == true
            // New-app suggestions and the organizing progress are notifications.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val serviceReady by Organizer.ready.collectAsStateWithLifecycle()
            TidyTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StartupUpdateCheck()
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            vm = vm,
                            serviceReady = serviceReady,
                            onPreview = { screen = Screen.PREVIEW },
                            onSettings = { screen = Screen.SETTINGS },
                        )
                        Screen.PREVIEW -> PreviewScreen(
                            vm = vm,
                            onBack = { screen = Screen.HOME },
                            onApplied = { screen = Screen.HOME },
                        )
                        Screen.SETTINGS -> SettingsScreen(serviceReady = serviceReady, onBack = { screen = Screen.HOME })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)) updateRequested = true
    }

    /**
     * Once per launch, offer a newer GitHub release if there is one (unless
     * the user turned this off or skipped that exact version). Failures are
     * silent -- being offline at startup isn't worth an error.
     */
    @Composable
    private fun StartupUpdateCheck() {
        var update by rememberSaveable(stateSaver = updateInfoSaver) { mutableStateOf<UpdateInfo?>(null) }
        var checked by rememberSaveable { mutableStateOf(false) }
        val settings = remember { SettingsRepository(applicationContext) }
        LaunchedEffect(Unit) {
            if (checked || updateRequested || !settings.autoUpdateCheck.first()) return@LaunchedEffect
            checked = true
            val found = runCatching { AppUpdater.checkForUpdate() }.getOrNull() ?: return@LaunchedEffect
            if (found.versionName != settings.skippedUpdateVersion.first()) update = found
        }
        // From the notification: the user asked for it, so ignore "skip".
        LaunchedEffect(updateRequested) {
            if (!updateRequested) return@LaunchedEffect
            updateRequested = false
            checked = true
            runCatching { AppUpdater.checkForUpdate() }.getOrNull()?.let { update = it }
        }
        update?.let { info ->
            UpdateDialog(
                info = info,
                onDismiss = { update = null },
                onSkip = {
                    update = null
                    lifecycleScope.launch { settings.setSkippedUpdateVersion(info.versionName) }
                },
            )
        }
    }

    companion object {
        const val EXTRA_SHOW_UPDATE = "show_update"
    }
}

private val updateInfoSaver = listSaver<UpdateInfo?, Any>(
    save = { info -> if (info == null) emptyList() else listOf(info.versionName, info.notes, info.apkUrl, info.sizeBytes) },
    restore = { list ->
        if (list.isEmpty()) null
        else UpdateInfo(list[0] as String, list[1] as String, list[2] as String, list[3] as Long)
    },
)
