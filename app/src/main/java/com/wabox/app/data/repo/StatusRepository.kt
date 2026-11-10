package com.wabox.app.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusItem
import com.wabox.app.core.util.StatusPaths
import com.wabox.app.data.db.StatusCounts
import com.wabox.app.data.db.StatusRow
import com.wabox.app.data.db.WABoxDatabase
import com.wabox.app.data.media.MediaProbe
import com.wabox.app.data.media.ThumbnailCache
import com.wabox.app.data.prefs.SettingsRepository
import com.wabox.app.data.saf.SafScanner
import com.wabox.app.data.saf.SourceGrants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

data class RefreshOutcome(val added: Int, val removed: Int, val problems: Int, val skipped: Boolean = false)

/**
 * Discovery engine. Scans every granted source, diffs against Room, keeps widget thumbnails
 * in step, and notifies widgets. All refresh triggers funnel through [refresh], serialised by a mutex.
 */
class StatusRepository(
    private val context: Context,
    private val db: WABoxDatabase,
    private val scanner: SafScanner,
    private val grants: SourceGrants,
    private val sources: SourceRepository,
    private val thumbnails: ThumbnailCache,
    private val settings: SettingsRepository,
    private val onDataChanged: suspend () -> Unit,
) {
    private val statusDao = db.statuses()
    private val folderDao = db.folders()
    private val mutex = Mutex()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    @Volatile private var lastRefreshAt = 0L

    fun observe(folderId: Long, filter: MediaFilter): Flow<List<StatusItem>> =
        statusDao.observe(folderId, filter.sqlKind).map { rows -> rows.map { it.toModel() } }

    fun observeLatest(folderId: Long, limit: Int): Flow<List<StatusItem>> =
        statusDao.observeLatest(folderId, limit).map { rows -> rows.map { it.toModel() } }

    fun observeCounts(folderId: Long, since: Long): Flow<StatusCounts> = statusDao.observeCounts(folderId, since)

    suspend fun list(folderId: Long, filter: MediaFilter): List<StatusItem> =
        statusDao.list(folderId, filter.sqlKind).map { it.toModel() }

    suspend fun get(id: String): StatusItem? = statusDao.getRow(id)?.toModel()

    /** @param force false = skip if a refresh finished within the throttle window. */
    suspend fun refresh(force: Boolean = true): RefreshOutcome {
        if (!force && System.currentTimeMillis() - lastRefreshAt < THROTTLE_MS) {
            return RefreshOutcome(0, 0, 0, skipped = true)
        }
        return mutex.withLock {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) { scanAll() }
            } finally {
                lastRefreshAt = System.currentTimeMillis()
                _refreshing.value = false
            }
        }
    }

    private suspend fun scanAll(): RefreshOutcome {
        val now = System.currentTimeMillis()
        var added = 0
        var removed = 0
        var problems = 0
        for (source in sources.allSources()) {
            val treeUri = Uri.parse(source.treeUri)
            val health = if (!grants.isGranted(treeUri)) {
                SourceHealth.ACCESS_LOST
            } else try {
                val found = scanner.locateStatusFolders(treeUri, source.rootDocumentId)
                sources.syncFolders(source, found)
                for (folder in folderDao.forSource(source.id)) {
                    val scanned = scanner.listChildren(treeUri, folder.documentId).mapNotNull { e ->
                        if (e.isDirectory) return@mapNotNull null
                        val kind = StatusPaths.mediaKindOf(e.name, e.mimeType) ?: return@mapNotNull null
                        if (e.size <= 0L) return@mapNotNull null // still being written by WhatsApp
                        ScannedFile(e.documentId, e.name, StatusPaths.mimeFor(e.name, e.mimeType, kind), kind, e.size, e.lastModified)
                    }
                    val diff = StatusDiff.compute(folder.id, statusDao.forFolder(folder.id), scanned, now) { docId ->
                        scanner.documentUri(treeUri, docId).toString()
                    }
                    if (diff.isEmpty) continue
                    val withMeta = diff.upserts.map { e ->
                        if (e.isVideo && e.durationMs == null) e.copy(durationMs = MediaProbe.videoDurationMs(context, Uri.parse(e.documentUri))) else e
                    }
                    // Thumbnails before insert, so widgets never render a tile without an image.
                    diff.added.sortedByDescending { it.lastModified }.take(MAX_NEW_THUMBS).forEach {
                        thumbnails.ensure(it.id, Uri.parse(it.documentUri), it.isVideo)
                    }
                    statusDao.applyDiff(withMeta, diff.deletes)
                    added += diff.added.size
                    removed += diff.deletes.size
                }
                if (found.isEmpty()) SourceHealth.NO_STATUS_FOLDER else SourceHealth.OK
            } catch (e: SecurityException) {
                Log.w(TAG, "Access lost: ${source.displayPath}", e); SourceHealth.ACCESS_LOST
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "Folder gone: ${source.displayPath}", e); SourceHealth.ACCESS_LOST
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Bad tree: ${source.displayPath}", e); SourceHealth.ACCESS_LOST
            }
            if (health == SourceHealth.ACCESS_LOST) {
                // Unreachable files can't be shown; keep folders and labels so a reconnect restores them.
                folderDao.forSource(source.id).forEach { f ->
                    val ids = statusDao.forFolder(f.id).map { it.id }
                    if (ids.isNotEmpty()) { statusDao.applyDiff(emptyList(), ids); removed += ids.size }
                }
            }
            if (health != SourceHealth.OK) problems++
            sources.setHealth(source.id, health)
        }

        // Widgets show at most a dozen tiles per source; keep the newest few dozen warm.
        statusDao.newest(WIDGET_THUMBS).forEach { thumbnails.ensure(it.id, Uri.parse(it.documentUri), it.isVideo) }
        thumbnails.prune(statusDao.allIds().toSet())

        settings.setLastRefresh(System.currentTimeMillis())
        runCatching { onDataChanged() }.onFailure { Log.w(TAG, "Widget update failed", it) }
        return RefreshOutcome(added, removed, problems)
    }

    companion object {
        private const val TAG = "StatusRepository"
        private const val THROTTLE_MS = 20_000L
        private const val MAX_NEW_THUMBS = 40
        private const val WIDGET_THUMBS = 36
    }
}

fun StatusRow.toModel() = StatusItem(
    id = status.id,
    folderId = status.folderId,
    documentUri = status.documentUri,
    name = status.name,
    mimeType = status.mimeType,
    kind = if (status.isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
    size = status.size,
    lastModified = status.lastModified,
    durationMs = status.durationMs,
    firstSeenAt = status.firstSeenAt,
    folderLabel = folderLabel,
    appKind = appKind,
    isSaved = isSaved,
)
