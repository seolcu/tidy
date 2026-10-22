package app.tidy.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tidy.android.domain.Category
import app.tidy.android.organize.DrawerLayout
import app.tidy.android.organize.LayoutCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** What to do when an app is installed. */
enum class NewAppMode { ASK, AUTO, OFF }

/**
 * Everything Tidy remembers: the apps screen as it was before the last run
 * (for undo), what to do with new apps, the names Tidy gives its own
 * folders, and the update-check flags the shared `update/` code expects
 * (same names as in WakeLink).
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private fun <T> observe(read: (Preferences) -> T): Flow<T> = store.data.map(read).distinctUntilChanged()

    /** The apps screen before the last run that changed it; null once undone or never run. */
    val undoSnapshot: Flow<DrawerLayout?> = observe { prefs -> prefs[UNDO_SNAPSHOT]?.let(LayoutCodec::decode) }

    suspend fun setUndoSnapshot(layout: DrawerLayout?) {
        store.edit { prefs ->
            if (layout == null) prefs.remove(UNDO_SNAPSHOT) else prefs[UNDO_SNAPSHOT] = LayoutCodec.encode(layout)
        }
    }

    val newAppMode: Flow<NewAppMode> = observe { prefs ->
        prefs[NEW_APP_MODE]?.let { runCatching { NewAppMode.valueOf(it) }.getOrNull() } ?: NewAppMode.ASK
    }
    suspend fun setNewAppMode(mode: NewAppMode) = store.edit { it[NEW_APP_MODE] = mode.name }

    /** Names the user gave Tidy's folders in the preview; the rest use their default label. */
    val folderNames: Flow<Map<Category, String>> = observe { prefs ->
        Category.entries.mapNotNull { c -> prefs[stringPreferencesKey(RENAME_PREFIX + c.name)]?.let { c to it } }.toMap()
    }

    suspend fun setFolderName(category: Category, name: String?) {
        store.edit { prefs ->
            val key = stringPreferencesKey(RENAME_PREFIX + category.name)
            if (name.isNullOrBlank()) prefs.remove(key) else prefs[key] = name.trim()
        }
    }

    val autoUpdateCheck: Flow<Boolean> = observe { it[AUTO_UPDATE_CHECK] ?: true }
    suspend fun setAutoUpdateCheck(value: Boolean) = store.edit { it[AUTO_UPDATE_CHECK] = value }

    /** Last version the background check notified about, so each version notifies once. */
    val notifiedUpdateVersion: Flow<String?> = observe { it[NOTIFIED_UPDATE_VERSION] }
    suspend fun setNotifiedUpdateVersion(value: String) = store.edit { it[NOTIFIED_UPDATE_VERSION] = value }

    /** Version the user chose "skip" for in the startup dialog. */
    val skippedUpdateVersion: Flow<String?> = observe { it[SKIPPED_UPDATE_VERSION] }
    suspend fun setSkippedUpdateVersion(value: String) = store.edit { it[SKIPPED_UPDATE_VERSION] = value }

    private companion object {
        const val RENAME_PREFIX = "rename."
        val UNDO_SNAPSHOT = stringPreferencesKey("undo_snapshot")
        val NEW_APP_MODE = stringPreferencesKey("new_app_mode")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val NOTIFIED_UPDATE_VERSION = stringPreferencesKey("notified_update_version")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
    }
}
