package com.queukat.sbsgeorgia.ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.CompleteOnboardingUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadOnboardingDocumentPreviewUseCase
import com.queukat.sbsgeorgia.ui.common.backup.BackupRestoreController
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportAction
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportFormState
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportLoadResult
import com.queukat.sbsgeorgia.ui.common.document.applyDocumentImportPreview
import com.queukat.sbsgeorgia.ui.common.document.documentImportStrings
import com.queukat.sbsgeorgia.ui.common.document.loadDocumentImportPreview
import com.queukat.sbsgeorgia.ui.common.setup.SetupFormState
import com.queukat.sbsgeorgia.ui.common.setup.SetupFormValidator
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidationField
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidationResult
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidationStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val loadOnboardingDocumentPreviewUseCase: LoadOnboardingDocumentPreviewUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val backupRestoreController: BackupRestoreController,
    @param:ApplicationContext private val appContext: Context,
    private val clock: Clock
) : ViewModel() {
    private val setupValidator =
        SetupFormValidator(
            strings =
            SetupValidationStrings(
                registrationIdRequired = appContext.getString(
                    R.string.onboarding_error_registration_id_required
                ),
                displayNameRequired = appContext.getString(
                    R.string.onboarding_error_display_name_required
                ),
                taxRateInvalid = appContext.getString(
                    R.string.onboarding_error_tax_rate_invalid
                ),
                registrationDateInvalid = appContext.getString(
                    R.string.onboarding_error_registration_date_invalid
                ),
                certificateIssuedDateInvalid = appContext.getString(
                    R.string.onboarding_error_certificate_issued_date_invalid
                )
            ),
            requiredFieldOrder =
            listOf(SetupValidationField.DISPLAY_NAME, SetupValidationField.REGISTRATION_ID)
        )
    private val _uiState =
        MutableStateFlow(OnboardingUiState(effectiveDate = LocalDate.now(clock)))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadExistingValues()
        }
    }

    fun loadDocument(uri: Uri, action: DocumentImportAction) {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            when (
                val result =
                    loadDocumentImportPreview(
                        uriString = uri.toString(),
                        action = action,
                        strings = appContext.documentImportStrings(),
                        loadPreview = loadOnboardingDocumentPreviewUseCase::invoke
                    )
            ) {
                is DocumentImportLoadResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            preview = result.preview,
                            infoMessage = result.infoMessage
                        )
                }
                is DocumentImportLoadResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            preview = null,
                            errorMessage = result.errorMessage
                        )
                }
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isRestoringBackup = true,
                    errorMessage = null,
                    infoMessage = null,
                    preview = null
                )
            runCatching {
                backupRestoreController.restore(uri.toString())
            }.onSuccess { result ->
                loadExistingValues(
                    infoMessage =
                    buildString {
                        append(result.message)
                        if (!result.setupComplete) {
                            append('\n')
                            append(
                                appContext.getString(
                                    R.string.onboarding_restore_backup_incomplete
                                )
                            )
                        }
                    },
                    errorMessage = null,
                    isRestoringBackup = false
                )
            }.onFailure {
                _uiState.value =
                    _uiState.value.copy(
                        isRestoringBackup = false,
                        errorMessage = appContext.getString(
                            R.string.onboarding_restore_backup_failed
                        )
                    )
            }
        }
    }

    fun applyPreview() {
        val preview = _uiState.value.preview ?: return
        val patchedFormState =
            _uiState.value
                .toDocumentImportFormState()
                .applyDocumentImportPreview(preview)
        _uiState.value =
            _uiState.value.copy(
                displayName = patchedFormState.displayName,
                legalForm = patchedFormState.legalForm,
                registrationId = patchedFormState.registrationId,
                registrationDate = patchedFormState.registrationDate,
                legalAddress = patchedFormState.legalAddress,
                activityType = patchedFormState.activityType,
                certificateNumber = patchedFormState.certificateNumber,
                certificateIssuedDate = patchedFormState.certificateIssuedDate,
                effectiveDate = patchedFormState.effectiveDate,
                infoMessage = appContext.documentImportStrings().previewApplied,
                errorMessage = null,
                displayNameError = null,
                registrationIdError = null,
                registrationDateError = null,
                certificateIssuedDateError = null,
                taxRatePercentError = null
            )
    }

    fun updateDisplayName(value: String) {
        _uiState.value =
            _uiState.value.copy(
                displayName = value,
                displayNameError = null,
                errorMessage = null
            )
    }

    fun updateLegalForm(value: String) {
        _uiState.value = _uiState.value.copy(legalForm = value, errorMessage = null)
    }

    fun updateRegistrationId(value: String) {
        _uiState.value =
            _uiState.value.copy(
                registrationId = value,
                registrationIdError = null,
                errorMessage = null
            )
    }

    fun updateRegistrationDate(value: String) {
        _uiState.value =
            _uiState.value.copy(
                registrationDate = value,
                registrationDateError = null,
                errorMessage = null
            )
    }

    fun updateLegalAddress(value: String) {
        _uiState.value = _uiState.value.copy(legalAddress = value, errorMessage = null)
    }

    fun updateActivityType(value: String) {
        _uiState.value = _uiState.value.copy(activityType = value, errorMessage = null)
    }

    fun updateCertificateNumber(value: String) {
        _uiState.value = _uiState.value.copy(certificateNumber = value, errorMessage = null)
    }

    fun updateCertificateIssuedDate(value: String) {
        _uiState.value =
            _uiState.value.copy(
                certificateIssuedDate = value,
                certificateIssuedDateError = null,
                errorMessage = null
            )
    }

    fun updateEffectiveDate(value: LocalDate) {
        _uiState.value = _uiState.value.copy(effectiveDate = value, errorMessage = null)
    }

    fun updateTaxRatePercent(value: String) {
        _uiState.value =
            _uiState.value.copy(
                taxRatePercent = value,
                taxRatePercentError = null,
                errorMessage = null
            )
    }

    fun completeOnboarding() {
        val current = _uiState.value
        when (val validation = setupValidator.validate(current.toSetupFormState())) {
            is SetupValidationResult.Invalid -> {
                _uiState.value =
                    current.withSetupValidationError(validation)
            }
            is SetupValidationResult.Valid -> {
                val input = validation.value
                viewModelScope.launch {
                    _uiState.value =
                        current.withClearedFieldErrors().copy(
                            isSaving = true,
                            errorMessage = null
                        )
                    runCatching {
                        completeOnboardingUseCase(
                            profile = input.toTaxpayerProfile(),
                            config = input.toStatusConfig()
                        )
                    }.onSuccess {
                        _uiState.value =
                            current.copy(
                                isSaving = false,
                                infoMessage = appContext.getString(
                                    R.string.onboarding_completed
                                ),
                                errorMessage = null
                            )
                    }.onFailure {
                        _uiState.value =
                            current.copy(
                                isSaving = false,
                                errorMessage = appContext.getString(
                                    R.string.onboarding_error_save_failed
                                )
                            )
                    }
                }
            }
        }
    }

    private suspend fun loadExistingValues(
        infoMessage: String? = _uiState.value.infoMessage,
        errorMessage: String? = _uiState.value.errorMessage,
        isRestoringBackup: Boolean = _uiState.value.isRestoringBackup
    ) {
        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        _uiState.value =
            _uiState.value.copy(
                preview = null,
                hasExistingSetupData = profile != null || config != null,
                displayName = profile?.displayName.orEmpty(),
                legalForm = profile?.legalForm.orEmpty(),
                registrationId = profile?.registrationId.orEmpty(),
                registrationDate = profile?.registrationDate?.toString().orEmpty(),
                legalAddress = profile?.legalAddress.orEmpty(),
                activityType = profile?.activityType.orEmpty(),
                certificateNumber = config?.certificateNumber.orEmpty(),
                certificateIssuedDate = config?.certificateIssuedDate?.toString().orEmpty(),
                effectiveDate = config?.effectiveDate ?: LocalDate.now(clock),
                taxRatePercent = config?.defaultTaxRatePercent?.toPlainString() ?: "1.0",
                infoMessage = infoMessage,
                errorMessage = errorMessage,
                isRestoringBackup = isRestoringBackup,
                displayNameError = null,
                registrationIdError = null,
                registrationDateError = null,
                certificateIssuedDateError = null,
                taxRatePercentError = null
            )
    }
}

private fun OnboardingUiState.withClearedFieldErrors(): OnboardingUiState = copy(
    displayNameError = null,
    registrationIdError = null,
    registrationDateError = null,
    certificateIssuedDateError = null,
    taxRatePercentError = null
)

private fun OnboardingUiState.withSetupValidationError(error: SetupValidationResult.Invalid): OnboardingUiState {
    val message = error.errorMessage
    return withClearedFieldErrors().copy(
        errorMessage = message,
        displayNameError = message.takeIf {
            error.field == SetupValidationField.DISPLAY_NAME
        },
        registrationIdError = message.takeIf {
            error.field == SetupValidationField.REGISTRATION_ID
        },
        registrationDateError = message.takeIf {
            error.field == SetupValidationField.REGISTRATION_DATE
        },
        certificateIssuedDateError = message.takeIf {
            error.field == SetupValidationField.CERTIFICATE_ISSUED_DATE
        },
        taxRatePercentError = message.takeIf {
            error.field == SetupValidationField.TAX_RATE_PERCENT
        }
    )
}

private fun OnboardingUiState.toSetupFormState(): SetupFormState = SetupFormState(
    registrationId = registrationId,
    displayName = displayName,
    legalForm = legalForm,
    registrationDate = registrationDate,
    legalAddress = legalAddress,
    activityType = activityType,
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate,
    effectiveDate = effectiveDate,
    taxRatePercent = taxRatePercent
)

private fun OnboardingUiState.toDocumentImportFormState(): DocumentImportFormState = DocumentImportFormState(
    displayName = displayName,
    legalForm = legalForm,
    registrationId = registrationId,
    registrationDate = registrationDate,
    legalAddress = legalAddress,
    activityType = activityType,
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate,
    effectiveDate = effectiveDate
)
