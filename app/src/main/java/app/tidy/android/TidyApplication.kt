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
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        UpdateCheckWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_UPDATES = "app_updates"
    }
}
