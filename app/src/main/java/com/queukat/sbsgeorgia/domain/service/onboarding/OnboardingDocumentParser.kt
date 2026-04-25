package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingDocumentParser @Inject constructor(
    private val registryExtractParser: RegistryExtractParser,
    private val smallBusinessStatusCertificateParser: SmallBusinessStatusCertificateParser,
) {
    fun parse(
        sourceFileName: String,
        sourceFingerprint: String,
        extractedText: String,
        expectedDocumentType: OnboardingDocumentType,
    ): OnboardingImportPreview {
        val detectedDocumentType = when {
            smallBusinessStatusCertificateParser.canParse(extractedText) ->
                OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE
            registryExtractParser.canParse(extractedText) ->
                OnboardingDocumentType.REGISTRY_EXTRACT
            else -> null
        }
        if (detectedDocumentType == null) {
            throw OnboardingDocumentParseException(OnboardingParseError.UNSUPPORTED_DOCUMENT)
        }
        if (detectedDocumentType != expectedDocumentType) {
            throw OnboardingDocumentParseException(
                when (detectedDocumentType) {
                    OnboardingDocumentType.REGISTRY_EXTRACT ->
                        OnboardingParseError.EXPECTED_SMALL_BUSINESS_CERTIFICATE
                    OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE ->
                        OnboardingParseError.EXPECTED_REGISTRY_EXTRACT
                },
            )
        }

        return when (detectedDocumentType) {
            OnboardingDocumentType.REGISTRY_EXTRACT ->
                registryExtractParser.parse(sourceFileName, sourceFingerprint, extractedText)
            OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE ->
                smallBusinessStatusCertificateParser.parse(sourceFileName, sourceFingerprint, extractedText)
        }
    }
}
