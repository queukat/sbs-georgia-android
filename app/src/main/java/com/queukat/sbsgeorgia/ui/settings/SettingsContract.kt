package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.time.LocalDate

data class SettingsUiState(
    val preview: OnboardingImportPreview? = null,
    val registrationId: String = "",
    val displayName: String = "",
    val legalForm: String = "",
    val registrationDate: String = "",
    val legalAddress: String = "",
    val activityType: String = "",
    val certificateNumber: String = "",
    val certificateIssuedDate: String = "",
    val effectiveDate: LocalDate = LocalDate.now(),
    val taxRatePercent: String = "1.0",
    val defaultReminderTime: String = "09:00",
    val declarationReminderDays: String = settingsDefaultReminderDays.joinToString(","),
    val paymentReminderDays: String = settingsDefaultReminderDays.joinToString(","),
    val declarationRemindersEnabled: Boolean = true,
    val paymentRemindersEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDocumentLoading: Boolean = false,
    val documentInfoMessage: String? = null,
    val documentErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val isDataOperationInProgress: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SettingsEffect {
    data object Saved : SettingsEffect

    data class Message(val text: String) : SettingsEffect
}
