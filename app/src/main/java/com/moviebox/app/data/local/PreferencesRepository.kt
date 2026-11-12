package com.moviebox.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moviebox.app.data.model.AppPreferences
import com.moviebox.app.data.model.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moviebox_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val WATCHLIST_IDS = stringSetPreferencesKey("watchlist_ids")
        val TMDB_API_KEY_OVERRIDE = stringPreferencesKey("tmdb_api_key_override")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_VARIANT] ?: ThemeVariant.DEEP_RED.name
        val variant = runCatching { ThemeVariant.valueOf(themeStr) }.getOrDefault(ThemeVariant.DEEP_RED)
        AppPreferences(
            themeVariant = variant,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Auto",
            autoPlayNextEpisode = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            watchlistIds = prefs[Keys.WATCHLIST_IDS] ?: emptySet(),
            tmdbApiKeyOverride = prefs[Keys.TMDB_API_KEY_OVERRIDE] ?: ""
        )
    }

    suspend fun setThemeVariant(variant: ThemeVariant) {
        context.dataStore.edit { it[Keys.THEME_VARIANT] = variant.name }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    suspend fun setAutoPlayNextEpisode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun toggleWatchlist(titleId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WATCHLIST_IDS]?.toMutableSet() ?: mutableSetOf()
            if (titleId in current) current.remove(titleId) else current.add(titleId)
            prefs[Keys.WATCHLIST_IDS] = current
        }
    }

    suspend fun removeFromWatchlist(titleId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WATCHLIST_IDS]?.toMutableSet() ?: mutableSetOf()
            current.remove(titleId)
            prefs[Keys.WATCHLIST_IDS] = current
        }
    }

    suspend fun clearWatchlist() {
        context.dataStore.edit { prefs ->
            prefs[Keys.WATCHLIST_IDS] = emptySet()
        }
    }

    suspend fun setTmdbApiKeyOverride(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TMDB_API_KEY_OVERRIDE] = apiKey.trim()
        }
    }

    fun getCacheSizeBytes(): Long = dirSize(context.cacheDir)

    fun clearAppCache(): Boolean = runCatching {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        true
    }.getOrDefault(false)

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
