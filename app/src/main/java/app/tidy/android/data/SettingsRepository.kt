package app.tidy.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tidy.android.domain.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Everything Tidy remembers: the user's manual moves (package -> folder),
 * renamed folders, onboarding, and the update-check flags the shared
 * `update/` code expects (same names as in WakeLink).
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private fun <T> observe(read: (Preferences) -> T): Flow<T> = store.data.map(read).distinctUntilChanged()

    /** Package name -> folder the user moved it to. */
    val overrides: Flow<Map<String, Category>> = observe { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val pkg = key.name.removePrefix(OVERRIDE_PREFIX).takeIf { key.name.startsWith(OVERRIDE_PREFIX) }
            val category = (value as? String)?.let(Category::fromName)
            if (pkg != null && category != null) pkg to category else null
        }.toMap()
    }

    suspend fun setOverride(packageName: String, category: Category?) {
        store.edit { prefs ->
            val key = stringPreferencesKey(OVERRIDE_PREFIX + packageName)
            if (category == null) prefs.remove(key) else prefs[key] = category.name
        }
    }

    /** Folders the user renamed; the rest use their default label. */
    val folderNames: Flow<Map<Category, String>> = observe { prefs ->
        Category.entries.mapNotNull { c -> prefs[stringPreferencesKey(RENAME_PREFIX + c.name)]?.let { c to it } }.toMap()
    }

    suspend fun setFolderName(category: Category, name: String?) {
        store.edit { prefs ->
            val key = stringPreferencesKey(RENAME_PREFIX + category.name)
            if (name.isNullOrBlank()) prefs.remove(key) else prefs[key] = name.trim()
        }
    }

    /** Drops every manual move and folder name. */
    suspend fun resetCustomizations() {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(OVERRIDE_PREFIX) || it.name.startsWith(RENAME_PREFIX) }
                .forEach { prefs.remove(it) }
        }
    }

    val onboarded: Flow<Boolean> = observe { it[ONBOARDED] ?: false }
    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = true }

    val autoUpdateCheck: Flow<Boolean> = observe { it[AUTO_UPDATE_CHECK] ?: true }
    suspend fun setAutoUpdateCheck(value: Boolean) = store.edit { it[AUTO_UPDATE_CHECK] = value }

    /** Last version the background check notified about, so each version notifies once. */
    val notifiedUpdateVersion: Flow<String?> = observe { it[NOTIFIED_UPDATE_VERSION] }
    suspend fun setNotifiedUpdateVersion(value: String) = store.edit { it[NOTIFIED_UPDATE_VERSION] = value }

    /** Version the user chose "skip" for in the startup dialog. */
    val skippedUpdateVersion: Flow<String?> = observe { it[SKIPPED_UPDATE_VERSION] }
    suspend fun setSkippedUpdateVersion(value: String) = store.edit { it[SKIPPED_UPDATE_VERSION] = value }

    private companion object {
        const val OVERRIDE_PREFIX = "override."
        const val RENAME_PREFIX = "rename."
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val NOTIFIED_UPDATE_VERSION = stringPreferencesKey("notified_update_version")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
    }
}
