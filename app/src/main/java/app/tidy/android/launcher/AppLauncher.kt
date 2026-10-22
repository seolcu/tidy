package app.tidy.android.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import app.tidy.android.R
import app.tidy.android.data.AppEntry

/** Starting, inspecting and uninstalling other apps -- the launcher's side of the platform. */
object AppLauncher {

    fun launch(context: Context, entry: AppEntry) {
        try {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(entry.info.componentName, entry.user, null, null)
        } catch (_: Exception) {
            // Disabled, uninstalled a moment ago, or its profile is paused.
            Toast.makeText(context, context.getString(R.string.app_launch_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(context: Context, entry: AppEntry) {
        runCatching {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(entry.info.componentName, entry.user, null, null)
        }
    }

    /** Hands off to the system's uninstall confirmation. */
    fun uninstall(context: Context, entry: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
                    .putExtra(Intent.EXTRA_USER, entry.user)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Whether Tidy is the default home app, and how to ask to become it. */
object HomeRole {

    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /**
     * The system's "set as default home app" prompt, or the home-app settings
     * screen where the role can't be requested directly.
     */
    fun requestIntent(context: Context): Intent {
        val roles = context.getSystemService(RoleManager::class.java)
        return if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }
}
