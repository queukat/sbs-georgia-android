package com.queukat.sbsgeorgia.domain.model

import java.time.LocalDate

enum class OnboardingDocumentType {
    REGISTRY_EXTRACT,
    SMALL_BUSINESS_STATUS_CERTIFICATE,
}

enum class ExtractionConfidence {
    CONFIDENT,
    REVIEW_REQUIRED,
}

enum class OnboardingPreviewNote {
    REGISTRY_EFFECTIVE_DATE_MANUAL,
    CERTIFICATE_EFFECTIVE_DATE_AUTOFILLED,
    REVIEW_BEFORE_APPLY,
}

enum class OnboardingParseError {
    UNSUPPORTED_DOCUMENT,
    EXPECTED_REGISTRY_EXTRACT,
    EXPECTED_SMALL_BUSINESS_CERTIFICATE,
}

class OnboardingDocumentParseException(
    val reason: OnboardingParseError,
) : IllegalArgumentException(reason.name)

data class ParsedTextField(
    val value: String? = null,
    val confidence: ExtractionConfidence = ExtractionConfidence.REVIEW_REQUIRED,
)

data class ParsedDateField(
    val value: LocalDate? = null,
    val confidence: ExtractionConfidence = ExtractionConfidence.REVIEW_REQUIRED,
)

data class OnboardingImportPreview(
    val sourceFileName: String,
    val sourceFingerprint: String,
    val documentType: OnboardingDocumentType,
    val displayName: ParsedTextField = ParsedTextField(),
    val legalForm: ParsedTextField = ParsedTextField(),
    val registrationId: ParsedTextField = ParsedTextField(),
    val registrationDate: ParsedDateField = ParsedDateField(),
    val legalAddress: ParsedTextField = ParsedTextField(),
    val activityType: ParsedTextField = ParsedTextField(),
    val certificateNumber: ParsedTextField = ParsedTextField(),
    val certificateIssuedDate: ParsedDateField = ParsedDateField(),
    val effectiveDate: ParsedDateField = ParsedDateField(),
    val notes: List<OnboardingPreviewNote> = emptyList(),
)
