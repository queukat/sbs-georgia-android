package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.ReminderPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first

@HiltWorker
class MonthlyReminderWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val observeAllSnapshotsUseCase: ObserveAllSnapshotsUseCase,
    private val reminderPlanner: ReminderPlanner,
    private val clock: Clock
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now(clock)
        val reminderConfig =
            settingsRepository.observeReminderConfig().first() ?: return Result.success()
        val snapshots = observeAllSnapshotsUseCase().first()
        val notifications = reminderPlanner.buildNotificationsForSnapshots(today, reminderConfig, snapshots)
        notifications.forEach { notification ->
            ReminderNotifications.show(applicationContext, notification)
        }
        return Result.success()
    }
}
