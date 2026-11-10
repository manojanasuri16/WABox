package com.wabox.app.data.repo

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.model.SavedItem
import com.wabox.app.core.model.SortOrder
import com.wabox.app.data.db.WABoxDatabase
import com.wabox.app.data.media.MediaSaver
import com.wabox.app.data.media.toModel
import com.wabox.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface DeleteResult {
    data class Deleted(val count: Int) : DeleteResult
    /** Android needs the user's confirmation (files not owned by this install, e.g. after reinstall). */
    data class NeedsConsent(val intentSender: IntentSender, val ids: List<Long>) : DeleteResult
    data class Failed(val reason: String) : DeleteResult
}

class SavedRepository(
    private val context: Context,
    private val db: WABoxDatabase,
    private val saver: MediaSaver,
    private val settings: SettingsRepository,
    private val onDataChanged: suspend () -> Unit,
) {
    private val savedDao = db.saved()
    private val statusDao = db.statuses()
    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    /** Status IDs currently being copied, shared by app UI and widget saves. */
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    val count: Flow<Int> = savedDao.observeCount()

    fun observe(filter: MediaFilter, sort: SortOrder): Flow<List<SavedItem>> =
        savedDao.observe(filter.sqlKind, sort == SortOrder.OLDEST).map { list -> list.map { it.toModel() } }

    suspend fun list(filter: MediaFilter, sort: SortOrder): List<SavedItem> =
        savedDao.list(filter.sqlKind, sort == SortOrder.OLDEST).map { it.toModel() }

    suspend fun saveStatus(statusId: String): SaveResult {
        if (statusId in _saving.value) return SaveResult.AlreadySaved
        val row = statusDao.getRow(statusId) ?: return SaveResult.SourceMissing
        _saving.update { it + statusId }
        return try {
            val result = saver.save(row.status, row.folderLabel, row.appKind, settings.current().saveLocation)
            if (result is SaveResult.Saved) runCatching { onDataChanged() }
            result
        } finally {
            _saving.update { it - statusId }
        }
    }

    /** Drops rows whose gallery copy was deleted outside WABox. */
    suspend fun reconcile(): Int = withContext(Dispatchers.IO) {
        val all = savedDao.all()
        if (all.isEmpty()) return@withContext 0
        val present = saver.existing(all.map { Uri.parse(it.mediaUri) })
        val gone = all.filter { Uri.parse(it.mediaUri) !in present }.map { it.id }
        if (gone.isNotEmpty()) {
            savedDao.delete(gone)
            runCatching { onDataChanged() }
        }
        gone.size
    }

    suspend fun delete(items: List<SavedItem>): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val removed = ArrayList<Long>()
        val needConsent = ArrayList<SavedItem>()
        var recoverable: RecoverableSecurityException? = null
        for (item in items) {
            val uri = Uri.parse(item.mediaUri)
            try {
                resolver.delete(uri, null, null)
                removed += item.id // 0 rows = already gone; either way the row is stale now
            } catch (e: SecurityException) {
                needConsent += item
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) recoverable = e
            } catch (e: Exception) {
                Log.w(TAG, "Delete failed for $uri", e)
            }
        }
        if (removed.isNotEmpty()) {
            savedDao.delete(removed)
            runCatching { onDataChanged() }
        }
        when {
            needConsent.isEmpty() && removed.size == items.size -> DeleteResult.Deleted(removed.size)
            needConsent.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val sender = MediaStore.createDeleteRequest(resolver, needConsent.map { Uri.parse(it.mediaUri) }).intentSender
                DeleteResult.NeedsConsent(sender, needConsent.map { it.id })
            }
            recoverable != null -> DeleteResult.NeedsConsent(recoverable.userAction.actionIntent.intentSender, needConsent.take(1).map { it.id })
            else -> DeleteResult.Failed("Some items couldn't be deleted.")
        }
    }

    /** After the system consent dialog: drop rows whose files are now gone. */
    suspend fun confirmDeleted(ids: List<Long>) = withContext(Dispatchers.IO) {
        reconcile()
    }

    companion object {
        private const val TAG = "SavedRepository"
    }
}
