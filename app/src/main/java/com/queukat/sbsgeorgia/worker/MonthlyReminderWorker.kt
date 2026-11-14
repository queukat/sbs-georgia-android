package com.queukat.sbsgeorgia.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.queukat.sbsgeorgia.domain.service.ReminderPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveDeclarationStateUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MonthlyReminderWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val observeDeclarationStateUseCase: ObserveDeclarationStateUseCase,
    private val reminderPlanner: ReminderPlanner
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val state = observeDeclarationStateUseCase.current()
        val config = state.data.reminders ?: return Result.success()
        val notifications = reminderPlanner.buildNotificationsForSnapshots(state.date, config, state.snapshots)
        notifications.forEach { notification ->
            ReminderNotifications.show(applicationContext, notification)
        }
        return Result.success()
    }
}
