package com.wabox.app.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.SaveLocation
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.util.Hashing
import com.wabox.app.core.util.StatusPaths
import com.wabox.app.data.db.SavedDao
import com.wabox.app.data.db.SavedEntity
import com.wabox.app.data.db.StatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Copies status media into the shared gallery via MediaStore. Needs no permission on API 29+
 * for files the app creates. `IS_PENDING` hides the file from other apps until the copy completes.
 */
class MediaSaver(private val context: Context, private val savedDao: SavedDao) {
    private val resolver = context.contentResolver

    suspend fun save(
        status: StatusEntity,
        originLabel: String,
        originAppKind: AppKind,
        location: SaveLocation,
    ): SaveResult = withContext(Dispatchers.IO) {
        val source = Uri.parse(status.documentUri)

        // 1. Fast duplicate check by origin identity.
        savedDao.findByOrigin(status.name, status.size)?.let { existing ->
            if (exists(Uri.parse(existing.mediaUri))) return@withContext SaveResult.AlreadySaved
            savedDao.delete(listOf(existing.id)) // copy was removed outside WABox; allow re-save
        }

        // 2. Content fingerprint (also proves the source is still readable).
        val fingerprint = try {
            fingerprint(source, status.size)
        } catch (e: FileNotFoundException) {
            return@withContext SaveResult.SourceMissing
        } catch (e: SecurityException) {
            return@withContext SaveResult.SourceMissing
        } catch (e: IOException) {
            return@withContext SaveResult.Failed(e.message ?: "Couldn't read the status")
        }
        savedDao.findByHash(fingerprint)?.let { existing ->
            if (exists(Uri.parse(existing.mediaUri))) return@withContext SaveResult.AlreadySaved
            savedDao.delete(listOf(existing.id))
        }

        // 3. Space check.
        val available = availableBytes()
        val needed = status.size + SPACE_MARGIN
        if (available in 0 until needed) return@withContext SaveResult.NoSpace(needed, available)

        // 4. Pending insert, streamed copy, publish.
        val relativePath = if (status.isVideo) location.videosPath else location.imagesPath
        val collection = if (status.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val ext = StatusPaths.extension(status.name).ifEmpty { if (status.isVideo) "mp4" else "jpg" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(status.lastModified.takeIf { it > 0 } ?: System.currentTimeMillis()))
        val displayName = "WABox_${stamp}_${status.name.substringBeforeLast('.').takeLast(6)}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, status.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (status.lastModified > 0) put(MediaStore.MediaColumns.DATE_TAKEN, status.lastModified)
        }
        val target = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            null
        } ?: return@withContext SaveResult.Failed("Android couldn't create the file in $relativePath")

        try {
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException()
            input.use { inp ->
                val out = resolver.openOutputStream(target, "w") ?: throw IOException("No output stream")
                out.use { o ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                    }
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            return@withContext when (t) {
                is FileNotFoundException, is SecurityException -> SaveResult.SourceMissing
                is kotlinx.coroutines.CancellationException -> throw t
                else -> SaveResult.Failed(t.message ?: "Copy failed")
            }
        }

        val entity = SavedEntity(
            mediaUri = target.toString(),
            displayName = displayName,
            mimeType = status.mimeType,
            isVideo = status.isVideo,
            size = status.size,
            savedAt = System.currentTimeMillis(),
            originName = status.name,
            originSize = status.size,
            partialHash = fingerprint,
            originLabel = originLabel,
            originAppKind = originAppKind,
            durationMs = status.durationMs,
        )
        val id = savedDao.insert(entity)
        SaveResult.Saved(entity.toModel(id), relativePath)
    }

    /** Which of [uris] still exist in MediaStore. One query per collection. */
    fun existing(uris: List<Uri>): Set<Uri> {
        if (uris.isEmpty()) return emptySet()
        val out = HashSet<Uri>()
        uris.groupBy { it.buildUpon().path(it.path?.substringBeforeLast('/')).build() }.forEach { (collection, group) ->
            group.chunked(400).forEach { chunk ->
                val ids = chunk.mapNotNull { runCatching { ContentUris.parseId(it) }.getOrNull() }
                if (ids.isEmpty()) return@forEach
                val selection = "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})"
                try {
                    resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, null, null)?.use { c ->
                        while (c.moveToNext()) out += ContentUris.withAppendedId(collection, c.getLong(0))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Existence query failed; assuming present", e)
                    out += chunk
                }
            }
        }
        return out
    }

    fun exists(uri: Uri): Boolean = uri in existing(listOf(uri))

    private fun fingerprint(uri: Uri, size: Long): String {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException()
        return pfd.use {
            FileInputStream(it.fileDescriptor).channel.use { ch ->
                val total = if (size > 0) size else ch.size()
                val headLen = minOf(CHUNK.toLong(), total).toInt()
                val head = ByteBuffer.allocate(headLen)
                ch.position(0)
                while (head.hasRemaining() && ch.read(head) > 0) Unit
                val tailLen = minOf(CHUNK.toLong(), (total - headLen).coerceAtLeast(0)).toInt()
                val tail = ByteBuffer.allocate(tailLen)
                if (tailLen > 0) {
                    ch.position(total - tailLen)
                    while (tail.hasRemaining() && ch.read(tail) > 0) Unit
                }
                Hashing.partialFingerprint(head.array(), tail.array(), total)
            }
        }
    }

    private fun availableBytes(): Long = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (_: Exception) {
        -1L
    }

    companion object {
        private const val TAG = "MediaSaver"
        private const val BUFFER = 256 * 1024
        private const val CHUNK = 64 * 1024
        private const val SPACE_MARGIN = 10L * 1024 * 1024
    }
}

fun SavedEntity.toModel(idOverride: Long = id) = com.wabox.app.core.model.SavedItem(
    id = idOverride,
    mediaUri = mediaUri,
    displayName = displayName,
    mimeType = mimeType,
    kind = if (isVideo) com.wabox.app.core.model.MediaKind.VIDEO else com.wabox.app.core.model.MediaKind.IMAGE,
    size = size,
    savedAt = savedAt,
    originLabel = originLabel,
    originAppKind = originAppKind,
    durationMs = durationMs,
)
