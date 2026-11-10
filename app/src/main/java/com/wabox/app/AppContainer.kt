package com.wabox.app

import android.content.Context
import com.wabox.app.data.db.WABoxDatabase
import com.wabox.app.data.media.MediaSaver
import com.wabox.app.data.media.ShareHelper
import com.wabox.app.data.media.ThumbnailCache
import com.wabox.app.data.prefs.SettingsRepository
import com.wabox.app.data.repo.SavedRepository
import com.wabox.app.data.repo.SourceRepository
import com.wabox.app.data.repo.StatusRepository
import com.wabox.app.data.saf.LiveStatusObserver
import com.wabox.app.data.saf.SafScanner
import com.wabox.app.data.saf.SourceGrants
import com.wabox.app.ui.UserMessages
import com.wabox.app.widget.WidgetUpdater
import com.wabox.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph. Everything is lazy so cold start does as little as possible. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { WABoxDatabase.create(context) }
    val settings by lazy { SettingsRepository(context) }
    val grants by lazy { SourceGrants(context) }
    val scanner by lazy { SafScanner(context.contentResolver) }
    val thumbnails by lazy { ThumbnailCache(context) }
    val shareHelper by lazy { ShareHelper(context) }
    val messages by lazy { UserMessages() }
    val scheduler by lazy { WorkScheduler(context) }

    val sourceRepository by lazy { SourceRepository(database, scanner, grants, settings) }
    val statusRepository by lazy {
        StatusRepository(context, database, scanner, grants, sourceRepository, thumbnails, settings, ::updateWidgets)
    }
    val savedRepository by lazy {
        SavedRepository(context, database, MediaSaver(context, database.saved()), settings, ::updateWidgets)
    }
    val liveObserver by lazy {
        LiveStatusObserver(context.contentResolver, database, statusRepository, settings, appScope)
    }

    suspend fun updateWidgets() = WidgetUpdater.updateAll(context)
}
