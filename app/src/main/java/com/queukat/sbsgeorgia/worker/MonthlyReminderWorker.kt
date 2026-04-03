package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.queukat.sbsgeorgia.domain.service.ReminderPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveMonthDetailUseCase
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first

@HiltWorker
class MonthlyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val observeMonthDetailUseCase: ObserveMonthDetailUseCase,
    private val reminderPlanner: ReminderPlanner,
    private val clock: Clock,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now(clock)
        val reminderConfig = settingsRepository.observeReminderConfig().first() ?: return Result.success()
        val dueMonth = YearMonth.from(today.minusMonths(1))
        val snapshot = observeMonthDetailUseCase(dueMonth).first().first
        val notifications = reminderPlanner.buildNotifications(today, reminderConfig, snapshot)
        notifications.forEach { notification ->
            ReminderNotifications.show(applicationContext, notification)
        }
        return Result.success()
    }
}
