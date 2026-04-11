package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDocumentParsersTest {
    private val registryExtractParser = RegistryExtractParser()
    private val certificateParser = SmallBusinessStatusCertificateParser()
    private val coordinator = OnboardingDocumentParser(
        registryExtractParser = registryExtractParser,
        smallBusinessStatusCertificateParser = certificateParser,
    )

    @Test
    fun parsesEnglishRegistryExtract() {
        val preview = coordinator.parse(
            sourceFileName = "registry-en.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = loadFixture("registry_extract_en_extracted.txt"),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals(OnboardingDocumentType.REGISTRY_EXTRACT, preview.documentType)
        assertEquals("Individual Entrepreneur Iaroslav Rychenkov", preview.displayName.value)
        assertEquals("Individual Entrepreneur", preview.legalForm.value)
        assertEquals("306449082", preview.registrationId.value)
        assertEquals(LocalDate.of(2023, 11, 24), preview.registrationDate.value)
        assertEquals(
            "Georgia, Tbilisi, Vake district, Abashidze Street 12",
            preview.legalAddress.value,
        )
        assertEquals(ExtractionConfidence.CONFIDENT, preview.displayName.confidence)
        assertTrue(preview.effectiveDate.value == null)
    }

    @Test
    fun parsesRealEnglishRegistryExtractPdfText() {
        val preview = coordinator.parse(
            sourceFileName = "registry-real.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = loadFixture("registry_extract_en_real_pdftext.txt"),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals(OnboardingDocumentType.REGISTRY_EXTRACT, preview.documentType)
        assertEquals("Individual Entrepreneur Iaroslav Rychenkov", preview.displayName.value)
        assertEquals("Individual Entrepreneur", preview.legalForm.value)
        assertEquals("306449082", preview.registrationId.value)
        assertEquals(LocalDate.of(2023, 11, 24), preview.registrationDate.value)
        assertEquals(
            "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
            preview.legalAddress.value,
        )
    }

    @Test
    fun keepsRegistryAddressCleanWhenNextSectionCollapsesIntoSameLine() {
        val preview = coordinator.parse(
            sourceFileName = "registry-inline-section.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = """
                Extract from Registry of
                Subject
                Firm Name: Individual Entrepreneur Iaroslav Rychenkov
                Legal Form:Individual Entrepreneur
                Identification Number:306449082
                Registration Number and
                Date: 24/11/2023
                Legal Address:Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a Person: Iaroslav Rychenkov, 51№7540587
                Seizure/Injunction
                Not registered
            """.trimIndent(),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals(
            "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
            preview.legalAddress.value,
        )
        assertTrue(preview.legalAddress.value?.contains("Person:") != true)
    }

    @Test
    fun recoversRegistryAddressWhenFirstLineLeaksIntoPreviousField() {
        val preview = coordinator.parse(
            sourceFileName = "registry-address-leak.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = """
                Extract from Registry of
                Subject
                Firm Name: Individual Entrepreneur Iaroslav Rychenkov
                Legal Form:Individual Entrepreneur
                Identification Number:306449082
                Registration Number and
                Date: 24/11/2023
                Registering Authority:LEPL National Agency of Public Registry Georgia, Tbilisi, Samgori District, Police Street I Dead
                Legal Address:
                End N5, Floor 2, N4a
                Person: Iaroslav Rychenkov, 51№7540587
                Seizure/Injunction
                Not registered
            """.trimIndent(),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals(
            "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
            preview.legalAddress.value,
        )
    }

    @Test
    fun skipsAuthorityLineWhenRegistryAddressStartsOnFollowingLine() {
        val preview = coordinator.parse(
            sourceFileName = "registry-address-authority-noise.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = """
                Extract from Registry of
                Subject
                Firm Name: Individual Entrepreneur Iaroslav Rychenkov
                Legal Form:Individual Entrepreneur
                Identification Number:306449082
                Registration Number and
                Date: 24/11/2023
                Legal Address:
                LEPL National Agency of Public Registry
                Georgia, Tbilisi, Samgori District, Police Street I Dead
                End N5, Floor 2, N4a
                Person: Iaroslav Rychenkov, 51№7540587
            """.trimIndent(),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals(
            "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
            preview.legalAddress.value,
        )
    }

    @Test
    fun parsesGeorgianRegistryExtractWithReviewConfidenceForNextLineValues() {
        val preview = coordinator.parse(
            sourceFileName = "registry-ka.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = loadFixture("registry_extract_ka_extracted.txt"),
            expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        )

        assertEquals("Individual Entrepreneur Iaroslav Rychenkov", preview.displayName.value)
        assertEquals("ინდივიდუალური მეწარმე", preview.legalForm.value)
        assertEquals("306449082", preview.registrationId.value)
        assertEquals(LocalDate.of(2023, 11, 24), preview.registrationDate.value)
        assertEquals(ExtractionConfidence.REVIEW_REQUIRED, preview.displayName.confidence)
    }

    @Test
    fun parsesGeorgianSmallBusinessStatusCertificateAndAutofillsEffectiveDate() {
        val preview = coordinator.parse(
            sourceFileName = "sbs-certificate.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = loadFixture("sbs_certificate_ka_extracted.txt"),
            expectedDocumentType = OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE,
        )

        assertEquals(OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE, preview.documentType)
        assertEquals("Individual Entrepreneur Iaroslav Rychenkov", preview.displayName.value)
        assertEquals("306449082", preview.registrationId.value)
        assertEquals("Software development services", preview.activityType.value)
        assertEquals("SBS-2026-000123", preview.certificateNumber.value)
        assertEquals(LocalDate.of(2026, 3, 7), preview.certificateIssuedDate.value)
        assertEquals(LocalDate.of(2026, 3, 7), preview.effectiveDate.value)
        assertEquals(ExtractionConfidence.CONFIDENT, preview.effectiveDate.confidence)
    }

    @Test
    fun parsesRealGeorgianSmallBusinessCertificatePdfText() {
        val preview = coordinator.parse(
            sourceFileName = "certificate-real.pdf",
            sourceFingerprint = "fingerprint",
            extractedText = loadFixture("sbs_certificate_ka_real_pdftext.txt"),
            expectedDocumentType = OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE,
        )

        assertEquals(OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE, preview.documentType)
        assertEquals("იაროსლავ რიჩენკოვ", preview.displayName.value)
        assertEquals("306449082", preview.registrationId.value)
        assertEquals("კომპიუტერული დაპროგრამება და მასთან დაკავშირებული საქმინობები/", preview.activityType.value)
        assertEquals("007 - 07378", preview.certificateNumber.value)
        assertEquals(LocalDate.of(2023, 11, 24), preview.certificateIssuedDate.value)
        assertEquals(LocalDate.of(2023, 12, 1), preview.effectiveDate.value)
    }

    @Test
    fun rejectsDocumentTypeMismatch() {
        val error = runCatching {
            coordinator.parse(
                sourceFileName = "certificate.pdf",
                sourceFingerprint = "fingerprint",
                extractedText = loadFixture("sbs_certificate_ka_extracted.txt"),
                expectedDocumentType = OnboardingDocumentType.REGISTRY_EXTRACT,
            )
        }.exceptionOrNull()

        assertEquals(
            OnboardingParseError.EXPECTED_REGISTRY_EXTRACT,
            (error as? OnboardingDocumentParseException)?.reason,
        )
    }

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText()
}
