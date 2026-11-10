package com.wabox.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.SourceHealth

@Entity(tableName = "sources", indices = [Index(value = ["treeUri"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val rootDocumentId: String,
    val appKind: AppKind,
    val displayPath: String,
    val health: SourceHealth,
    val addedAt: Long,
    val lastScanAt: Long = 0,
)

@Entity(
    tableName = "status_folders",
    foreignKeys = [ForeignKey(
        entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sourceId"), Index(value = ["sourceId", "documentId"], unique = true)],
)
data class StatusFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val documentId: String,
    val relativePath: String,
    val appKind: AppKind,
    val label: String,
    val customLabel: Boolean = false,
    val position: Int = 0,
)

@Entity(
    tableName = "statuses",
    foreignKeys = [ForeignKey(
        entity = StatusFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folderId"), Index(value = ["name", "size"]), Index("lastModified")],
)
data class StatusEntity(
    @PrimaryKey val id: String,
    val folderId: Long,
    val documentId: String,
    val documentUri: String,
    val name: String,
    val mimeType: String,
    val isVideo: Boolean,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long?,
    val firstSeenAt: Long,
)

@Entity(
    tableName = "saved",
    indices = [Index(value = ["originName", "originSize"]), Index("partialHash"), Index("savedAt")],
)
data class SavedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val size: Long,
    val savedAt: Long,
    val originName: String,
    val originSize: Long,
    val partialHash: String,
    val originLabel: String,
    val originAppKind: AppKind,
    val durationMs: Long?,
)

/** Folder joined with its source health and counts. */
data class FolderRow(
    @Embedded val folder: StatusFolderEntity,
    val sourceHealth: SourceHealth,
    val statusCount: Int,
    val videoCount: Int,
)

/** Status joined with its folder label and saved state. */
data class StatusRow(
    @Embedded val status: StatusEntity,
    val folderLabel: String,
    val appKind: AppKind,
    val isSaved: Boolean,
)
