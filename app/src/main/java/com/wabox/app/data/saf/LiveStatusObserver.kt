package com.wabox.app.data.saf

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.data.db.WABoxDatabase
import com.wabox.app.data.prefs.SettingsRepository
import com.wabox.app.data.repo.StatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground-only live updates. ExternalStorageProvider attaches a FileObserver to a directory
 * for as long as a child-documents cursor on it stays open, and notifies that cursor (AOSP
 * FileSystemProvider.DirectoryCursor). We hold one cursor per status folder while the app is
 * visible, then rescan with a debounce. Everything is released in onStop. There is no
 * background polling here.
 */
@OptIn(FlowPreview::class)
class LiveStatusObserver(
    private val resolver: ContentResolver,
    private val db: WABoxDatabase,
    private val statusRepository: StatusRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val cursors = mutableListOf<Pair<Cursor, ContentObserver>>()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var job: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStart(owner: LifecycleOwner) {
        job?.cancel()
        job = scope.launch {
            // A visit only ends after a real absence (persisted, so it survives process death).
            // Round-trips to the system picker or share sheet must not turn everything into "Earlier".
            val s = settings.current()
            if (s.lastStoppedAt > s.lastSeenAt && System.currentTimeMillis() - s.lastStoppedAt > NEW_VISIT_AFTER_MS) {
                settings.setLastSeen(s.lastStoppedAt)
            }
            launch {
                if (settings.current().refreshOnOpen) statusRepository.refresh(force = false)
            }
            launch {
                changes.debounce(DEBOUNCE_MS).collect { statusRepository.refresh(force = true) }
            }
            val watched = combine(settings.settings.map { it.liveUpdates }.distinctUntilChanged(), db.folders().observeRows()) { enabled, rows ->
                if (!enabled) emptyList() else rows.filter { it.sourceHealth != SourceHealth.ACCESS_LOST }.map { it.folder.sourceId to it.folder.documentId }
            }.distinctUntilChanged()
            watched.collectLatest { targets -> rearm(targets) }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
        closeAll()
        val at = System.currentTimeMillis()
        scope.launch { settings.setLastStopped(at) }
    }

    private suspend fun rearm(targets: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        closeAll()
        if (targets.isEmpty()) return@withContext
        val sources = db.sources().getAll().associateBy { it.id }
        for ((sourceId, documentId) in targets) {
            val tree = sources[sourceId]?.treeUri?.let(Uri::parse) ?: continue
            try {
                val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                val cursor = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null) ?: continue
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        changes.tryEmit(Unit)
                    }
                }
                cursor.registerContentObserver(observer)
                synchronized(cursors) { cursors += cursor to observer }
            } catch (e: Exception) {
                Log.w(TAG, "Can't observe $documentId", e)
            }
        }
    }

    private fun closeAll() = synchronized(cursors) {
        cursors.forEach { (c, o) ->
            runCatching { c.unregisterContentObserver(o) }
            runCatching { c.close() }
        }
        cursors.clear()
    }

    companion object {
        private const val TAG = "LiveStatusObserver"
        private const val DEBOUNCE_MS = 750L
        private const val NEW_VISIT_AFTER_MS = 60_000L
    }
}
