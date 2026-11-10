package com.wabox.app.ui.saved

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wabox.app.AppContainer
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.SaveLocation
import com.wabox.app.core.model.SavedItem
import com.wabox.app.core.model.SortOrder
import com.wabox.app.core.util.Formats
import com.wabox.app.data.repo.DeleteResult
import com.wabox.app.ui.MessageTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedSection(val title: String, val items: List<SavedItem>)

data class SavedUiState(
    val loaded: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val sort: SortOrder = SortOrder.NEWEST,
    val sections: List<SavedSection> = emptyList(),
    val selection: Set<Long>? = null,
    val location: SaveLocation = SaveLocation.PICTURES,
    val totalSaved: Int = 0,
) {
    val items: List<SavedItem> get() = sections.flatMap { it.items }
    val selecting: Boolean get() = selection != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(MediaFilter.ALL)
    private val selection = MutableStateFlow<Set<Long>?>(null)
    private val _consent = MutableStateFlow<Pair<IntentSender, List<Long>>?>(null)
    val consent: StateFlow<Pair<IntentSender, List<Long>>?> = _consent.asStateFlow()

    private val prefs = c.settings.settings.map { it.savedSort to it.saveLocation }.distinctUntilChanged()

    val state: StateFlow<SavedUiState> = combine(filter, prefs) { f, p -> f to p }.flatMapLatest { (f, p) ->
        combine(c.savedRepository.observe(f, p.first), selection, c.savedRepository.count) { items, sel, total ->
            val sections = items.groupBy { Formats.daySection(it.savedAt) }.map { (title, list) -> SavedSection(title, list) }
            SavedUiState(true, f, p.first, sections, sel?.intersect(items.map { it.id }.toSet()), p.second, total)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    init {
        // Remove entries whose gallery copy was deleted in another app.
        viewModelScope.launch { c.savedRepository.reconcile() }
    }

    fun setFilter(value: MediaFilter) { filter.value = value }
    fun setSort(value: SortOrder) = viewModelScope.launch { c.settings.setSavedSort(value) }

    fun startSelection(id: Long? = null) { selection.value = setOfNotNull(id) }
    fun endSelection() { selection.value = null }
    fun toggle(id: Long) = selection.update { s -> s?.let { if (id in it) it - id else it + id } }
    fun selectAll() { selection.value = state.value.items.map { it.id }.toSet() }

    private fun selectedItems(): List<SavedItem> {
        val ids = selection.value ?: return emptyList()
        return state.value.items.filter { it.id in ids }
    }

    fun shareSelected(context: Context) {
        val items = selectedItems().ifEmpty { return }
        context.startActivity(c.shareHelper.shareSaved(items.map { Uri.parse(it.mediaUri) }, items.map { it.mimeType }))
    }

    fun deleteSelected() = viewModelScope.launch {
        val items = selectedItems().ifEmpty { return@launch }
        when (val r = c.savedRepository.delete(items)) {
            is DeleteResult.Deleted -> {
                c.messages.post(if (r.count == 1) "Deleted 1 item" else "Deleted ${r.count} items", MessageTone.SUCCESS)
                endSelection()
            }
            is DeleteResult.NeedsConsent -> _consent.value = r.intentSender to r.ids
            is DeleteResult.Failed -> c.messages.post(r.reason, MessageTone.ERROR)
        }
    }

    fun onConsentResult(granted: Boolean) = viewModelScope.launch {
        val pending = _consent.value ?: return@launch
        _consent.value = null
        if (granted) {
            c.savedRepository.confirmDeleted(pending.second)
            c.messages.post("Deleted", MessageTone.SUCCESS)
            endSelection()
        }
    }
}
