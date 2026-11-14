package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.ReminderConfig

/** WorkManager scheduling adapter. Room remains the source of truth. */
interface ReminderSchedulePort {
    fun cancelAll()

    fun reschedule(config: ReminderConfig)

    /** Repair an interrupted settings-to-WorkManager write after process restart. */
    suspend fun reconcile(config: ReminderConfig?)
}
