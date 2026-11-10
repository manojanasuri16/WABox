package com.wabox.app.data.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.wabox.app.core.util.StatusPaths
import java.io.FileNotFoundException

data class DocEntry(
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/** A `.Statuses` directory discovered inside a granted tree. */
data class FoundFolder(val documentId: String, val relativePath: String)

/**
 * Thin, fast wrapper over DocumentsContract. One query per directory with a fixed projection.
 * (DocumentFile would issue one IPC per property per file.)
 */
class SafScanner(private val resolver: ContentResolver) {

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    /** @throws SecurityException if access was revoked, FileNotFoundException if the folder is gone. */
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<DocEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw FileNotFoundException("Provider returned no cursor for $parentDocumentId")
        return cursor.use { c ->
            val out = ArrayList<DocEntry>(c.count)
            while (c.moveToNext()) {
                out += DocEntry(
                    documentId = c.getString(0),
                    name = c.getString(1) ?: continue,
                    mimeType = c.getString(2),
                    size = if (c.isNull(3)) 0L else c.getLong(3),
                    lastModified = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
            out
        }
    }

    fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /**
     * Bounded breadth-first search for `.Statuses` directories below the tree root.
     * Skips heavyweight media folders and never descends inside a found `.Statuses`.
     */
    fun locateStatusFolders(treeUri: Uri, rootDocumentId: String): List<FoundFolder> {
        val rootName = StatusPaths.pathOf(rootDocumentId).substringAfterLast('/')
        if (StatusPaths.isStatusesDir(rootName)) return listOf(FoundFolder(rootDocumentId, ""))

        val found = mutableListOf<FoundFolder>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootDocumentId to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_DIRECTORIES) {
            val (docId, depth) = queue.removeFirst()
            visited++
            val children = try {
                listChildren(treeUri, docId)
            } catch (e: FileNotFoundException) {
                if (docId == rootDocumentId) throw e else continue
            }
            for (child in children) {
                if (!child.isDirectory) continue
                when {
                    StatusPaths.isStatusesDir(child.name) ->
                        found += FoundFolder(child.documentId, StatusPaths.relativePath(rootDocumentId, child.documentId))
                    depth + 1 < StatusPaths.MAX_DEPTH && !StatusPaths.shouldSkipDuringDiscovery(child.name) ->
                        queue.add(child.documentId to depth + 1)
                }
            }
        }
        return found.sortedBy { it.relativePath.length }
    }

    private companion object {
        const val MAX_DIRECTORIES = 120
    }
}
