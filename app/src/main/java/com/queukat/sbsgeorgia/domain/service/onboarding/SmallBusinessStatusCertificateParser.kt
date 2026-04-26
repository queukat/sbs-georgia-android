package com.queukat.sbsgeorgia.domain.service.onboarding

import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmallBusinessStatusCertificateParser @Inject constructor() {
    fun canParse(extractedText: String): Boolean {
        val normalized = extractedText.normalizedText()
        return certificateMarkers.count { it in normalized } >= 2
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
            documentType = OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE,
            displayName = lines.extractTextField(
                primaryLabels = listOf("სახელი, გვარი / დასახელება", "დასახელება", "სახელი, გვარი", "ფიზიკურ პირს", "იურიდიულ პირს"),
            ),
            registrationId = lines.extractTextField(
                primaryLabels = listOf("პირადი ნომერი / საიდენტიფიკაციო ნომერი", "საიდენტიფიკაციო ნომერი", "პირადი ნომერი"),
            ),
            activityType = lines.extractTextField(
                primaryLabels = listOf("საქმიანობის სახე", "ეკონომიკური საქმიანობის სახე"),
                previousLineLabels = listOf("(საქმიანობის სახე)", "(ეკონომიკური საქმიანობის სახე)"),
            ),
            certificateNumber = lines.extractTextField(
                primaryLabels = listOf("სერტიფიკატის ნომერი", "სერტიფიკატის N", "სერტიფიკატი N"),
                fallbackPatterns = listOf(
                    Regex("^(?:N|№)\\s*[:.]?\\s*(.+)$", RegexOption.IGNORE_CASE),
                ),
            ),
            certificateIssuedDate = lines.extractDateField(
                primaryLabels = listOf("გაცემის თარიღი", "გაცემულია", "სერტიფიკატის გაცემის თარიღი", "სერთიფიკატის გაცემის თარიღი"),
            ),
            effectiveDate = lines.extractDateFromSentence(
                sentenceMarkers = listOf("მცირე ბიზნესის სტატუსი მინიჭებულია"),
            ),
            notes = listOf(
                OnboardingPreviewNote.CERTIFICATE_EFFECTIVE_DATE_AUTOFILLED,
                OnboardingPreviewNote.REVIEW_BEFORE_APPLY,
            ),
        )
    }

    companion object {
        val certificateMarkers = listOf(
            "მცირე ბიზნესის სტატუსის",
            "სერტიფიკატი",
            "მცირე ბიზნესის სტატუსი მინიჭებულია",
        )
    }
}
