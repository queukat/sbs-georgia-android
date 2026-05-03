package com.queukat.sbsgeorgia.ui.common.document

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import java.time.LocalDate

enum class DocumentImportAction {
    IMPORT_REGISTRY_EXTRACT,
    IMPORT_SMALL_BUSINESS_CERTIFICATE
}

data class DocumentImportStrings(
    val registryRecognized: String,
    val certificateRecognized: String,
    val previewApplied: String,
    val unsupportedDocument: String,
    val expectedRegistryExtract: String,
    val expectedSmallBusinessCertificate: String,
    val parseFailed: String
)

data class DocumentImportFormPatch(
    val displayName: String? = null,
    val legalForm: String? = null,
    val registrationId: String? = null,
    val registrationDate: String? = null,
    val legalAddress: String? = null,
    val activityType: String? = null,
    val certificateNumber: String? = null,
    val certificateIssuedDate: String? = null,
    val effectiveDate: LocalDate? = null
)

data class DocumentImportFormState(
    val displayName: String,
    val legalForm: String,
    val registrationId: String,
    val registrationDate: String,
    val legalAddress: String,
    val activityType: String,
    val certificateNumber: String,
    val certificateIssuedDate: String,
    val effectiveDate: LocalDate
)

sealed interface DocumentImportLoadResult {
    data class Success(val preview: OnboardingImportPreview, val infoMessage: String) : DocumentImportLoadResult

    data class Error(val errorMessage: String) : DocumentImportLoadResult
}

fun Context.documentImportStrings(): DocumentImportStrings = DocumentImportStrings(
    registryRecognized = getString(R.string.onboarding_registry_recognized),
    certificateRecognized = getString(R.string.onboarding_certificate_recognized),
    previewApplied = getString(R.string.onboarding_preview_applied),
    unsupportedDocument = getString(R.string.onboarding_error_unsupported_document),
    expectedRegistryExtract = getString(R.string.onboarding_error_expected_registry_extract),
    expectedSmallBusinessCertificate = getString(
        R.string.onboarding_error_expected_sbs_certificate
    ),
    parseFailed = getString(R.string.onboarding_error_parse_failed)
)

suspend fun loadDocumentImportPreview(
    uriString: String,
    action: DocumentImportAction,
    strings: DocumentImportStrings,
    loadPreview: suspend (String, OnboardingDocumentType) -> OnboardingImportPreview
): DocumentImportLoadResult = runCatching {
    loadPreview(uriString, action.expectedDocumentType())
}.fold(
    onSuccess = { preview ->
        DocumentImportLoadResult.Success(
            preview = preview,
            infoMessage =
            when (preview.documentType) {
                OnboardingDocumentType.REGISTRY_EXTRACT -> strings.registryRecognized
                OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE -> strings.certificateRecognized
            }
        )
    },
    onFailure = { error ->
        DocumentImportLoadResult.Error(
            errorMessage =
            when ((error as? OnboardingDocumentParseException)?.reason) {
                OnboardingParseError.UNSUPPORTED_DOCUMENT -> strings.unsupportedDocument
                OnboardingParseError.EXPECTED_REGISTRY_EXTRACT -> strings.expectedRegistryExtract
                OnboardingParseError.EXPECTED_SMALL_BUSINESS_CERTIFICATE -> strings.expectedSmallBusinessCertificate
                null -> strings.parseFailed
            }
        )
    }
)

fun OnboardingImportPreview.toDocumentImportFormPatch(): DocumentImportFormPatch = DocumentImportFormPatch(
    displayName = displayName.value,
    legalForm = legalForm.value,
    registrationId = registrationId.value,
    registrationDate = registrationDate.value?.toString(),
    legalAddress = legalAddress.value,
    activityType = activityType.value,
    certificateNumber = certificateNumber.value,
    certificateIssuedDate = certificateIssuedDate.value?.toString(),
    effectiveDate = effectiveDate.value
)

fun DocumentImportFormState.applyDocumentImportPatch(patch: DocumentImportFormPatch): DocumentImportFormState = copy(
    displayName = patch.displayName ?: displayName,
    legalForm = patch.legalForm ?: legalForm,
    registrationId = patch.registrationId ?: registrationId,
    registrationDate = patch.registrationDate ?: registrationDate,
    legalAddress = patch.legalAddress ?: legalAddress,
    activityType = patch.activityType ?: activityType,
    certificateNumber = patch.certificateNumber ?: certificateNumber,
    certificateIssuedDate = patch.certificateIssuedDate ?: certificateIssuedDate,
    effectiveDate = patch.effectiveDate ?: effectiveDate
)

fun DocumentImportFormState.applyDocumentImportPreview(preview: OnboardingImportPreview): DocumentImportFormState =
    applyDocumentImportPatch(preview.toDocumentImportFormPatch())

private fun DocumentImportAction.expectedDocumentType(): OnboardingDocumentType = when (this) {
    DocumentImportAction.IMPORT_REGISTRY_EXTRACT -> OnboardingDocumentType.REGISTRY_EXTRACT
    DocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE -> OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE
}
