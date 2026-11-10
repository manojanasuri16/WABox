package com.wabox.app.core.model

/** Which app a granted folder belongs to, inferred from its path. */
enum class AppKind { WHATSAPP, BUSINESS, OTHER }

enum class MediaKind { IMAGE, VIDEO }

/** Health of one SAF grant. Recomputed on every refresh; never silently ignored. */
enum class SourceHealth { OK, NO_STATUS_FOLDER, ACCESS_LOST }

enum class MediaFilter { ALL, IMAGES, VIDEOS;
    /** -1 = any, 0 = images, 1 = videos (matches the Room query parameter). */
    val sqlKind: Int get() = when (this) { ALL -> -1; IMAGES -> 0; VIDEOS -> 1 }
}

enum class SortOrder { NEWEST, OLDEST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where saved copies go. Both are MediaStore-legal relative paths on API 29+. */
enum class SaveLocation(val imagesPath: String, val videosPath: String) {
    PICTURES("Pictures/WABox", "Movies/WABox"),
    DCIM("DCIM/WABox", "DCIM/WABox"),
}

/** How long WABox may sit in the background before App Lock asks again. */
enum class LockTimeout(val millis: Long) { IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000), FIFTEEN_MINUTES(900_000) }

enum class BackgroundRefresh(val hours: Long) {
    OFF(0), HOURLY(1), EVERY_6_HOURS(6), EVERY_12_HOURS(12), DAILY(24)
}

/** A granted folder (one SAF tree permission). */
data class Source(
    val id: Long,
    val treeUri: String,
    val rootDocumentId: String,
    val appKind: AppKind,
    val displayPath: String,
    val health: SourceHealth,
    val lastScanAt: Long,
)

/** One discovered `.Statuses` directory. This is what the user switches between. */
data class StatusFolder(
    val id: Long,
    val sourceId: Long,
    val appKind: AppKind,
    val label: String,
    val relativePath: String,
    val statusCount: Int,
    val videoCount: Int,
    val sourceHealth: SourceHealth,
)

data class StatusItem(
    val id: String,
    val folderId: Long,
    val documentUri: String,
    val name: String,
    val mimeType: String,
    val kind: MediaKind,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long?,
    val firstSeenAt: Long,
    val folderLabel: String,
    val appKind: AppKind,
    val isSaved: Boolean,
)

data class SavedItem(
    val id: Long,
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
    val kind: MediaKind,
    val size: Long,
    val savedAt: Long,
    val originLabel: String,
    val originAppKind: AppKind,
    val durationMs: Long?,
)

sealed interface SaveResult {
    data class Saved(val item: SavedItem, val location: String) : SaveResult
    data object AlreadySaved : SaveResult
    data object SourceMissing : SaveResult
    data class NoSpace(val needed: Long, val available: Long) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

sealed interface AddSourceResult {
    data class Added(val appKind: AppKind, val folderCount: Int) : AddSourceResult
    data class Reconnected(val appKind: AppKind) : AddSourceResult
    data class AlreadyConnected(val appKind: AppKind) : AddSourceResult
    /** The picked folder has no `.Statuses` inside and isn't recognisably WhatsApp's. */
    data object NotAStatusFolder : AddSourceResult
    data class Failed(val reason: String) : AddSourceResult
}
