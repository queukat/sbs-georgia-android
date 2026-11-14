package com.queukat.sbsgeorgia.worker

import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.ReminderSchedulePort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class ReminderBootstrapper
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderSchedulePort
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun scheduleStoredConfigIfPresent() {
        scope.launch {
            reminderScheduler.reconcile(settingsRepository.observeReminderConfig().first())
        }
    }
}
