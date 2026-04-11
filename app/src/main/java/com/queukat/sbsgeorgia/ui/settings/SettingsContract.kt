package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.time.LocalDate

private val defaultReminderDays = listOf(10, 13, 15)

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
    val declarationReminderDays: String = defaultReminderDays.joinToString(","),
    val paymentReminderDays: String = defaultReminderDays.joinToString(","),
    val declarationRemindersEnabled: Boolean = true,
    val paymentRemindersEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDocumentLoading: Boolean = false,
    val documentInfoMessage: String? = null,
    val documentErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val isDataOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
)

enum class SettingsDocumentImportAction {
    IMPORT_REGISTRY_EXTRACT,
    IMPORT_SMALL_BUSINESS_CERTIFICATE,
}

fun SettingsDocumentImportAction.expectedDocumentType(): OnboardingDocumentType = when (this) {
    SettingsDocumentImportAction.IMPORT_REGISTRY_EXTRACT -> OnboardingDocumentType.REGISTRY_EXTRACT
    SettingsDocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE -> OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE
}

sealed interface SettingsEffect {
    data object Saved : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}
