package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.queukat.sbsgeorgia.domain.service.ReminderNotification
import com.queukat.sbsgeorgia.domain.service.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderTestScheduler
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    fun schedule(reminderNotification: ReminderNotification, delaySeconds: Long) {
        val request =
            OneTimeWorkRequestBuilder<ReminderTestWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(
                    Data
                        .Builder()
                        .putString(ReminderTestWorker.KEY_TYPE, reminderNotification.type.name)
                        .putString(ReminderTestWorker.KEY_TITLE, reminderNotification.title)
                        .putString(ReminderTestWorker.KEY_BODY, reminderNotification.body)
                        .putInt(
                            ReminderTestWorker.KEY_NOTIFICATION_ID,
                            reminderNotification.notificationId ?: defaultNotificationId()
                        ).build()
                ).addTag(ReminderTestWorker.TAG)
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun defaultNotificationId(): Int = (System.currentTimeMillis() and 0x7fffffff).toInt()
}

class ReminderTestWorker(appContext: Context, workerParams: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val type =
            inputData
                .getString(KEY_TYPE)
                ?.let(ReminderType::valueOf)
                ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0).takeIf { it != 0 }
        ReminderNotifications.show(
            applicationContext,
            ReminderNotification(
                type = type,
                title = title,
                body = body,
                notificationId = notificationId
            )
        )
        return Result.success()
    }

    companion object {
        const val TAG = "test-reminder-worker"
        const val KEY_TYPE = "type"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}
