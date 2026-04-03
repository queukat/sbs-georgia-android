package com.queukat.sbsgeorgia.ui.onboarding

import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import java.time.LocalDate

data class OnboardingUiState(
    val preview: OnboardingImportPreview? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val displayName: String = "",
    val legalForm: String = "",
    val registrationId: String = "",
    val registrationDate: String = "",
    val legalAddress: String = "",
    val activityType: String = "",
    val certificateNumber: String = "",
    val certificateIssuedDate: String = "",
    val effectiveDate: LocalDate = LocalDate.now(),
    val taxRatePercent: String = "1.0",
)

enum class OnboardingImportAction {
    IMPORT_REGISTRY_EXTRACT,
    IMPORT_SMALL_BUSINESS_CERTIFICATE,
}

fun OnboardingImportAction.expectedDocumentType(): OnboardingDocumentType = when (this) {
    OnboardingImportAction.IMPORT_REGISTRY_EXTRACT -> OnboardingDocumentType.REGISTRY_EXTRACT
    OnboardingImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE -> OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE
}
