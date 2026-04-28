package com.queukat.sbsgeorgia.ui.onboarding

import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import java.time.LocalDate

data class OnboardingUiState(
    val preview: OnboardingImportPreview? = null,
    val hasExistingSetupData: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRestoringBackup: Boolean = false,
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
