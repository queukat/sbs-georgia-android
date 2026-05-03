package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) {
    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun reschedule(reminderConfig: ReminderConfig) {
        val workManager = WorkManager.getInstance(context)
        if (!reminderConfig.declarationRemindersEnabled &&
            !reminderConfig.paymentRemindersEnabled
        ) {
            cancelAll()
            return
        }

        val now = ZonedDateTime.now(clock)
        var nextRun =
            now
                .withHour(reminderConfig.defaultReminderTime.hour)
                .withMinute(reminderConfig.defaultReminderTime.minute)
                .withSecond(0)
                .withNano(0)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }

        val initialDelayMillis =
            java.time.Duration
                .between(now, nextRun)
                .toMillis()
        val request =
            PeriodicWorkRequestBuilder<MonthlyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .addTag(UNIQUE_WORK_NAME)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "monthly-reminder-worker"
    }
}
