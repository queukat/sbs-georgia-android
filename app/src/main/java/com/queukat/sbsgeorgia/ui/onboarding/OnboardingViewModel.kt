package com.queukat.sbsgeorgia.ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.CompleteOnboardingUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadOnboardingDocumentPreviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val loadOnboardingDocumentPreviewUseCase: LoadOnboardingDocumentPreviewUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    @ApplicationContext private val appContext: Context,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState(effectiveDate = LocalDate.now(clock)))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadExistingValues()
        }
    }

    fun loadDocument(uri: Uri, action: OnboardingImportAction) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            runCatching {
                loadOnboardingDocumentPreviewUseCase(
                    uriString = uri.toString(),
                    expectedDocumentType = action.expectedDocumentType(),
                )
            }.onSuccess { preview ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    preview = preview,
                    infoMessage = when (preview.documentType) {
                        com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType.REGISTRY_EXTRACT ->
                            appContext.getString(R.string.onboarding_registry_recognized)
                        com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE ->
                            appContext.getString(R.string.onboarding_certificate_recognized)
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    preview = null,
                    errorMessage = when ((error as? OnboardingDocumentParseException)?.reason) {
                        OnboardingParseError.UNSUPPORTED_DOCUMENT ->
                            appContext.getString(R.string.onboarding_error_unsupported_document)
                        OnboardingParseError.EXPECTED_REGISTRY_EXTRACT ->
                            appContext.getString(R.string.onboarding_error_expected_registry_extract)
                        OnboardingParseError.EXPECTED_SMALL_BUSINESS_CERTIFICATE ->
                            appContext.getString(R.string.onboarding_error_expected_sbs_certificate)
                        null -> appContext.getString(R.string.onboarding_error_parse_failed)
                    },
                )
            }
        }
    }

    fun applyPreview() {
        val preview = _uiState.value.preview ?: return
        _uiState.value = _uiState.value.copy(
            displayName = preview.displayName.value ?: _uiState.value.displayName,
            legalForm = preview.legalForm.value ?: _uiState.value.legalForm,
            registrationId = preview.registrationId.value ?: _uiState.value.registrationId,
            registrationDate = preview.registrationDate.value?.toString() ?: _uiState.value.registrationDate,
            legalAddress = preview.legalAddress.value ?: _uiState.value.legalAddress,
            activityType = preview.activityType.value ?: _uiState.value.activityType,
            certificateNumber = preview.certificateNumber.value ?: _uiState.value.certificateNumber,
            certificateIssuedDate = preview.certificateIssuedDate.value?.toString() ?: _uiState.value.certificateIssuedDate,
            effectiveDate = preview.effectiveDate.value ?: _uiState.value.effectiveDate,
            infoMessage = appContext.getString(R.string.onboarding_preview_applied),
            errorMessage = null,
        )
    }

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value, errorMessage = null)
    }

    fun updateLegalForm(value: String) {
        _uiState.value = _uiState.value.copy(legalForm = value, errorMessage = null)
    }

    fun updateRegistrationId(value: String) {
        _uiState.value = _uiState.value.copy(registrationId = value, errorMessage = null)
    }

    fun updateRegistrationDate(value: String) {
        _uiState.value = _uiState.value.copy(registrationDate = value, errorMessage = null)
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
        _uiState.value = _uiState.value.copy(certificateIssuedDate = value, errorMessage = null)
    }

    fun updateEffectiveDate(value: LocalDate) {
        _uiState.value = _uiState.value.copy(effectiveDate = value, errorMessage = null)
    }

    fun updateTaxRatePercent(value: String) {
        _uiState.value = _uiState.value.copy(taxRatePercent = value, errorMessage = null)
    }

    fun completeOnboarding() {
        val current = _uiState.value
        val taxRate = current.taxRatePercent.toBigDecimalOrNull()
        val registrationDate = current.registrationDate.parseOptionalDate()
        val certificateIssuedDate = current.certificateIssuedDate.parseOptionalDate()
        when {
            current.displayName.isBlank() ->
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_display_name_required))
            current.registrationId.isBlank() ->
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_registration_id_required))
            taxRate == null || taxRate < BigDecimal.ZERO ->
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_tax_rate_invalid))
            current.registrationDate.isNotBlank() && registrationDate == null ->
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_registration_date_invalid))
            current.certificateIssuedDate.isNotBlank() && certificateIssuedDate == null ->
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_certificate_issued_date_invalid))
            else -> {
                viewModelScope.launch {
                    _uiState.value = current.copy(isSaving = true, errorMessage = null)
                    runCatching {
                        completeOnboardingUseCase(
                            profile = TaxpayerProfile(
                                registrationId = current.registrationId.trim(),
                                displayName = current.displayName.trim(),
                                legalForm = current.legalForm.trim().ifBlank { null },
                                registrationDate = registrationDate,
                                legalAddress = current.legalAddress.trim().ifBlank { null },
                                activityType = current.activityType.trim().ifBlank { null },
                            ),
                            config = SmallBusinessStatusConfig(
                                effectiveDate = current.effectiveDate,
                                defaultTaxRatePercent = taxRate,
                                certificateNumber = current.certificateNumber.trim().ifBlank { null },
                                certificateIssuedDate = certificateIssuedDate,
                            ),
                        )
                    }.onSuccess {
                        _uiState.value = current.copy(
                            isSaving = false,
                            infoMessage = appContext.getString(R.string.onboarding_completed),
                            errorMessage = null,
                        )
                    }.onFailure {
                        _uiState.value = current.copy(
                            isSaving = false,
                            errorMessage = appContext.getString(R.string.onboarding_error_save_failed),
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadExistingValues() {
        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        _uiState.value = _uiState.value.copy(
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
        )
    }
}

private fun String.parseOptionalDate(): LocalDate? =
    trim().takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
    }
