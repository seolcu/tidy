package app.tidy.android.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import app.tidy.android.MainActivity
import app.tidy.android.R

/**
 * Keeps One UI Home as the home screen but swaps its apps screen for Tidy:
 * when the Samsung launcher shows its apps screen (swipe up on home), Tidy
 * opens over it.
 *
 * There's no API for this, so it's an accessibility service limited (in
 * res/xml/apps_screen_service.xml) to the Samsung launcher's own windows.
 * The apps screen is recognized by its `apps_screen` view id, which the home
 * screen doesn't have; checked on One UI 8 (Android 17). It only looks for
 * that id and never reads any other app's screen.
 */
class AppsScreenService : AccessibilityService() {
    private var appsScreenShown = false
    private var lastTakeover = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != SAMSUNG_LAUNCHER) return
        val root = rootInActiveWindow ?: return
        if (root.packageName != SAMSUNG_LAUNCHER) return
        val shown = root.findAccessibilityNodeInfosByViewId(APPS_SCREEN_ID).any { it.isVisibleToUser }
        if (SystemClock.uptimeMillis() < ignoreUntil) {
            // Tidy just left; One UI is still closing the apps screen that was
            // behind it. Count it as closed already: the home screen may send no
            // further events, and the next swipe must register as an opening.
            appsScreenShown = false
            return
        }
        // Act on the transition only: content events keep coming while it's open.
        // Once Tidy covers it, forget it was open: this service never sees it
        // close behind Tidy, and the next swipe must count as a new opening.
        // (Repeat events before Tidy is up are absorbed by the debounce.)
        appsScreenShown = if (shown && !appsScreenShown) {
            takeOver()
            false
        } else {
            shown
        }
    }

    private fun takeOver() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTakeover < DEBOUNCE_MS) return
        lastTakeover = now
        val options = ActivityOptions.makeCustomAnimation(this, R.anim.apps_screen_enter, R.anim.none)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FROM_APPS_SWIPE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            options.toBundle(),
        )
        // Samsung's apps screen stays open behind Tidy; MainActivity sends HOME
        // when leaving, which closes it. (A global BACK here raced Tidy's start
        // and closed Tidy itself instead.)
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    companion object {
        const val SAMSUNG_LAUNCHER = "com.sec.android.app.launcher"
        private const val APPS_SCREEN_ID = "$SAMSUNG_LAUNCHER:id/apps_screen"
        private const val DEBOUNCE_MS = 800L
        private const val AFTER_LEAVING_MS = 1_000L

        @Volatile
        private var ignoreUntil = 0L

        @Volatile
        private var instance: AppsScreenService? = null

        /**
         * Leaves Tidy the way the home button does. Only the real home action
         * closes One UI's apps screen (still open behind Tidy); a HOME intent
         * from an app brings One UI Home forward with its apps screen still up.
         * False when the service isn't running.
         */
        fun goHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        /**
         * Called whenever Tidy leaves the screen. Going home (back or the home
         * button) makes One UI close the apps screen that's still open behind
         * Tidy, and that closing apps screen must not bring Tidy straight back.
         */
        fun onTidyLeft() {
            ignoreUntil = SystemClock.uptimeMillis() + AFTER_LEAVING_MS
        }

        /** Only offered on phones with One UI Home. */
        fun isAvailable(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(SAMSUNG_LAUNCHER, 0) }.isSuccess

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, AppsScreenService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Accessibility settings, where the user finds Tidy and turns it on. (The
         * page for one service needs a system-only permission: opening it
         * crashed with a SecurityException on One UI 8.)
         */
        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        /**
         * App info, where Android 13+ hides "Allow restricted settings" (⋮) for
         * sideloaded apps -- needed once before an accessibility service can be turned on.
         */
        fun appInfoIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
}
