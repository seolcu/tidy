package app.tidy.android.oneui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.tidy.android.R
import app.tidy.android.TidyApplication

/** The new-app helper's notifications: "put X in folder Y?" and how it went. */
object NewAppNotifier {

    fun ask(context: Context, label: String, folder: String) {
        val id = idFor(label)
        val add = PendingIntent.getBroadcast(
            context, id,
            Intent(context, NewAppReceiver::class.java)
                .setAction(NewAppReceiver.ACTION_ADD)
                .putExtra(NewAppReceiver.EXTRA_LABEL, label)
                .putExtra(NewAppReceiver.EXTRA_FOLDER, folder),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context, id,
            NotificationCompat.Builder(context, TidyApplication.CHANNEL_NEW_APPS)
                .setSmallIcon(R.drawable.ic_stat_tidy)
                .setContentTitle(context.getString(R.string.new_app_title, label))
                .setContentText(context.getString(R.string.new_app_body, folder))
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.new_app_add), add),
        )
    }

    fun done(context: Context, label: String, folder: String, ok: Boolean) {
        post(
            context, idFor(label),
            NotificationCompat.Builder(context, TidyApplication.CHANNEL_NEW_APPS)
                .setSmallIcon(R.drawable.ic_stat_tidy)
                .setContentTitle(
                    if (ok) context.getString(R.string.new_app_done, label, folder)
                    else context.getString(R.string.new_app_failed, label)
                )
                .setTimeoutAfter(if (ok) 5_000 else 0)
                .setAutoCancel(true),
        )
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, builder.build()) }
    }

    private fun idFor(label: String) = 1_000 + (label.hashCode() and 0xFFFF)
}

/** Notification buttons: "Add" from the new-app helper, "Stop" while organizing. */
class NewAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> Organizer.stop()
            ACTION_ADD -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: return
                val folder = intent.getStringExtra(EXTRA_FOLDER) ?: return
                OrganizerService.instance?.fileNewApp(label, folder)
            }
        }
    }

    companion object {
        const val ACTION_ADD = "app.tidy.android.ADD_NEW_APP"
        const val ACTION_STOP = "app.tidy.android.STOP_ORGANIZING"
        const val EXTRA_LABEL = "label"
        const val EXTRA_FOLDER = "folder"
    }
}
