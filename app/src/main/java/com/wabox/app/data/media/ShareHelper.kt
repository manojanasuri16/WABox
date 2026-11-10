package com.wabox.app.data.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds share intents. Saved items are shared by their MediaStore URI (owned by WABox).
 * Unsaved statuses are copied to a private cache and shared via FileProvider, so no SAF grant
 * to WhatsApp's folder ever leaks to another app.
 */
class ShareHelper(private val context: Context) {
    private val shareDir: File by lazy { File(context.cacheDir, "share").apply { mkdirs() } }

    fun shareSaved(uris: List<Uri>, mimeTypes: List<String>): Intent = buildChooser(uris, mimeTypes)

    /** Copies the status into the share cache. Returns null if the source is gone. */
    suspend fun shareStatus(source: Uri, name: String, mimeType: String): Intent? = withContext(Dispatchers.IO) {
        cleanup()
        val target = File(shareDir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val ok = try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it, 256 * 1024) }
            } != null
        } catch (_: Exception) {
            false
        }
        if (!ok) return@withContext null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        buildChooser(listOf(uri), listOf(mimeType))
    }

    private fun buildChooser(uris: List<Uri>, mimeTypes: List<String>): Intent {
        val type = when {
            mimeTypes.all { it.startsWith("image/") } -> if (mimeTypes.distinct().size == 1) mimeTypes.first() else "image/*"
            mimeTypes.all { it.startsWith("video/") } -> if (mimeTypes.distinct().size == 1) mimeTypes.first() else "video/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        }.apply {
            this.type = type
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        shareDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}
