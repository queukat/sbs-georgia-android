package com.queukat.sbsgeorgia.domain.service.onboarding

import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegistryExtractParser @Inject constructor() {
    fun canParse(extractedText: String): Boolean {
        val normalized = extractedText.normalizedText()
        if (SmallBusinessStatusCertificateParser.certificateMarkers.any { it in normalized }) {
            return false
        }
        return registryMarkers.any { it in normalized } || registryFieldMatchCount(normalized) >= 2
    }

    fun parse(
        sourceFileName: String,
        sourceFingerprint: String,
        extractedText: String,
    ): OnboardingImportPreview {
        val lines = extractedText.normalizedLines()
        return OnboardingImportPreview(
            sourceFileName = sourceFileName,
            sourceFingerprint = sourceFingerprint,
            documentType = OnboardingDocumentType.REGISTRY_EXTRACT,
            displayName = lines.extractTextField(
                primaryLabels = listOf("Firm / display name", "Firm name", "Firm Name", "Name", "Display name", "საფირმო სახელწოდება", "დასახელება"),
            ),
            legalForm = lines.extractTextField(
                primaryLabels = listOf("Legal form", "სამართლებრივი ფორმა"),
            ),
            registrationId = lines.extractTextField(
                primaryLabels = listOf("Identification number", "Identification code", "ID number", "საიდენტიფიკაციო ნომერი"),
            ),
            registrationDate = lines.extractDateField(
                primaryLabels = listOf("Registration date", "Registration Number and Date", "რეგისტრაციის თარიღი"),
                fallbackPatterns = listOf(
                    Regex("^Date\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE),
                ),
            ),
            legalAddress = lines.extractRegistryAddressField(
                primaryLabels = listOf("Legal address", "იურიდიული მისამართი"),
                inlineStopLabels = registryAddressStopLabels,
            ),
            notes = listOf(
                OnboardingPreviewNote.REGISTRY_EFFECTIVE_DATE_MANUAL,
                OnboardingPreviewNote.REVIEW_BEFORE_APPLY,
            ),
        )
    }

    private fun registryFieldMatchCount(normalized: String): Int = listOf(
        "legal form",
        "identification number",
        "registration date",
        "საიდენტიფიკაციო ნომერი",
        "რეგისტრაციის თარიღი",
    ).count { it in normalized }

    private companion object {
        val registryMarkers = listOf(
            "extract from registry",
            "entrepreneurial and non-entrepreneurial",
            "ამონაწერი",
            "რეესტრიდან",
        )
        val registryAddressStopLabels = listOf(
            "Person",
            "პირი",
            "Subject",
            "სუბიექტი",
            "Registering Authority",
            "მარეგისტრირებელი ორგანო",
            "Seizure/Injunction",
            "ყადაღა/აკრძალვა",
            "Tax Lien/Mortgage",
            "საგადასახადო გირავნობა/იპოთეკა",
            "Pledge/Leasing on Intangible or Movable Property",
            "გირავნობა/ლიზინგი არამატერიალურ ან მოძრავ ქონებაზე",
            "Debtor Registry",
            "მოვალეთა რეესტრი",
        )
    }
}
