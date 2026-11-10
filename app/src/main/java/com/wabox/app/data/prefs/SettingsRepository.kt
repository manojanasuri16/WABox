package com.wabox.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wabox.app.core.model.BackgroundRefresh
import com.wabox.app.core.model.LockTimeout
import com.wabox.app.core.model.SaveLocation
import com.wabox.app.core.model.SortOrder
import com.wabox.app.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val saveLocation: SaveLocation = SaveLocation.PICTURES,
    val refreshOnOpen: Boolean = true,
    val liveUpdates: Boolean = true,
    val backgroundRefresh: BackgroundRefresh = BackgroundRefresh.OFF,
    val activeFolderId: Long = 0L,
    val savedSort: SortOrder = SortOrder.NEWEST,
    val lastSeenAt: Long = 0L,
    val lastRefreshAt: Long = 0L,
    val widgetRefreshing: Boolean = false,
    val lastStoppedAt: Long = 0L,
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val saveLocation = stringPreferencesKey("save_location")
        val refreshOnOpen = booleanPreferencesKey("refresh_on_open")
        val liveUpdates = booleanPreferencesKey("live_updates")
        val background = stringPreferencesKey("background_refresh")
        val activeFolder = longPreferencesKey("active_folder")
        val savedSort = stringPreferencesKey("saved_sort")
        val lastSeen = longPreferencesKey("last_seen_at")
        val lastRefresh = longPreferencesKey("last_refresh_at")
        val widgetRefreshing = booleanPreferencesKey("widget_refreshing")
        val lastStopped = longPreferencesKey("last_stopped_at")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = stringPreferencesKey("lock_timeout")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            themeMode = p.enum(Keys.theme, ThemeMode.SYSTEM),
            saveLocation = p.enum(Keys.saveLocation, SaveLocation.PICTURES),
            refreshOnOpen = p[Keys.refreshOnOpen] ?: true,
            liveUpdates = p[Keys.liveUpdates] ?: true,
            backgroundRefresh = p.enum(Keys.background, BackgroundRefresh.OFF),
            activeFolderId = p[Keys.activeFolder] ?: 0L,
            savedSort = p.enum(Keys.savedSort, SortOrder.NEWEST),
            lastSeenAt = p[Keys.lastSeen] ?: 0L,
            lastRefreshAt = p[Keys.lastRefresh] ?: 0L,
            widgetRefreshing = p[Keys.widgetRefreshing] ?: false,
            lastStoppedAt = p[Keys.lastStopped] ?: 0L,
            appLock = p[Keys.appLock] ?: false,
            lockTimeout = p.enum(Keys.lockTimeout, LockTimeout.ONE_MINUTE),
        )
    }.distinctUntilChanged()

    val activeFolderId: Flow<Long> = settings.map { it.activeFolderId }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[Keys.onboarding] = done }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setSaveLocation(location: SaveLocation) = store.edit { it[Keys.saveLocation] = location.name }
    suspend fun setRefreshOnOpen(enabled: Boolean) = store.edit { it[Keys.refreshOnOpen] = enabled }
    suspend fun setLiveUpdates(enabled: Boolean) = store.edit { it[Keys.liveUpdates] = enabled }
    suspend fun setBackgroundRefresh(value: BackgroundRefresh) = store.edit { it[Keys.background] = value.name }
    suspend fun setActiveFolder(id: Long) = store.edit { it[Keys.activeFolder] = id }
    suspend fun setSavedSort(order: SortOrder) = store.edit { it[Keys.savedSort] = order.name }
    suspend fun setLastSeen(at: Long) = store.edit { it[Keys.lastSeen] = at }
    suspend fun setLastRefresh(at: Long) = store.edit { it[Keys.lastRefresh] = at }
    suspend fun setWidgetRefreshing(value: Boolean) = store.edit { it[Keys.widgetRefreshing] = value }
    suspend fun setAppLock(enabled: Boolean) = store.edit { it[Keys.appLock] = enabled }
    suspend fun setLockTimeout(value: LockTimeout) = store.edit { it[Keys.lockTimeout] = value.name }
    suspend fun setLastStopped(at: Long) = store.edit { it[Keys.lastStopped] = at }
}
