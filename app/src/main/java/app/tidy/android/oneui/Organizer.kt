package app.tidy.android.oneui

import android.content.Context
import app.tidy.android.TidyApplication
import app.tidy.android.data.AppEntry
import app.tidy.android.data.SettingsRepository
import app.tidy.android.domain.Category
import app.tidy.android.organize.DrawerLayout
import app.tidy.android.organize.Planner
import app.tidy.android.organize.RunResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** What Tidy is doing to the apps screen right now. */
sealed interface Work {
    data object Idle : Work
    data object Reading : Work
    data class Organizing(val done: Int, val total: Int) : Work
    /** The last run ended; shown until the next one starts. */
    data class Finished(val result: RunResult, val undo: Boolean) : Work
    /** The apps screen couldn't be read (not One UI's, or it looked unexpected). */
    data object ReadFailed : Work
}

/**
 * The app-wide face of the organizer: what the UI calls and observes. The
 * screen work itself runs in [OrganizerService], which has to be turned on
 * in Accessibility settings first ([ready]).
 */
object Organizer {
    private val _work = MutableStateFlow<Work>(Work.Idle)
    val work: StateFlow<Work> = _work.asStateFlow()

    private val _lastRead = MutableStateFlow<DrawerLayout?>(null)
    /** The apps screen as last read or left by a run; null until read once. */
    val lastRead: StateFlow<DrawerLayout?> = _lastRead.asStateFlow()

    @Volatile
    private var stopRequested = false

    private val _ready = MutableStateFlow(false)
    /** Whether the accessibility service is connected; follows it being turned on or off. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    internal fun setReady(value: Boolean) {
        _ready.value = value
    }

    val busy: Boolean get() = work.value is Work.Reading || work.value is Work.Organizing

    internal fun setWork(work: Work) {
        _work.value = work
    }

    internal fun setLastRead(layout: DrawerLayout?) {
        _lastRead.value = layout
    }

    internal fun consumeStop(): Boolean = stopRequested

    /** Opens One UI's apps screen, reads every folder, and comes back to Tidy. */
    fun read(): Boolean {
        val service = OrganizerService.instance ?: return false
        if (busy) return false
        service.read()
        return true
    }

    /** Changes the apps screen into [target] (re-read first, so the steps match what's really there). */
    fun apply(target: DrawerLayout): Boolean {
        val service = OrganizerService.instance ?: return false
        if (busy) return false
        stopRequested = false
        service.organize(target, saveUndo = true)
        return true
    }

    /** Puts the apps screen back the way it was before the last run. */
    suspend fun undo(context: Context): Boolean {
        val service = OrganizerService.instance ?: return false
        if (busy) return false
        val snapshot = SettingsRepository(context).undoSnapshot.first() ?: return false
        stopRequested = false
        service.organize(snapshot, saveUndo = false)
        return true
    }

    fun stop() {
        stopRequested = true
    }

    /** One UI label -> Tidy category, from the apps installed on the phone. */
    fun categoryOf(apps: List<AppEntry>): (String) -> Category? {
        val byLabel = apps.groupBy { it.label }.mapValues { (_, es) -> es.first().autoCategory }
        return { label -> byLabel[label] }
    }

    suspend fun nameOf(context: Context): (Category) -> String {
        val names = SettingsRepository(context).folderNames.first()
        return { c -> names[c] ?: context.getString(c.label) }
    }

    /**
     * Where a newly installed app should go: the user's folder for its
     * category, judged from the last read of the apps screen. Null when
     * there's no fitting folder (or the screen was never read).
     */
    fun folderForNewApp(context: Context, label: String): String? {
        val layout = lastRead.value ?: return null
        val apps = (context.applicationContext as TidyApplication).apps.apps.value ?: return null
        val categoryOf = categoryOf(apps)
        val withNew = layout.copy(loose = layout.loose + label)
        val target = Planner.keepFolders(withNew, categoryOf, nameOf = { "" }, minNewFolder = Int.MAX_VALUE)
        return target.folders.firstOrNull { label in it.apps && layout.folder(it.name) != null }?.name
    }
}
