package com.wabox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, StatusFolderEntity::class, StatusEntity::class, SavedEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WABoxDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun folders(): FolderDao
    abstract fun statuses(): StatusDao
    abstract fun saved(): SavedDao

    companion object {
        fun create(context: Context): WABoxDatabase =
            Room.databaseBuilder(context, WABoxDatabase::class.java, "wabox.db").build()
    }
}
