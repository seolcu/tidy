package app.tidy.android.oneui

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import app.tidy.android.MainActivity
import app.tidy.android.R
import app.tidy.android.TidyApplication
import app.tidy.android.data.NewAppMode
import app.tidy.android.data.SettingsRepository
import app.tidy.android.organize.DrawerLayout
import app.tidy.android.organize.Executor
import app.tidy.android.organize.Planner
import app.tidy.android.organize.RunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tidy's hands on One UI's apps screen: reads it and changes its folders
 * through [OneUiDrawer], one step at a time, like a person would. Turned on
 * by the user in Accessibility settings; limited to the Samsung launcher's
 * windows (res/xml/organizer_service.xml).
 *
 * Also stays around to notice newly installed apps for the new-app helper.
 */
class OrganizerService : AccessibilityService() {
    /** Made on every connect: One UI can unbind and rebind the same service object. */
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var drawer: OneUiDrawer
    private lateinit var settings: SettingsRepository
    private var watchInstalls: Job? = null

    override fun onServiceConnected() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        drawer = OneUiDrawer(this)
        settings = SettingsRepository(this)
        instance = this
        Organizer.setReady(true)
        Log.i("TidyService", "connected")
        val apps = (application as TidyApplication).apps
        watchInstalls?.cancel()
        watchInstalls = scope.launch { apps.installed.collect { onInstalled(it) } }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Organizer.setReady(false)
        Log.i("TidyService", "unbound")
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    fun read() {
        scope.launch {
            Organizer.setWork(Work.Reading)
            val layout = drawer.read()
            Organizer.setLastRead(layout)
            Organizer.setWork(if (layout == null) Work.ReadFailed else Work.Idle)
            backToTidy()
        }
    }

    fun organize(target: DrawerLayout, saveUndo: Boolean) {
        scope.launch {
            Organizer.setWork(Work.Reading)
            val current = drawer.read()
            if (current == null) {
                Organizer.setWork(Work.ReadFailed)
                backToTidy()
                return@launch
            }
            val steps = Planner.steps(current, target)
            if (saveUndo && steps.isNotEmpty()) settings.setUndoSnapshot(current)
            val result = Executor(drawer).run(
                steps,
                shouldStop = Organizer::consumeStop,
                onProgress = { done, total ->
                    Organizer.setWork(Work.Organizing(done, total))
                    showProgress(done, total)
                },
            )
            hideProgress()
            if (!saveUndo && result == RunResult.Done) settings.setUndoSnapshot(null)
            Organizer.setLastRead(drawer.read())
            Organizer.setWork(Work.Finished(result, undo = !saveUndo))
            backToTidy()
        }
    }

    /** Files one new app into an existing folder (the new-app helper's "Add"). */
    fun fileNewApp(label: String, folder: String) {
        if (Organizer.busy) return
        scope.launch {
            Organizer.setWork(Work.Organizing(0, 1))
            val ok = drawer.addToFolder(listOf(label), folder)
            Organizer.setLastRead(Organizer.lastRead.value?.let { layout ->
                if (!ok) layout
                else layout.copy(folders = layout.folders.map { if (it.name == folder) it.copy(apps = it.apps + label) else it })
            })
            Organizer.setWork(Work.Idle)
            drawer.leave()
            NewAppNotifier.done(this@OrganizerService, label, folder, ok)
        }
    }

    private suspend fun onInstalled(packageName: String) {
        val mode = settings.newAppMode.first()
        if (mode == NewAppMode.OFF) return
        // Wait for the reload that includes it, for its label.
        val apps = (application as TidyApplication).apps
        val entry = apps.apps.first { list -> list?.any { it.packageName == packageName } == true }
            ?.first { it.packageName == packageName } ?: return
        val folder = Organizer.folderForNewApp(this, entry.label) ?: return
        if (mode == NewAppMode.AUTO) fileNewApp(entry.label, folder) else NewAppNotifier.ask(this, entry.label, folder)
    }

    private suspend fun backToTidy() {
        drawer.leave()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showProgress(done: Int, total: Int) {
        val stop = PendingIntent.getBroadcast(
            this, 0, Intent(this, NewAppReceiver::class.java).setAction(NewAppReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, TidyApplication.CHANNEL_ORGANIZING)
            .setSmallIcon(R.drawable.ic_stat_tidy)
            .setContentTitle(getString(R.string.organizing_title, done, total))
            .setContentText(getString(R.string.organizing_body))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.organizing_stop), stop)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(PROGRESS_ID, notification) }
    }

    private fun hideProgress() {
        getSystemService(NotificationManager::class.java).cancel(PROGRESS_ID)
    }

    companion object {
        private const val PROGRESS_ID = 10

        @Volatile
        var instance: OrganizerService? = null
            private set
    }
}
