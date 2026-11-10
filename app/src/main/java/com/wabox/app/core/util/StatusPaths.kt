package com.wabox.app.core.util

import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.MediaKind

/**
 * Pure path logic over ExternalStorageProvider document IDs ("primary:Android/media/…").
 * Kept free of Android types so it is unit-testable on the JVM.
 */
object StatusPaths {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val STATUSES_DIR = ".Statuses"
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /** Discovery never walks deeper than this below the granted root. */
    const val MAX_DEPTH = 4

    /** Folder the picker opens in when connecting an app (verified grantable in AOSP). */
    fun initialDocumentId(kind: AppKind): String? = when (kind) {
        AppKind.WHATSAPP -> "primary:Android/media/$WHATSAPP_PACKAGE"
        AppKind.BUSINESS -> "primary:Android/media/$BUSINESS_PACKAGE"
        AppKind.OTHER -> null
    }

    fun volumeOf(documentId: String): String = documentId.substringBefore(':', "")
    fun pathOf(documentId: String): String = documentId.substringAfter(':', documentId).trim('/')

    fun classify(documentId: String): AppKind {
        val path = pathOf(documentId).lowercase()
        val segments = path.split('/')
        return when {
            BUSINESS_PACKAGE in segments || "whatsapp business" in segments -> AppKind.BUSINESS
            WHATSAPP_PACKAGE in segments || "whatsapp" in segments -> AppKind.WHATSAPP
            else -> AppKind.OTHER
        }
    }

    /** Human readable "Internal storage › Android › media › com.whatsapp". */
    fun displayPath(documentId: String): String {
        val volume = volumeOf(documentId)
        val root = if (volume.equals("primary", ignoreCase = true)) "Internal storage" else "SD card"
        val path = pathOf(documentId)
        return if (path.isEmpty()) root else root + " › " + path.split('/').joinToString(" › ")
    }

    /** Path of [childId] relative to [rootId], or the child's own path if unrelated. */
    fun relativePath(rootId: String, childId: String): String {
        val root = pathOf(rootId)
        val child = pathOf(childId)
        return if (root.isNotEmpty() && child.startsWith(root)) child.removePrefix(root).trim('/') else child
    }

    /** True if [inner] is the same as, or nested inside, [outer] on the same volume. */
    fun isWithin(inner: String, outer: String): Boolean {
        if (!volumeOf(inner).equals(volumeOf(outer), ignoreCase = true)) return false
        val a = pathOf(inner).lowercase()
        val b = pathOf(outer).lowercase()
        return b.isEmpty() || a == b || a.startsWith("$b/")
    }

    fun isStatusesDir(name: String): Boolean = name.equals(STATUSES_DIR, ignoreCase = true)

    private val skipExact = setOf(
        "databases", "backups", ".shared", ".trash", ".thumbs", ".links", "wallpaper",
        "wallpapers", ".tmp", "cache", "logs", ".backups",
    )
    private val mediaSuffixes = listOf(
        " images", " video", " videos", " documents", " audio", " voice notes", " stickers",
        " animated gifs", " profile photos", " backup excluded stickers", " gifs",
    )

    /** Folders that can hold thousands of files and never contain `.Statuses`. */
    fun shouldSkipDuringDiscovery(name: String): Boolean {
        val n = name.lowercase()
        if (n in skipExact) return true
        return n.startsWith("whatsapp") && mediaSuffixes.any { n.endsWith(it) }
    }

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov", "m4v")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Media kind of a status file, or null if it isn't displayable media. */
    fun mediaKindOf(name: String, mimeType: String?): MediaKind? {
        if (name.startsWith(".")) return null // .nomedia, hidden temp files
        val mime = mimeType?.lowercase().orEmpty()
        val ext = extension(name)
        return when {
            mime.startsWith("video/") || ext in videoExt -> MediaKind.VIDEO
            mime.startsWith("image/") || ext in imageExt -> MediaKind.IMAGE
            else -> null
        }
    }

    /** Best mime type for a file, falling back from the provider's value to the extension. */
    fun mimeFor(name: String, providerMime: String?, kind: MediaKind): String {
        val m = providerMime?.lowercase()
        if (m != null && (m.startsWith("image/") || m.startsWith("video/"))) return m
        return when (extension(name)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg"
        }
    }

    fun defaultLabel(kind: AppKind, ordinal: Int, rootName: String): String {
        val base = when (kind) {
            AppKind.WHATSAPP -> "WhatsApp"
            AppKind.BUSINESS -> "WhatsApp Business"
            AppKind.OTHER -> rootName.ifBlank { "Other folder" }
        }
        return if (ordinal <= 0) base else "$base ${ordinal + 1}"
    }
}
