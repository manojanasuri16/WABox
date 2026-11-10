package com.wabox.app.data.repo

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.wabox.app.core.model.AddSourceResult
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.Source
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.core.util.StatusPaths
import com.wabox.app.data.db.FolderRow
import com.wabox.app.data.db.SourceEntity
import com.wabox.app.data.db.StatusFolderEntity
import com.wabox.app.data.db.WABoxDatabase
import com.wabox.app.data.prefs.SettingsRepository
import com.wabox.app.data.saf.FoundFolder
import com.wabox.app.data.saf.SafScanner
import com.wabox.app.data.saf.SourceGrants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Granted folders and the `.Statuses` folders discovered inside them. */
class SourceRepository(
    private val db: WABoxDatabase,
    private val scanner: SafScanner,
    private val grants: SourceGrants,
    private val settings: SettingsRepository,
) {
    private val sourceDao = db.sources()
    private val folderDao = db.folders()

    val sources: Flow<List<Source>> = sourceDao.observeAll().map { list -> list.map { it.toModel() } }
    val folders: Flow<List<StatusFolder>> = folderDao.observeRows().map { rows -> rows.map { it.toModel() } }

    suspend fun folderRows(): List<StatusFolder> = folderDao.getRows().map { it.toModel() }

    suspend fun addFromTree(treeUri: Uri, reconnectSourceId: Long? = null): AddSourceResult = withContext(Dispatchers.IO) {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            return@withContext AddSourceResult.Failed("That isn't a folder WABox can read.")
        }
        if (!grants.persist(treeUri)) {
            return@withContext AddSourceResult.Failed("Android didn't let WABox keep access to that folder.")
        }
        val kind = StatusPaths.classify(rootId)
        val found = try {
            scanner.locateStatusFolders(treeUri, rootId)
        } catch (e: Exception) {
            Log.w(TAG, "Discovery failed for $treeUri", e)
            grants.release(treeUri)
            return@withContext AddSourceResult.Failed("WABox couldn't read that folder.")
        }
        if (found.isEmpty() && kind == AppKind.OTHER) {
            grants.release(treeUri)
            return@withContext AddSourceResult.NotAStatusFolder
        }
        val health = if (found.isEmpty()) SourceHealth.NO_STATUS_FOLDER else SourceHealth.OK
        val now = System.currentTimeMillis()
        val existing = sourceDao.getAll()

        existing.firstOrNull { it.treeUri == treeUri.toString() }?.let { same ->
            sourceDao.setHealth(same.id, health, now)
            syncFolders(same, found)
            return@withContext if (reconnectSourceId != null || same.health == SourceHealth.ACCESS_LOST) {
                AddSourceResult.Reconnected(same.appKind)
            } else {
                AddSourceResult.AlreadyConnected(same.appKind)
            }
        }
        existing.firstOrNull {
            StatusPaths.isWithin(rootId, it.rootDocumentId) && it.health != SourceHealth.ACCESS_LOST && it.id != reconnectSourceId
        }?.let { parent ->
            grants.release(treeUri)
            return@withContext AddSourceResult.AlreadyConnected(parent.appKind)
        }

        // Replace grants that the new one supersedes, carrying custom labels across.
        val superseded = existing.filter {
            it.id == reconnectSourceId || StatusPaths.isWithin(it.rootDocumentId, rootId) ||
                (StatusPaths.isWithin(rootId, it.rootDocumentId) && it.health == SourceHealth.ACCESS_LOST)
        }
        val carriedLabels = HashMap<String, String>()
        superseded.forEach { old ->
            folderDao.forSource(old.id).filter { it.customLabel }.forEach { carriedLabels[it.documentId] = it.label }
            removeInternal(old, releaseGrant = old.treeUri != treeUri.toString())
        }

        val entity = SourceEntity(
            treeUri = treeUri.toString(),
            rootDocumentId = rootId,
            appKind = kind,
            displayPath = StatusPaths.displayPath(rootId),
            health = health,
            addedAt = now,
            lastScanAt = now,
        )
        val id = sourceDao.insert(entity)
        syncFolders(entity.copy(id = id), found, carriedLabels)
        if (superseded.isNotEmpty()) AddSourceResult.Reconnected(kind) else AddSourceResult.Added(kind, found.size)
    }

    /** Reconciles the folder rows of [source] with a fresh discovery. Returns true if anything changed. */
    suspend fun syncFolders(
        source: SourceEntity,
        found: List<FoundFolder>,
        carriedLabels: Map<String, String> = emptyMap(),
    ): Boolean {
        val current = folderDao.forSource(source.id)
        val foundIds = found.map { it.documentId }.toSet()
        val gone = current.filter { it.documentId !in foundIds }.map { it.id }
        if (gone.isNotEmpty()) {
            folderDao.delete(gone)
            val active = settings.current().activeFolderId
            if (active in gone) settings.setActiveFolder(0)
        }
        val known = current.map { it.documentId }.toSet()
        val rootName = StatusPaths.pathOf(source.rootDocumentId).substringAfterLast('/')
        var added = 0
        found.filter { it.documentId !in known }.forEachIndexed { index, f ->
            val ordinal = folderDao.countOfKind(source.appKind)
            val label = carriedLabels[f.documentId] ?: StatusPaths.defaultLabel(source.appKind, ordinal, rootName)
            folderDao.insert(
                StatusFolderEntity(
                    sourceId = source.id,
                    documentId = f.documentId,
                    relativePath = f.relativePath,
                    appKind = source.appKind,
                    label = label,
                    customLabel = carriedLabels.containsKey(f.documentId),
                    position = current.size + index,
                )
            )
            added++
        }
        return gone.isNotEmpty() || added > 0
    }

    suspend fun rename(folderId: Long, label: String) {
        val clean = label.trim().take(40)
        if (clean.isNotEmpty()) folderDao.rename(folderId, clean)
    }

    suspend fun remove(sourceId: Long) = withContext(Dispatchers.IO) {
        sourceDao.get(sourceId)?.let { removeInternal(it, releaseGrant = true) }
    }

    private suspend fun removeInternal(source: SourceEntity, releaseGrant: Boolean) {
        val folderIds = folderDao.forSource(source.id).map { it.id }
        if (releaseGrant) grants.release(Uri.parse(source.treeUri))
        sourceDao.delete(source.id)
        if (settings.current().activeFolderId in folderIds) settings.setActiveFolder(0)
    }

    suspend fun setHealth(id: Long, health: SourceHealth) = sourceDao.setHealth(id, health, System.currentTimeMillis())

    suspend fun allSources(): List<SourceEntity> = sourceDao.getAll()

    /** True if access is still persisted *and* the root is still readable. */
    fun verify(source: SourceEntity): SourceHealth {
        val uri = Uri.parse(source.treeUri)
        if (!grants.isGranted(uri)) return SourceHealth.ACCESS_LOST
        return try {
            scanner.listChildren(uri, source.rootDocumentId)
            SourceHealth.OK
        } catch (_: SecurityException) {
            SourceHealth.ACCESS_LOST
        } catch (_: FileNotFoundException) {
            SourceHealth.ACCESS_LOST
        } catch (_: IllegalArgumentException) {
            SourceHealth.ACCESS_LOST
        }
    }

    companion object {
        private const val TAG = "SourceRepository"
    }
}

fun SourceEntity.toModel() = Source(id, treeUri, appKind, displayPath, health, lastScanAt)

fun FolderRow.toModel() = StatusFolder(
    id = folder.id,
    sourceId = folder.sourceId,
    appKind = folder.appKind,
    label = folder.label,
    relativePath = folder.relativePath,
    statusCount = statusCount,
    videoCount = videoCount,
    sourceHealth = sourceHealth,
)
