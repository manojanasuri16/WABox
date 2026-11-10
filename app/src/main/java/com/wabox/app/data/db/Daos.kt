package com.wabox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wabox.app.core.model.SourceHealth
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY appKind, addedAt")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY appKind, addedAt")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("UPDATE sources SET health = :health, lastScanAt = :scanAt WHERE id = :id")
    suspend fun setHealth(id: Long, health: SourceHealth, scanAt: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

private const val FOLDER_ROW_SELECT = """
    SELECT f.*, s.health AS sourceHealth,
      (SELECT COUNT(*) FROM statuses st WHERE st.folderId = f.id) AS statusCount,
      (SELECT COUNT(*) FROM statuses st WHERE st.folderId = f.id AND st.isVideo = 1) AS videoCount
    FROM status_folders f JOIN sources s ON s.id = f.sourceId
"""

@Dao
interface FolderDao {
    @Query("$FOLDER_ROW_SELECT ORDER BY f.appKind, f.position, f.id")
    fun observeRows(): Flow<List<FolderRow>>

    @Query("$FOLDER_ROW_SELECT ORDER BY f.appKind, f.position, f.id")
    suspend fun getRows(): List<FolderRow>

    @Query("SELECT * FROM status_folders ORDER BY appKind, position, id")
    fun observeAll(): Flow<List<StatusFolderEntity>>

    @Query("SELECT * FROM status_folders WHERE sourceId = :sourceId ORDER BY position, id")
    suspend fun forSource(sourceId: Long): List<StatusFolderEntity>

    @Query("SELECT COUNT(*) FROM status_folders WHERE appKind = :appKind")
    suspend fun countOfKind(appKind: com.wabox.app.core.model.AppKind): Int

    @Query("SELECT * FROM status_folders WHERE id = :id")
    suspend fun get(id: Long): StatusFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: StatusFolderEntity): Long

    @Query("UPDATE status_folders SET label = :label, customLabel = 1 WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("DELETE FROM status_folders WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

private const val STATUS_ROW_SELECT = """
    SELECT st.*, f.label AS folderLabel, f.appKind AS appKind,
      EXISTS(SELECT 1 FROM saved sv WHERE sv.originName = st.name AND sv.originSize = st.size) AS isSaved
    FROM statuses st JOIN status_folders f ON f.id = st.folderId
"""

@Dao
interface StatusDao {
    @Query("""$STATUS_ROW_SELECT
        WHERE (:folderId = 0 OR st.folderId = :folderId) AND (:kind = -1 OR st.isVideo = :kind)
        ORDER BY st.lastModified DESC""")
    fun observe(folderId: Long, kind: Int): Flow<List<StatusRow>>

    @Query("""$STATUS_ROW_SELECT
        WHERE (:folderId = 0 OR st.folderId = :folderId) AND (:kind = -1 OR st.isVideo = :kind)
        ORDER BY st.lastModified DESC""")
    suspend fun list(folderId: Long, kind: Int): List<StatusRow>

    @Query("""$STATUS_ROW_SELECT
        WHERE (:folderId = 0 OR st.folderId = :folderId)
        ORDER BY st.lastModified DESC LIMIT :limit""")
    fun observeLatest(folderId: Long, limit: Int): Flow<List<StatusRow>>

    @Query("$STATUS_ROW_SELECT WHERE st.id = :id")
    suspend fun getRow(id: String): StatusRow?

    @Query("SELECT * FROM statuses WHERE folderId = :folderId")
    suspend fun forFolder(folderId: Long): List<StatusEntity>

    @Query("SELECT id FROM statuses")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM statuses ORDER BY lastModified DESC LIMIT :limit")
    suspend fun newest(limit: Int): List<StatusEntity>

    @Query("""SELECT COUNT(*) AS total,
        COALESCE(SUM(CASE WHEN isVideo = 1 THEN 1 ELSE 0 END), 0) AS videos,
        COALESCE(SUM(CASE WHEN firstSeenAt > :since THEN 1 ELSE 0 END), 0) AS fresh
        FROM statuses WHERE (:folderId = 0 OR folderId = :folderId)""")
    fun observeCounts(folderId: Long, since: Long): Flow<StatusCounts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<StatusEntity>)

    @Query("DELETE FROM statuses WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Transaction
    suspend fun applyDiff(upserts: List<StatusEntity>, deletes: List<String>) {
        if (deletes.isNotEmpty()) deletes.chunked(500).forEach { delete(it) }
        if (upserts.isNotEmpty()) upsert(upserts)
    }
}

data class StatusCounts(val total: Int, val videos: Int, val fresh: Int)

@Dao
interface SavedDao {
    @Query("""SELECT * FROM saved WHERE (:kind = -1 OR isVideo = :kind)
        ORDER BY CASE WHEN :ascending = 1 THEN savedAt END ASC,
                 CASE WHEN :ascending = 0 THEN savedAt END DESC""")
    fun observe(kind: Int, ascending: Boolean): Flow<List<SavedEntity>>

    @Query("""SELECT * FROM saved WHERE (:kind = -1 OR isVideo = :kind)
        ORDER BY CASE WHEN :ascending = 1 THEN savedAt END ASC,
                 CASE WHEN :ascending = 0 THEN savedAt END DESC""")
    suspend fun list(kind: Int, ascending: Boolean): List<SavedEntity>

    @Query("SELECT COUNT(*) FROM saved")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM saved")
    suspend fun all(): List<SavedEntity>

    @Query("SELECT * FROM saved WHERE originName = :name AND originSize = :size LIMIT 1")
    suspend fun findByOrigin(name: String, size: Long): SavedEntity?

    @Query("SELECT * FROM saved WHERE partialHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SavedEntity?

    @Insert
    suspend fun insert(item: SavedEntity): Long

    @Query("DELETE FROM saved WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
