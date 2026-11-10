package com.wabox.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Small square JPEG thumbnails for widgets (RemoteViews can't use Coil).
 * Written during discovery, pruned when statuses disappear.
 */
class ThumbnailCache(private val context: Context) {
    private val dir: File by lazy { File(context.cacheDir, "widget_thumbs").apply { mkdirs() } }

    fun fileFor(statusId: String): File = File(dir, "$statusId.jpg")

    fun has(statusId: String): Boolean = fileFor(statusId).let { it.exists() && it.length() > 0 }

    /** Generates a thumbnail if absent. Returns false if the media couldn't be decoded. */
    fun ensure(statusId: String, uri: Uri, isVideo: Boolean): Boolean {
        if (has(statusId)) return true
        val bitmap = try {
            if (isVideo) videoFrame(uri) else imageThumb(uri)
        } catch (t: Throwable) {
            Log.w(TAG, "Thumbnail failed for $uri", t)
            null
        } ?: return false
        val square = centerCrop(bitmap, SIZE)
        if (square !== bitmap) bitmap.recycle()
        return try {
            val tmp = File(dir, "$statusId.tmp")
            FileOutputStream(tmp).use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(fileFor(statusId))
        } finally {
            square.recycle()
        }
    }

    /** Decodes a thumbnail for a widget; rounds corners itself where the launcher can't (API < 31). */
    fun load(statusId: String): Bitmap? {
        val f = fileFor(statusId)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        return if (Build.VERSION.SDK_INT < 31) rounded(bmp, bmp.width * 0.12f) else bmp
    }

    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension !in keep || f.extension == "tmp") f.delete()
        }
    }

    private fun imageThumb(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = SIZE.toFloat() / min(w, h).coerceAtLeast(1)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun videoFrame(uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIZE * 2, SIZE * 2)
                ?: r.frameAtTime
        } finally {
            r.release()
        }
    }

    private fun centerCrop(src: Bitmap, size: Int): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (side <= size) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private fun rounded(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }

    companion object {
        private const val TAG = "ThumbnailCache"
        const val SIZE = 240
    }
}

/** Cheap metadata probes. */
object MediaProbe {
    fun videoDurationMs(context: Context, uri: Uri): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            r.release()
        }
    }

    data class Dimensions(val width: Int, val height: Int)

    fun dimensions(context: Context, uri: Uri, isVideo: Boolean): Dimensions? = try {
        if (isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (w != null && h != null) {
                    if (rot == 90 || rot == 270) Dimensions(h, w) else Dimensions(w, h)
                } else null
            } finally {
                r.release()
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth > 0) Dimensions(opts.outWidth, opts.outHeight) else null
        }
    } catch (_: Throwable) {
        null
    }
}
