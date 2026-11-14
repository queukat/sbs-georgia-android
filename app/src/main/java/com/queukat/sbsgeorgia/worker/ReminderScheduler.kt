package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.service.ReminderSchedulePort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ReminderScheduler
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) : ReminderSchedulePort {
    override fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun ensureScheduled(reminderConfig: ReminderConfig) {
        schedule(reminderConfig, ExistingPeriodicWorkPolicy.KEEP)
    }

    override fun reschedule(config: ReminderConfig) {
        schedule(config, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    override suspend fun reconcile(config: ReminderConfig?) {
        if (config == null || (!config.declarationRemindersEnabled && !config.paymentRemindersEnabled)) {
            cancelAll()
            return
        }

        // Query WorkManager's durable state, not a separate SharedPreferences stamp:
        // a crash between enqueue and writing a stamp would otherwise hide lost work.
        val workManager = WorkManager.getInstance(context)
        val current = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).first()
        val matching = current.any { !it.state.isFinished && scheduleTag(config) in it.tags }
        if (matching) {
            ensureScheduled(config)
        } else {
            reschedule(config)
        }
    }

    private fun scheduleTag(config: ReminderConfig): String =
        "reminder-schedule:${config.defaultReminderTime}:" +
            "${config.declarationRemindersEnabled}:${config.paymentRemindersEnabled}"

    private fun schedule(reminderConfig: ReminderConfig, policy: ExistingPeriodicWorkPolicy) {
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
                .addTag(scheduleTag(reminderConfig))
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "monthly-reminder-worker"
    }
}
