package app.tidy.android

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tidy.android.apps.AppsScreen
import app.tidy.android.apps.AppsViewModel
import app.tidy.android.apps.OnboardingScreen
import app.tidy.android.data.SettingsRepository
import app.tidy.android.launcher.AppsScreenService
import app.tidy.android.launcher.HomeRole
import app.tidy.android.settings.SettingsScreen
import app.tidy.android.settings.TakeoverDialog
import app.tidy.android.ui.theme.TidyTheme
import app.tidy.android.update.AppUpdater
import app.tidy.android.update.UpdateDialog
import app.tidy.android.update.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one activity, registered both as a launcher icon and as a HOME
 * activity. As the home app it's never "finished": back does nothing at
 * the top level, and pressing home while here returns to a clean apps
 * screen (onNewIntent).
 */
class MainActivity : ComponentActivity() {
    private val vm: AppsViewModel by viewModels()

    /** Opened from the "new version available" notification: show the update dialog right away. */
    private var updateRequested by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var isDefaultHome by mutableStateOf(false)
    private var takeoverEnabled by mutableStateOf(false)
    private var showTakeoverDialog by mutableStateOf(false)
    /** Opened by AppsScreenService in place of One UI's apps screen: leave like one. */
    private var fromAppsSwipe = false

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = HomeRole.isHeld(this)
        takeoverEnabled = AppsScreenService.isEnabled(this)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            updateRequested = intent?.getBooleanExtra(EXTRA_SHOW_UPDATE, false) == true
        }
        isDefaultHome = HomeRole.isHeld(this)
        fromAppsSwipe = intent?.getBooleanExtra(EXTRA_FROM_APPS_SWIPE, false) == true
        val takeoverAvailable = AppsScreenService.isAvailable(this)

        // Lowest priority: screens' own BackHandlers (search, settings) run first.
        onBackPressedDispatcher.addCallback(this) {
            when {
                isDefaultHome -> {}
                fromAppsSwipe -> backToOneUiHome()
                else -> finish()
            }
        }

        setContent {
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TidyTheme(darkTheme = dark) {
                StartupUpdateCheck()

                val onboarded by vm.onboarded.collectAsStateWithLifecycle()
                val state by vm.state.collectAsStateWithLifecycle()
                when {
                    // Still reading settings: leave the wallpaper showing for that instant.
                    onboarded == null -> {}
                    onboarded == false -> OnboardingScreen(
                        state = state,
                        takeoverAvailable = takeoverAvailable,
                        onUseAppsScreen = {
                            finishOnboarding()
                            showTakeoverDialog = true
                        },
                        onSetHome = {
                            finishOnboarding()
                            requestHomeRole()
                        },
                        onKeepHome = ::finishOnboarding,
                    )
                    showSettings -> SettingsScreen(
                        state = state,
                        isDefaultHome = isDefaultHome,
                        takeoverAvailable = takeoverAvailable,
                        takeoverEnabled = takeoverEnabled,
                        onRequestHome = ::requestHomeRole,
                        onSetUpTakeover = { showTakeoverDialog = true },
                        onResetCustomizations = vm::resetCustomizations,
                        onBack = { showSettings = false },
                    )
                    else -> AppsScreen(vm, onOpenSettings = { showSettings = true })
                }
                if (showTakeoverDialog) TakeoverDialog(onDismiss = { showTakeoverDialog = false })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        AppsScreenService.onTidyLeft()
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = HomeRole.isHeld(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)) {
            updateRequested = true
        } else {
            // Home button, the launcher icon, or another swipe up while already open.
            fromAppsSwipe = intent.getBooleanExtra(EXTRA_FROM_APPS_SWIPE, false)
            showSettings = false
            vm.resetUi()
        }
    }

    /**
     * Leaving Tidy opened in place of One UI's apps screen: back acts as the
     * home button, which also closes One UI's apps screen still open behind
     * Tidy (see AppsScreenService.goHome).
     */
    private fun backToOneUiHome() {
        if (!AppsScreenService.goHome()) finish()
    }

    private fun finishOnboarding() {
        vm.finishOnboarding()
        // For the "new version" notification; the in-app notices need nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestHomeRole() {
        homeRoleRequest.launch(HomeRole.requestIntent(this))
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
        const val EXTRA_FROM_APPS_SWIPE = "from_apps_swipe"
    }
}

private val updateInfoSaver = listSaver<UpdateInfo?, Any>(
    save = { info -> if (info == null) emptyList() else listOf(info.versionName, info.notes, info.apkUrl, info.sizeBytes) },
    restore = { list ->
        if (list.isEmpty()) null
        else UpdateInfo(list[0] as String, list[1] as String, list[2] as String, list[3] as Long)
    },
)
