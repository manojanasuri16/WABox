package com.wabox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wabox.app.AppContainer
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.model.Source
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.core.model.StatusItem
import com.wabox.app.ui.MessageTone
import com.wabox.app.ui.feedbackText
import com.wabox.app.ui.feedbackTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loaded: Boolean = false,
    val sources: List<Source> = emptyList(),
    val folders: List<StatusFolder> = emptyList(),
    val activeFolderId: Long = 0,
    val filter: MediaFilter = MediaFilter.ALL,
    val fresh: List<StatusItem> = emptyList(),
    val earlier: List<StatusItem> = emptyList(),
    val refreshing: Boolean = false,
    val saving: Set<String> = emptySet(),
) {
    val activeFolder: StatusFolder? get() = folders.firstOrNull { it.id == activeFolderId }
    val isEmpty: Boolean get() = fresh.isEmpty() && earlier.isEmpty()
    val hasSources: Boolean get() = sources.isNotEmpty()
    val problems: List<Source> get() = sources.filter { it.health != SourceHealth.OK }
    /** Tile badges only make sense when "All" mixes more than one folder. */
    val showSourceBadges: Boolean get() = activeFolderId == 0L && folders.size > 1
    val total: Int get() = fresh.size + earlier.size
}

private data class HomeInputs(
    val sources: List<Source>,
    val folders: List<StatusFolder>,
    val activeFolderId: Long,
    val lastSeenAt: Long,
    val filter: MediaFilter,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(MediaFilter.ALL)

    private val inputs = combine(
        c.sourceRepository.sources,
        c.sourceRepository.folders,
        c.settings.settings.map { it.activeFolderId to it.lastSeenAt }.distinctUntilChanged(),
        filter,
    ) { sources, folders, (active, lastSeen), f ->
        val valid = active.takeIf { id -> folders.any { it.id == id } } ?: 0L
        HomeInputs(sources, folders, valid, lastSeen, f)
    }

    val state: StateFlow<HomeUiState> = inputs.flatMapLatest { input ->
        combine(
            c.statusRepository.observe(input.activeFolderId, input.filter),
            c.statusRepository.refreshing,
            c.savedRepository.saving,
        ) { items, refreshing, saving ->
            // "New" = discovered since the previous visit. On first run everything is simply "Recent".
            val (fresh, earlier) = if (input.lastSeenAt == 0L) emptyList<StatusItem>() to items
            else items.partition { it.firstSeenAt > input.lastSeenAt }
            HomeUiState(
                loaded = true,
                sources = input.sources,
                folders = input.folders,
                activeFolderId = input.activeFolderId,
                filter = input.filter,
                fresh = fresh,
                earlier = earlier,
                refreshing = refreshing,
                saving = saving,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(value: MediaFilter) { filter.value = value }

    fun selectFolder(id: Long) = viewModelScope.launch {
        c.settings.setActiveFolder(id)
        c.updateWidgets()
    }

    fun refresh() = viewModelScope.launch {
        val outcome = c.statusRepository.refresh(force = true)
        when {
            outcome.problems > 0 -> c.messages.post("Some sources need attention", MessageTone.ERROR)
            outcome.added > 0 -> c.messages.post(if (outcome.added == 1) "1 new status" else "${outcome.added} new statuses", MessageTone.SUCCESS)
        }
    }

    /** @return the result so the UI can fire matching haptics. */
    fun save(item: StatusItem, onResult: (SaveResult) -> Unit = {}) = viewModelScope.launch {
        val result = c.savedRepository.saveStatus(item.id)
        c.messages.post(feedbackText(result), feedbackTone(result))
        onResult(result)
    }
}
