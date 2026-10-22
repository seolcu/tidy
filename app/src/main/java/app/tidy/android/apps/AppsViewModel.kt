package app.tidy.android.apps

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tidy.android.TidyApplication
import app.tidy.android.data.AppEntry
import app.tidy.android.data.SettingsRepository
import app.tidy.android.domain.Categorizer
import app.tidy.android.domain.Category
import app.tidy.android.domain.Folder
import app.tidy.android.domain.buildFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Collator

/** An app as shown: where it lives now, and whether the user put it there. */
data class AppItem(val entry: AppEntry, val category: Category, val movedByUser: Boolean)

data class AppsState(
    /** Every app, sorted by label. */
    val all: List<AppItem>,
    val folders: List<Folder<AppItem>>,
    val folderNames: Map<Category, String>,
) {
    fun folderName(context: Context, category: Category): String =
        folderNames[category] ?: context.getString(category.label)

    val customizationCount: Int get() = all.count { it.movedByUser } + folderNames.size
}

/** "Just installed X, put it in Y" -- shown with a "change" action. */
data class InstallNotice(val key: String, val label: String, val category: Category)

enum class ViewMode { FOLDERS, ALL }

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TidyApplication).apps
    private val settings = SettingsRepository(app)

    val query = MutableStateFlow("")
    val viewMode = MutableStateFlow(ViewMode.FOLDERS)
    val openFolder = MutableStateFlow<Category?>(null)
    /** Key of the app whose "move to folder" sheet is open. */
    val moving = MutableStateFlow<String?>(null)

    val onboarded: StateFlow<Boolean?> = settings.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<AppsState?> = combine(repo.apps, settings.overrides, settings.folderNames) { apps, overrides, names ->
        if (apps == null) return@combine null
        val collator = Collator.getInstance()
        val items = apps
            .map { AppItem(it, Categorizer.assign(it.packageName, it.autoCategory, overrides), it.packageName in overrides) }
            .sortedWith(compareBy(collator) { it.entry.label })
        AppsState(
            all = items,
            folders = buildFolders(items, { it.category }, { it.entry.label }),
            folderNames = names,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val notices = Channel<InstallNotice>(Channel.BUFFERED)
    val installNotices: Flow<InstallNotice> = notices.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.installed.collect { pkg -> launch { announceInstall(pkg) } }
        }
    }

    /** Waits for the reload that follows an install, then says where the new app went. */
    private suspend fun announceInstall(packageName: String) {
        val item = withTimeoutOrNull(5_000) {
            state.first { s -> s?.all?.any { it.entry.packageName == packageName } == true }
        }?.all?.firstOrNull { it.entry.packageName == packageName } ?: return
        notices.send(InstallNotice(item.entry.key, item.entry.label, item.category))
    }

    fun item(key: String): AppItem? = state.value?.all?.firstOrNull { it.entry.key == key }

    /** Moving back to the automatic folder clears the override instead of pinning it. */
    fun moveTo(key: String, category: Category) {
        val item = item(key) ?: return
        moving.value = null
        viewModelScope.launch {
            settings.setOverride(item.entry.packageName, category.takeIf { it != item.entry.autoCategory })
        }
    }

    fun renameFolder(category: Category, name: String) {
        val default = getApplication<Application>().getString(category.label)
        viewModelScope.launch { settings.setFolderName(category, name.takeIf { it.trim() != default }) }
    }

    fun resetCustomizations() {
        viewModelScope.launch { settings.resetCustomizations() }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settings.setOnboarded() }
    }

    /** Back to a clean apps screen -- the home button while already home. */
    fun resetUi() {
        query.value = ""
        openFolder.value = null
        moving.value = null
    }
}
