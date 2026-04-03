package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.time.LocalDate

private val defaultReminderDays = listOf(10, 13, 15)

data class SettingsUiState(
    val registrationId: String = "",
    val displayName: String = "",
    val effectiveDate: LocalDate = LocalDate.now(),
    val taxRatePercent: String = "1.0",
    val defaultReminderTime: String = "09:00",
    val declarationReminderDays: String = defaultReminderDays.joinToString(","),
    val paymentReminderDays: String = defaultReminderDays.joinToString(","),
    val declarationRemindersEnabled: Boolean = true,
    val paymentRemindersEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isSaving: Boolean = false,
    val isDataOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface SettingsEffect {
    data object Saved : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}
