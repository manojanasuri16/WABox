package com.wabox.app.work

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wabox.app.WABoxApp
import com.wabox.app.core.model.BackgroundRefresh
import com.wabox.app.core.model.SaveResult
import com.wabox.app.ui.feedbackText
import java.util.concurrent.TimeUnit

/** Runs one discovery pass. Used by the widget refresh button and optional periodic refresh. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as WABoxApp).container
        return try {
            c.statusRepository.refresh(force = true)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        } finally {
            c.settings.setWidgetRefreshing(false)
            c.updateWidgets()
        }
    }
}

/** Saves one status from a widget tap, then confirms with a toast. */
class SaveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val c = (applicationContext as WABoxApp).container
        val result = c.savedRepository.saveStatus(id)
        c.updateWidgets()
        val text = feedbackText(result)
        Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
        return if (result is SaveResult.Failed) Result.failure() else Result.success()
    }

    companion object { const val KEY_ID = "status_id" }
}

class WorkScheduler(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    fun applyBackgroundRefresh(setting: BackgroundRefresh) {
        if (setting == BackgroundRefresh.OFF) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(setting.hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow() {
        val b = OneTimeWorkRequestBuilder<RefreshWorker>()
        // Expedited work on API < 31 needs a foreground notification; plain work is fine there.
        if (Build.VERSION.SDK_INT >= 31) b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        wm.enqueueUniqueWork(REFRESH_NOW, ExistingWorkPolicy.KEEP, b.build())
    }

    fun saveNow(statusId: String) {
        val b = OneTimeWorkRequestBuilder<SaveWorker>().setInputData(workDataOf(SaveWorker.KEY_ID to statusId))
        if (Build.VERSION.SDK_INT >= 31) b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        wm.enqueueUniqueWork("save_$statusId", ExistingWorkPolicy.KEEP, b.build())
    }

    private companion object {
        const val PERIODIC = "periodic_refresh"
        const val REFRESH_NOW = "refresh_now"
    }
}
