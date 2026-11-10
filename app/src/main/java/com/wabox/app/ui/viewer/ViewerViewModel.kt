package com.wabox.app.ui.viewer

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wabox.app.AppContainer
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.util.Formats
import com.wabox.app.data.media.MediaProbe
import com.wabox.app.data.repo.DeleteResult
import com.wabox.app.ui.MessageTone
import com.wabox.app.ui.appName
import com.wabox.app.ui.feedbackText
import com.wabox.app.ui.feedbackTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewerMode { STATUS, SAVED }

data class ViewerItem(
    val key: String,
    val uri: String,
    val isVideo: Boolean,
    val mimeType: String,
    val size: Long,
    val timestamp: Long,
    val sourceLabel: String,
    val appKind: AppKind,
    val durationMs: Long?,
    val name: String,
    val statusId: String? = null,
    val savedId: Long? = null,
)

data class InfoRow(val label: String, val value: String)

data class ViewerUiState(
    val loaded: Boolean = false,
    val mode: ViewerMode = ViewerMode.STATUS,
    val items: List<ViewerItem> = emptyList(),
    val startIndex: Int = 0,
    val savedStatusIds: Set<String> = emptySet(),
    val presentStatusIds: Set<String>? = null,
    val saving: Set<String> = emptySet(),
)

class ViewerViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val mode: ViewerMode = ViewerMode.valueOf(handle.get<String>("mode") ?: "STATUS")
    private val startId: String = handle.get<String>("startId").orEmpty()
    private val folderId: Long = handle.get<Long>("folderId") ?: 0L
    private val filter: MediaFilter = MediaFilter.valueOf(handle.get<String>("filter") ?: "ALL")

    private val snapshot = MutableStateFlow<Pair<List<ViewerItem>, Int>?>(null)

    private val _info = MutableStateFlow<List<InfoRow>?>(null)
    val info: StateFlow<List<InfoRow>?> = _info.asStateFlow()

    /** Pending system delete-consent request for the UI to launch. */
    private val _consent = MutableStateFlow<Pair<IntentSender, List<Long>>?>(null)
    val consent: StateFlow<Pair<IntentSender, List<Long>>?> = _consent.asStateFlow()

    init {
        viewModelScope.launch { snapshot.value = loadSnapshot() }
    }

    private suspend fun loadSnapshot(): Pair<List<ViewerItem>, Int> = when (mode) {
        ViewerMode.STATUS -> {
            var list = c.statusRepository.list(folderId, filter)
            if (list.none { it.id == startId }) list = c.statusRepository.list(0, MediaFilter.ALL) // e.g. from a widget
            val items = list.map {
                ViewerItem(
                    key = it.id, uri = it.documentUri, isVideo = it.kind == MediaKind.VIDEO, mimeType = it.mimeType,
                    size = it.size, timestamp = it.lastModified, sourceLabel = it.folderLabel, appKind = it.appKind,
                    durationMs = it.durationMs, name = it.name, statusId = it.id,
                )
            }
            items to items.indexOfFirst { it.key == startId }.coerceAtLeast(0)
        }
        ViewerMode.SAVED -> {
            val sort = c.settings.current().savedSort
            val items = c.savedRepository.list(filter, sort).map {
                ViewerItem(
                    key = "s${it.id}", uri = it.mediaUri, isVideo = it.kind == MediaKind.VIDEO, mimeType = it.mimeType,
                    size = it.size, timestamp = it.savedAt, sourceLabel = it.originLabel, appKind = it.originAppKind,
                    durationMs = it.durationMs, name = it.displayName, savedId = it.id,
                )
            }
            items to items.indexOfFirst { it.key == "s$startId" }.coerceAtLeast(0)
        }
    }

    private val liveStatus = if (mode == ViewerMode.STATUS) c.statusRepository.observe(0, MediaFilter.ALL) else flowOf(null)

    val state: StateFlow<ViewerUiState> = combine(snapshot, liveStatus, c.savedRepository.saving) { snap, live, saving ->
        if (snap == null) return@combine ViewerUiState(mode = mode)
        ViewerUiState(
            loaded = true,
            mode = mode,
            items = snap.first,
            startIndex = snap.second,
            savedStatusIds = live?.filter { it.isSaved }?.map { it.id }?.toSet() ?: emptySet(),
            presentStatusIds = live?.map { it.id }?.toSet(),
            saving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState(mode = mode))

    fun save(item: ViewerItem, onResult: (SaveResult) -> Unit) {
        val id = item.statusId ?: return
        viewModelScope.launch {
            val r = c.savedRepository.saveStatus(id)
            c.messages.post(feedbackText(r), feedbackTone(r))
            onResult(r)
        }
    }

    fun share(context: Context, item: ViewerItem) = viewModelScope.launch {
        val intent: Intent? = if (item.savedId != null) {
            c.shareHelper.shareSaved(listOf(Uri.parse(item.uri)), listOf(item.mimeType))
        } else {
            c.shareHelper.shareStatus(Uri.parse(item.uri), item.name, item.mimeType)
        }
        if (intent == null) {
            c.messages.post("This status is no longer on your device", MessageTone.ERROR)
        } else {
            context.startActivity(intent)
        }
    }

    fun delete(item: ViewerItem, onDeleted: () -> Unit) {
        val savedId = item.savedId ?: return
        viewModelScope.launch {
            val model = c.savedRepository.list(MediaFilter.ALL, c.settings.current().savedSort).firstOrNull { it.id == savedId }
            if (model == null) { removeFromSnapshot(item.key); onDeleted(); return@launch }
            when (val r = c.savedRepository.delete(listOf(model))) {
                is DeleteResult.Deleted -> { c.messages.post("Deleted", MessageTone.SUCCESS); removeFromSnapshot(item.key); onDeleted() }
                is DeleteResult.NeedsConsent -> _consent.value = r.intentSender to r.ids
                is DeleteResult.Failed -> c.messages.post(r.reason, MessageTone.ERROR)
            }
        }
    }

    fun onConsentResult(granted: Boolean, onDeleted: () -> Unit) {
        val pending = _consent.value ?: return
        _consent.value = null
        if (!granted) return
        viewModelScope.launch {
            c.savedRepository.confirmDeleted(pending.second)
            pending.second.forEach { removeFromSnapshot("s$it") }
            c.messages.post("Deleted", MessageTone.SUCCESS)
            onDeleted()
        }
    }

    private fun removeFromSnapshot(key: String) = snapshot.update { snap ->
        snap?.let { (items, start) -> items.filterNot { it.key == key } to start }
    }

    fun loadInfo(context: Context, item: ViewerItem) {
        _info.value = baseInfo(item)
        viewModelScope.launch {
            val dims = withContext(Dispatchers.IO) { MediaProbe.dimensions(context, Uri.parse(item.uri), item.isVideo) }
            if (dims != null) _info.update { rows -> rows?.let { it + InfoRow("Dimensions", "${dims.width} × ${dims.height}") } }
        }
    }

    fun dismissInfo() { _info.value = null }

    private fun baseInfo(item: ViewerItem): List<InfoRow> = buildList {
        add(InfoRow("Type", if (item.isVideo) "Video · ${item.mimeType.substringAfter('/').uppercase()}" else "Photo · ${item.mimeType.substringAfter('/').uppercase()}"))
        add(InfoRow("Size", Formats.size(item.size)))
        item.durationMs?.takeIf { it > 0 }?.let { add(InfoRow("Duration", Formats.duration(it))) }
        add(InfoRow(if (mode == ViewerMode.SAVED) "Saved" else "Posted", Formats.dateTime(item.timestamp)))
        add(InfoRow("Source", "${item.sourceLabel} (${appName(item.appKind)})"))
        add(InfoRow("File", item.name))
    }
}

/** Statuses that disappeared from disk while the viewer was open. */
fun ViewerUiState.isMissing(item: ViewerItem): Boolean =
    item.statusId != null && presentStatusIds != null && item.statusId !in presentStatusIds

