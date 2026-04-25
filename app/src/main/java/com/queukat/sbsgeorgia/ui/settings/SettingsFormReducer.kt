package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportLoadResult
import com.queukat.sbsgeorgia.ui.common.document.toDocumentImportFormPatch
import java.time.LocalDate

internal object SettingsFormReducer {
    fun updateRegistrationId(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(registrationId = value, errorMessage = null)

    fun updateDisplayName(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(displayName = value, errorMessage = null)

    fun updateLegalForm(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(legalForm = value, errorMessage = null)

    fun updateRegistrationDate(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(registrationDate = value, errorMessage = null)

    fun updateLegalAddress(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(legalAddress = value, errorMessage = null)

    fun updateActivityType(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(activityType = value, errorMessage = null)

    fun updateCertificateNumber(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(certificateNumber = value, errorMessage = null)

    fun updateCertificateIssuedDate(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(certificateIssuedDate = value, errorMessage = null)

    fun updateEffectiveDate(state: SettingsUiState, value: LocalDate): SettingsUiState =
        state.copy(effectiveDate = value, errorMessage = null)

    fun updateTaxRatePercent(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(taxRatePercent = value, errorMessage = null)

    fun updateDefaultReminderTime(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(defaultReminderTime = value, errorMessage = null)

    fun updateDeclarationReminderDays(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(declarationReminderDays = value, errorMessage = null)

    fun updatePaymentReminderDays(state: SettingsUiState, value: String): SettingsUiState =
        state.copy(paymentReminderDays = value, errorMessage = null)

    fun startDocumentLoading(state: SettingsUiState): SettingsUiState = state.copy(
        isDocumentLoading = true,
        documentInfoMessage = null,
        documentErrorMessage = null,
    )

    fun applyDocumentLoadResult(
        state: SettingsUiState,
        result: DocumentImportLoadResult,
    ): SettingsUiState = when (result) {
        is DocumentImportLoadResult.Success -> state.copy(
            isDocumentLoading = false,
            preview = result.preview,
            documentInfoMessage = result.infoMessage,
        )
        is DocumentImportLoadResult.Error -> state.copy(
            isDocumentLoading = false,
            preview = null,
            documentErrorMessage = result.errorMessage,
        )
    }

    fun applyPreview(
        state: SettingsUiState,
        preview: OnboardingImportPreview,
        previewAppliedMessage: String,
    ): SettingsUiState {
        val patch = preview.toDocumentImportFormPatch()
        return state.copy(
            displayName = patch.displayName ?: state.displayName,
            legalForm = patch.legalForm ?: state.legalForm,
            registrationId = patch.registrationId ?: state.registrationId,
            registrationDate = patch.registrationDate ?: state.registrationDate,
            legalAddress = patch.legalAddress ?: state.legalAddress,
            activityType = patch.activityType ?: state.activityType,
            certificateNumber = patch.certificateNumber ?: state.certificateNumber,
            certificateIssuedDate = patch.certificateIssuedDate ?: state.certificateIssuedDate,
            effectiveDate = patch.effectiveDate ?: state.effectiveDate,
            documentInfoMessage = previewAppliedMessage,
            documentErrorMessage = null,
            errorMessage = null,
        )
    }

    fun loadedState(
        currentState: SettingsUiState,
        profile: TaxpayerProfile?,
        config: SmallBusinessStatusConfig?,
        reminderConfig: ReminderConfig?,
        today: LocalDate,
    ): SettingsUiState = currentState.copy(
        preview = null,
        registrationId = profile?.registrationId.orEmpty(),
        displayName = profile?.displayName.orEmpty(),
        legalForm = profile?.legalForm.orEmpty(),
        registrationDate = profile?.registrationDate?.toString().orEmpty(),
        legalAddress = profile?.legalAddress.orEmpty(),
        activityType = profile?.activityType.orEmpty(),
        certificateNumber = config?.certificateNumber.orEmpty(),
        certificateIssuedDate = config?.certificateIssuedDate?.toString().orEmpty(),
        effectiveDate = config?.effectiveDate ?: today,
        taxRatePercent = config?.defaultTaxRatePercent?.toPlainString() ?: "1.0",
        defaultReminderTime = reminderConfig?.defaultReminderTime?.toString() ?: "09:00",
        declarationReminderDays = reminderConfig?.declarationReminderDays?.joinToString(",")
            ?: settingsDefaultReminderDays.joinToString(","),
        paymentReminderDays = reminderConfig?.paymentReminderDays?.joinToString(",")
            ?: settingsDefaultReminderDays.joinToString(","),
        declarationRemindersEnabled = reminderConfig?.declarationRemindersEnabled ?: true,
        paymentRemindersEnabled = reminderConfig?.paymentRemindersEnabled ?: true,
        themeMode = reminderConfig?.themeMode ?: currentState.themeMode,
        isDocumentLoading = false,
        documentInfoMessage = null,
        documentErrorMessage = null,
        errorMessage = null,
    )
}
