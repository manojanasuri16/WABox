package com.wabox.app.data.repo

import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.util.Hashing
import com.wabox.app.data.db.StatusEntity

/** A media file seen in a `.Statuses` folder during a scan. */
data class ScannedFile(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val kind: MediaKind,
    val size: Long,
    val lastModified: Long,
)

data class StatusDiffResult(
    val upserts: List<StatusEntity>,
    val deletes: List<String>,
    val added: List<StatusEntity>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()
}

/** Pure diff between what Room knows and what a scan found. JVM unit-tested. */
object StatusDiff {
    fun idFor(folderId: Long, documentId: String): String =
        "${folderId}_" + Hashing.sha1Hex(documentId).take(20)

    fun compute(
        folderId: Long,
        existing: List<StatusEntity>,
        scanned: List<ScannedFile>,
        now: Long,
        documentUri: (String) -> String,
    ): StatusDiffResult {
        val known = existing.associateBy { it.id }
        val seen = HashSet<String>(scanned.size)
        val upserts = ArrayList<StatusEntity>()
        val added = ArrayList<StatusEntity>()
        for (file in scanned) {
            val id = idFor(folderId, file.documentId)
            if (!seen.add(id)) continue
            val old = known[id]
            if (old == null) {
                val entity = StatusEntity(
                    id = id,
                    folderId = folderId,
                    documentId = file.documentId,
                    documentUri = documentUri(file.documentId),
                    name = file.name,
                    mimeType = file.mimeType,
                    isVideo = file.kind == MediaKind.VIDEO,
                    size = file.size,
                    lastModified = file.lastModified,
                    durationMs = null,
                    firstSeenAt = now,
                )
                upserts += entity
                added += entity
            } else if (old.size != file.size || old.lastModified != file.lastModified || old.mimeType != file.mimeType) {
                upserts += old.copy(
                    size = file.size,
                    lastModified = file.lastModified,
                    mimeType = file.mimeType,
                    isVideo = file.kind == MediaKind.VIDEO,
                    durationMs = if (old.size == file.size) old.durationMs else null,
                )
            }
        }
        val deletes = existing.filter { it.id !in seen }.map { it.id }
        return StatusDiffResult(upserts, deletes, added)
    }
}
