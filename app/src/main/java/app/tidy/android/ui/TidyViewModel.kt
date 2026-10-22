package app.tidy.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tidy.android.TidyApplication
import app.tidy.android.data.NewAppMode
import app.tidy.android.data.SettingsRepository
import app.tidy.android.oneui.Organizer
import app.tidy.android.organize.DrawerLayout
import app.tidy.android.organize.Planner
import app.tidy.android.organize.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How to organize: file loose apps into the user's folders, or start over. */
enum class Mode { KEEP, REBUILD }

/** The preview's hand edits on Tidy's proposal. */
data class Edits(val moves: Map<String, String?> = emptyMap(), val renames: Map<String, String> = emptyMap())

data class Preview(val current: DrawerLayout, val target: DrawerLayout, val steps: List<Step>) {
    val newFolders: Set<String> get() = target.folders.map { it.name }.toSet() - current.folders.map { it.name }.toSet()

    /** Apps that end up somewhere other than where they are now. */
    val changedApps: Set<String> by lazy {
        fun where(layout: DrawerLayout, app: String) = layout.folders.firstOrNull { app in it.apps }?.name
        current.allApps.filter { where(current, it) != where(target, it) }.toSet()
    }
}

class TidyViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)
    private val repo = (app as TidyApplication).apps

    val work = Organizer.work
    val lastRead = Organizer.lastRead
    val undoSnapshot: StateFlow<DrawerLayout?> = settings.undoSnapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val newAppMode: StateFlow<NewAppMode> = settings.newAppMode.stateIn(viewModelScope, SharingStarted.Eagerly, NewAppMode.ASK)

    val mode = MutableStateFlow(Mode.KEEP)
    val edits = MutableStateFlow(Edits())

    val preview: StateFlow<Preview?> = combine(lastRead, repo.apps, settings.folderNames, mode, edits) { current, apps, _, mode, edits ->
        if (current == null || apps == null) return@combine null
        val categoryOf = Organizer.categoryOf(apps)
        val nameOf = Organizer.nameOf(getApplication())
        val proposed = when (mode) {
            Mode.KEEP -> Planner.keepFolders(current, categoryOf, nameOf)
            Mode.REBUILD -> Planner.rebuild(current, categoryOf, nameOf)
        }
        val target = Planner.edit(proposed, edits.moves, edits.renames)
        Preview(current, target, Planner.steps(current, target))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setMode(m: Mode) {
        mode.value = m
        edits.value = Edits()
    }

    /** Edits are keyed by Tidy's proposed folder names; the screen shows renamed ones. */
    private fun proposedName(shown: String) = edits.value.renames.entries.firstOrNull { it.value == shown }?.key ?: shown

    fun moveApp(app: String, folder: String?) {
        edits.value = edits.value.copy(moves = edits.value.moves + (app to folder?.let(::proposedName)))
    }

    fun renameFolder(from: String, to: String) {
        edits.value = edits.value.copy(renames = edits.value.renames + (proposedName(from) to to))
    }

    fun read(): Boolean {
        edits.value = Edits()
        return Organizer.read()
    }

    fun apply(): Boolean = preview.value?.let { Organizer.apply(it.target) } ?: false

    fun undo() {
        viewModelScope.launch { Organizer.undo(getApplication()) }
    }

    fun stop() = Organizer.stop()

    fun setNewAppMode(m: NewAppMode) {
        viewModelScope.launch { settings.setNewAppMode(m) }
    }
}
