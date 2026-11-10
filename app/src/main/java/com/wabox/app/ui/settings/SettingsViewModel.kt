package com.wabox.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wabox.app.AppContainer
import com.wabox.app.core.model.BackgroundRefresh
import com.wabox.app.core.model.LockTimeout
import com.wabox.app.core.model.SaveLocation
import com.wabox.app.core.model.Source
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.core.model.ThemeMode
import com.wabox.app.data.prefs.AppSettings
import com.wabox.app.ui.MessageTone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sources: List<Source> = emptyList(),
    val folders: List<StatusFolder> = emptyList(),
    val savedCount: Int = 0,
    val loaded: Boolean = false,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        c.settings.settings, c.sourceRepository.sources, c.sourceRepository.folders, c.savedRepository.count,
    ) { s, sources, folders, saved -> SettingsUiState(s, sources, folders, saved, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(mode) }
    fun setSaveLocation(l: SaveLocation) = viewModelScope.launch { c.settings.setSaveLocation(l) }
    fun setRefreshOnOpen(v: Boolean) = viewModelScope.launch { c.settings.setRefreshOnOpen(v) }
    fun setLiveUpdates(v: Boolean) = viewModelScope.launch { c.settings.setLiveUpdates(v) }

    /** Only called after the user has authenticated in the UI. */
    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        c.settings.setAppLock(enabled)
        if (!enabled) c.appLock.disabled()
        c.messages.post(if (enabled) "App Lock is on" else "App Lock is off", MessageTone.SUCCESS)
        c.updateWidgets() // the Recent widget hides previews while App Lock is on
    }

    fun setLockTimeout(v: LockTimeout) = viewModelScope.launch { c.settings.setLockTimeout(v) }

    fun postError(text: String) = c.messages.post(text, MessageTone.ERROR)

    fun setBackgroundRefresh(v: BackgroundRefresh) = viewModelScope.launch {
        c.settings.setBackgroundRefresh(v)
        c.scheduler.applyBackgroundRefresh(v)
    }

    fun rename(folderId: Long, label: String) = viewModelScope.launch {
        c.sourceRepository.rename(folderId, label)
        c.updateWidgets()
    }

    fun remove(source: Source) = viewModelScope.launch {
        c.sourceRepository.remove(source.id)
        c.messages.post("Disconnected. WABox no longer has access to that folder.", MessageTone.INFO)
        c.statusRepository.refresh(force = true)
    }

    fun rescan() = viewModelScope.launch {
        val r = c.statusRepository.refresh(force = true)
        c.messages.post(
            if (r.problems > 0) "Scan finished. ${r.problems} source${if (r.problems == 1) "" else "s"} need${if (r.problems == 1) "s" else ""} attention"
            else "All sources are up to date",
            if (r.problems > 0) MessageTone.ERROR else MessageTone.SUCCESS,
        )
    }
}
