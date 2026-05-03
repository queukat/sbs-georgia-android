package com.queukat.sbsgeorgia.ui.common.document

import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import com.queukat.sbsgeorgia.domain.model.ParsedDateField
import com.queukat.sbsgeorgia.domain.model.ParsedTextField
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentImportControllerTest {
    private val strings =
        DocumentImportStrings(
            registryRecognized = "Registry recognized",
            certificateRecognized = "Certificate recognized",
            previewApplied = "Preview applied",
            unsupportedDocument = "Unsupported",
            expectedRegistryExtract = "Expected registry",
            expectedSmallBusinessCertificate = "Expected certificate",
            parseFailed = "Parse failed"
        )

    @Test
    fun loadDocumentImportPreviewMapsRegistrySuccess() = runTest {
        val preview = samplePreview(documentType = OnboardingDocumentType.REGISTRY_EXTRACT)

        val result =
            loadDocumentImportPreview(
                uriString = "content://registry",
                action = DocumentImportAction.IMPORT_REGISTRY_EXTRACT,
                strings = strings,
                loadPreview = { uriString, expectedDocumentType ->
                    assertEquals("content://registry", uriString)
                    assertEquals(OnboardingDocumentType.REGISTRY_EXTRACT, expectedDocumentType)
                    preview
                }
            )

        assertEquals(
            DocumentImportLoadResult.Success(
                preview = preview,
                infoMessage = strings.registryRecognized
            ),
            result
        )
    }

    @Test
    fun loadDocumentImportPreviewMapsExpectedDocumentError() = runTest {
        val result =
            loadDocumentImportPreview(
                uriString = "content://certificate",
                action = DocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE,
                strings = strings,
                loadPreview = { _, _ ->
                    throw OnboardingDocumentParseException(
                        OnboardingParseError.EXPECTED_REGISTRY_EXTRACT
                    )
                }
            )

        assertEquals(
            DocumentImportLoadResult.Error(strings.expectedRegistryExtract),
            result
        )
    }

    @Test
    fun loadDocumentImportPreviewMapsUnknownFailureToParseFailed() = runTest {
        val result =
            loadDocumentImportPreview(
                uriString = "content://broken",
                action = DocumentImportAction.IMPORT_REGISTRY_EXTRACT,
                strings = strings,
                loadPreview = { _, _ -> error("boom") }
            )

        assertEquals(
            DocumentImportLoadResult.Error(strings.parseFailed),
            result
        )
    }

    @Test
    fun toDocumentImportFormPatchMapsPreviewFields() {
        val patch =
            samplePreview(
                documentType = OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE
            ).toDocumentImportFormPatch()

        assertEquals("Test Entrepreneur", patch.displayName)
        assertEquals("IE", patch.legalForm)
        assertEquals("306449082", patch.registrationId)
        assertEquals("2026-01-15", patch.registrationDate)
        assertEquals("Tbilisi, Georgia", patch.legalAddress)
        assertEquals("Software services", patch.activityType)
        assertEquals("CERT-42", patch.certificateNumber)
        assertEquals("2026-01-16", patch.certificateIssuedDate)
        assertEquals(LocalDate.of(2026, 1, 1), patch.effectiveDate)
    }

    @Test
    fun applyDocumentImportPatchMergesOnlyProvidedFields() {
        val initialState =
            DocumentImportFormState(
                displayName = "Existing name",
                legalForm = "LLC",
                registrationId = "111111111",
                registrationDate = "2025-01-10",
                legalAddress = "Existing address",
                activityType = "Existing activity",
                certificateNumber = "OLD-CERT",
                certificateIssuedDate = "2025-01-11",
                effectiveDate = LocalDate.of(2025, 1, 1)
            )

        val updatedState =
            initialState.applyDocumentImportPatch(
                DocumentImportFormPatch(
                    displayName = "Imported name",
                    registrationId = "222222222",
                    certificateNumber = "NEW-CERT"
                )
            )

        assertEquals("Imported name", updatedState.displayName)
        assertEquals("LLC", updatedState.legalForm)
        assertEquals("222222222", updatedState.registrationId)
        assertEquals("2025-01-10", updatedState.registrationDate)
        assertEquals("Existing address", updatedState.legalAddress)
        assertEquals("Existing activity", updatedState.activityType)
        assertEquals("NEW-CERT", updatedState.certificateNumber)
        assertEquals("2025-01-11", updatedState.certificateIssuedDate)
        assertEquals(LocalDate.of(2025, 1, 1), updatedState.effectiveDate)
    }

    @Test
    fun previewAppliedStringRemainsAvailableForViewModels() {
        assertTrue(strings.previewApplied.isNotBlank())
    }

    private fun samplePreview(documentType: OnboardingDocumentType): OnboardingImportPreview = OnboardingImportPreview(
        sourceFileName = "document.pdf",
        sourceFingerprint = "fingerprint",
        documentType = documentType,
        displayName = ParsedTextField("Test Entrepreneur", ExtractionConfidence.CONFIDENT),
        legalForm = ParsedTextField("IE", ExtractionConfidence.REVIEW_REQUIRED),
        registrationId = ParsedTextField("306449082", ExtractionConfidence.CONFIDENT),
        registrationDate = ParsedDateField(
            LocalDate.of(2026, 1, 15),
            ExtractionConfidence.CONFIDENT
        ),
        legalAddress = ParsedTextField(
            "Tbilisi, Georgia",
            ExtractionConfidence.REVIEW_REQUIRED
        ),
        activityType = ParsedTextField(
            "Software services",
            ExtractionConfidence.REVIEW_REQUIRED
        ),
        certificateNumber = ParsedTextField("CERT-42", ExtractionConfidence.CONFIDENT),
        certificateIssuedDate = ParsedDateField(
            LocalDate.of(2026, 1, 16),
            ExtractionConfidence.REVIEW_REQUIRED
        ),
        effectiveDate = ParsedDateField(
            LocalDate.of(2026, 1, 1),
            ExtractionConfidence.CONFIDENT
        )
    )
}
