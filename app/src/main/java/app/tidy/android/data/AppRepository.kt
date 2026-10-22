package app.tidy.android.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.tidy.android.BuildConfig
import app.tidy.android.domain.Categorizer
import app.tidy.android.domain.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One launchable activity, in one user profile (personal or work). */
class AppEntry(
    val info: LauncherActivityInfo,
    val isSystem: Boolean,
    val autoCategory: Category,
) {
    val packageName: String get() = info.componentName.packageName
    val user: UserHandle get() = info.user
    val label: String = info.label.toString()

    /** Stable across reloads; unique even when an app is in both profiles. */
    val key: String = "${info.componentName.flattenToShortString()}#${info.user.hashCode()}"
}

/**
 * The launchable apps on the phone, kept current through
 * [LauncherApps.Callback]. A launcher needs no QUERY_ALL_PACKAGES:
 * LauncherApps already lists everything with a launcher icon, across the
 * personal and work profiles.
 */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reloadLock = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    /** Null until the first load finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val _installed = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Package names of apps installed while Tidy is running. */
    val installed: SharedFlow<String> = _installed

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            reload()
            _installed.tryEmit(packageName)
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(*packageNames)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(*packageNames)
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    /** Re-reads the app list; [changed] packages also get their cached icons dropped. */
    fun reload(vararg changed: String) {
        changed.forEach(IconCache::evictPackage)
        scope.launch { reloadLock.withLock { _apps.value = load() } }
    }

    private fun load(): List<AppEntry> = userManager.userProfiles.flatMap { user ->
        // A paused work profile can refuse to list its apps.
        runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
    }
        .map { info ->
            val app = info.applicationInfo
            val isSystem = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            AppEntry(
                info = info,
                isSystem = isSystem,
                autoCategory = Categorizer.classify(
                    packageName = info.componentName.packageName,
                    label = info.label.toString(),
                    osCategory = declaredCategory(app),
                    isSystem = isSystem,
                ),
            ).also { entry ->
                // `adb logcat -s TidyClassify` shows why each app landed where it did.
                if (BuildConfig.DEBUG) {
                    Log.d("TidyClassify", "${entry.packageName}|${entry.label}|cat=${app.category}|sys=$isSystem|-> ${entry.autoCategory}")
                }
            }
        }

    /** What the app says about itself (`android:appCategory`), mapped onto Tidy's folders. */
    // CATEGORY_ACCESSIBILITY is API 31, but it's a compile-time constant: on
    // older phones it simply never matches.
    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    private fun declaredCategory(app: ApplicationInfo): Category? = when (app.category) {
        ApplicationInfo.CATEGORY_GAME -> Category.GAME
        ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
        ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> Category.PHOTO
        ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> Category.NEWS
        ApplicationInfo.CATEGORY_MAPS -> Category.MAP
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.WORK
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> Category.TOOLS
        else -> if (app.flags and ApplicationInfo.FLAG_IS_GAME != 0) Category.GAME else null
    }
}
