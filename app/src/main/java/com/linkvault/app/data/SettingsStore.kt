package com.linkvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkvault.app.util.OpenMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val openMode: OpenMode = OpenMode.SMART,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val lastBackupAt: Long = 0L,
    val clipboardSuggestions: Boolean = true,
    val showMostUsed: Boolean = true,
    val fetchTitles: Boolean = true,
    val downloadQuality: DownloadQuality = DownloadQuality.BEST,
    val floatingButton: Boolean = false
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val openMode = stringPreferencesKey("open_mode")
        val lock = booleanPreferencesKey("app_lock")
        val bio = booleanPreferencesKey("biometric")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val lastBackup = longPreferencesKey("last_backup")
        val clip = booleanPreferencesKey("clipboard_suggestions")
        val mostUsed = booleanPreferencesKey("show_most_used")
        val fetchTitles = booleanPreferencesKey("fetch_titles")
        val downloadQuality = stringPreferencesKey("download_quality")
        val floatingButton = booleanPreferencesKey("floating_button")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[K.theme] } ?: ThemeMode.LIGHT,
            openMode = OpenMode.fromKey(p[K.openMode]),
            appLockEnabled = p[K.lock] ?: false,
            biometricEnabled = p[K.bio] ?: false,
            pinHash = p[K.pinHash].orEmpty(),
            pinSalt = p[K.pinSalt].orEmpty(),
            lastBackupAt = p[K.lastBackup] ?: 0L,
            clipboardSuggestions = p[K.clip] ?: true,
            showMostUsed = p[K.mostUsed] ?: true,
            fetchTitles = p[K.fetchTitles] ?: true,
            downloadQuality = DownloadQuality.fromKey(p[K.downloadQuality]),
            floatingButton = p[K.floatingButton] ?: false
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setOpenMode(v: OpenMode) = edit { it[K.openMode] = v.name }
    suspend fun setBiometric(v: Boolean) = edit { it[K.bio] = v }
    suspend fun setLastBackup(t: Long) = edit { it[K.lastBackup] = t }
    suspend fun setClipboardSuggestions(v: Boolean) = edit { it[K.clip] = v }
    suspend fun setShowMostUsed(v: Boolean) = edit { it[K.mostUsed] = v }
    suspend fun setFetchTitles(v: Boolean) = edit { it[K.fetchTitles] = v }
    suspend fun setDownloadQuality(v: DownloadQuality) = edit { it[K.downloadQuality] = v.name }
    suspend fun setFloatingButton(v: Boolean) = edit { it[K.floatingButton] = v }

    suspend fun enableLock(hash: String, salt: String) = edit {
        it[K.lock] = true; it[K.pinHash] = hash; it[K.pinSalt] = salt
    }

    suspend fun disableLock() = edit {
        it[K.lock] = false; it[K.bio] = false; it[K.pinHash] = ""; it[K.pinSalt] = ""
    }
}
