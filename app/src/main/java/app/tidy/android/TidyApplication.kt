package app.tidy.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.tidy.android.data.AppRepository
import app.tidy.android.update.UpdateCheckWorker

class TidyApplication : Application() {

    /** One app list per process, so the LauncherApps callback is registered once. */
    val apps: AppRepository by lazy { AppRepository(this).also { it.start() } }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW_APPS, getString(R.string.channel_new_apps), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Shown while Tidy moves icons; quiet, the screen itself is the feedback.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ORGANIZING, getString(R.string.channel_organizing), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        UpdateCheckWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_NEW_APPS = "new_apps"
        const val CHANNEL_ORGANIZING = "organizing"
        const val CHANNEL_UPDATES = "app_updates"
    }
}
