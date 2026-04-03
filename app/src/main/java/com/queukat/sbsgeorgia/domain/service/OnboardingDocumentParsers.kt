package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.domain.model.ParsedDateField
import com.queukat.sbsgeorgia.domain.model.ParsedTextField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
            legalAddress = lines.extractTextFieldWithContinuation(
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

private fun String.normalizedLines(): List<String> = lineSequence()
    .map { line -> line.replace(Regex("\\s+"), " ").trim() }
    .filter { it.isNotBlank() }
    .toList()

private fun String.normalizedText(): String = normalizedLines().joinToString("\n").lowercase()

private fun List<String>.extractTextField(
    primaryLabels: List<String>,
    previousLineLabels: List<String> = emptyList(),
    fallbackPatterns: List<Regex> = emptyList(),
): ParsedTextField {
    extractInlineValue(primaryLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.CONFIDENT)
    }
    extractNextLineValue(primaryLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    extractPreviousLineValue(previousLineLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    extractPatternValue(fallbackPatterns)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    return ParsedTextField()
}

private fun List<String>.extractTextFieldWithContinuation(
    primaryLabels: List<String>,
    inlineStopLabels: List<String> = emptyList(),
): ParsedTextField {
    extractInlineValueWithContinuation(primaryLabels, inlineStopLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.CONFIDENT)
    }
    extractNextLineValueWithContinuation(primaryLabels, inlineStopLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    return ParsedTextField()
}

private fun List<String>.extractDateField(
    primaryLabels: List<String>,
    fallbackPatterns: List<Regex> = emptyList(),
): ParsedDateField {
    extractInlineValue(primaryLabels)?.extractDateCandidate()?.parseKnownDate()?.let { value ->
        return ParsedDateField(value = value, confidence = ExtractionConfidence.CONFIDENT)
    }
    extractNextLineValue(primaryLabels)?.extractDateCandidate()?.parseKnownDate()?.let { value ->
        return ParsedDateField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    extractPatternValue(fallbackPatterns)?.extractDateCandidate()?.parseKnownDate()?.let { value ->
        return ParsedDateField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    return ParsedDateField()
}

private fun List<String>.extractDateFromSentence(
    sentenceMarkers: List<String>,
): ParsedDateField {
    firstOrNull { line ->
        sentenceMarkers.any { marker -> line.contains(marker, ignoreCase = true) }
    }?.let { line ->
        val candidate = line.extractDateCandidate()
        if (candidate != null) {
            return ParsedDateField(
                value = candidate.parseKnownDate(),
                confidence = ExtractionConfidence.CONFIDENT,
            )
        }
    }
    return ParsedDateField()
}

private fun List<String>.extractInlineValue(labels: List<String>): String? =
    asSequence().mapNotNull { line ->
        labels.firstNotNullOfOrNull { label ->
            if (line.equals(label, ignoreCase = true)) {
                null
            } else {
                Regex("^${Regex.escape(label)}\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }
    }.firstOrNull()

private fun List<String>.extractNextLineValue(labels: List<String>): String? {
    forEachIndexed { index, line ->
        if (labels.any { label -> line.equals(label, ignoreCase = true) }) {
            return getOrNull(index + 1)?.takeIf { it.isNotBlank() }
        }
    }
    return null
}

private fun List<String>.extractInlineValueWithContinuation(
    labels: List<String>,
    inlineStopLabels: List<String>,
): String? {
    forEachIndexed { index, line ->
        labels.forEach { label ->
            val match = Regex("^${Regex.escape(label)}\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
            if (match != null) {
                val initialValue = match.groupValues.getOrNull(1)?.trim().orEmpty()
                return collectContinuationValue(
                    startIndex = index + 1,
                    initialValue = initialValue,
                    inlineStopLabels = inlineStopLabels,
                )
            }
        }
    }
    return null
}

private fun List<String>.extractNextLineValueWithContinuation(
    labels: List<String>,
    inlineStopLabels: List<String>,
): String? {
    forEachIndexed { index, line ->
        if (labels.any { label -> line.equals(label, ignoreCase = true) }) {
            val nextLine = getOrNull(index + 1)?.takeIf { it.isNotBlank() } ?: return null
            return collectContinuationValue(
                startIndex = index + 2,
                initialValue = nextLine,
                inlineStopLabels = inlineStopLabels,
            )
        }
    }
    return null
}

private fun List<String>.collectContinuationValue(
    startIndex: Int,
    initialValue: String,
    inlineStopLabels: List<String>,
): String {
    val segments = mutableListOf<String>()
    val sanitizedInitialValue = initialValue.trimAtInlineStopLabel(inlineStopLabels)
    if (sanitizedInitialValue.isNotBlank()) {
        segments += sanitizedInitialValue
    }
    var index = startIndex
    while (index < size) {
        val candidate = this[index]
        if (candidate.looksLikeNextFieldOrSection()) break
        val sanitizedCandidate = candidate.trimAtInlineStopLabel(inlineStopLabels)
        if (sanitizedCandidate.isNotBlank()) {
            segments += sanitizedCandidate
        }
        if (sanitizedCandidate != candidate) break
        index += 1
    }
    return segments.joinToString(" ").replace(Regex("\\s+"), " ").trim()
}

private fun String.looksLikeNextFieldOrSection(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return true
    if (Regex("^[\\p{L}][^:]{0,60}:\\s*.+$").matches(normalized)) return true
    return normalized.lowercase() in continuationStopHeadings
}

private fun String.trimAtInlineStopLabel(stopLabels: List<String>): String {
    if (stopLabels.isEmpty()) return trim()
    val stopIndex = stopLabels
        .mapNotNull { label ->
            Regex("(?<!^)${Regex.escape(label)}\\s*:", RegexOption.IGNORE_CASE)
                .find(this)
                ?.range
                ?.first
        }
        .minOrNull()
        ?: return trim()
    return substring(0, stopIndex).trim()
}

private fun List<String>.extractPreviousLineValue(labels: List<String>): String? {
    forEachIndexed { index, line ->
        if (labels.any { label -> line.equals(label, ignoreCase = true) }) {
            return getOrNull(index - 1)?.takeIf { it.isNotBlank() }
        }
    }
    return null
}

private fun List<String>.extractPatternValue(patterns: List<Regex>): String? =
    asSequence().mapNotNull { line ->
        patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }.firstOrNull()

private fun String.extractDateCandidate(): String? =
    numericDateRegex.find(this)?.value
        ?: textualDateRegex.find(this)?.value

private fun String.parseKnownDate(): LocalDate? {
    val normalized = trim()
        .replace(Regex("\\s+"), " ")
        .removeSuffix("წელი")
        .trim()

    knownDateFormatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(normalized, formatter) }.getOrNull()
    }?.let { return it }

    textualDateRegex.matchEntire(normalized)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthToken = match.groupValues[2].lowercase()
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val month = textualMonths[monthToken] ?: return null
        return LocalDate.of(year, month, day)
    }

    return null
}

private val knownDateFormatters = listOf(
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("dd.MM.yyyy"),
    DateTimeFormatter.ISO_LOCAL_DATE,
)

private val numericDateRegex = Regex("\\d{2}[./]\\d{2}[./]\\d{4}|\\d{4}-\\d{2}-\\d{2}")
private val textualDateRegex = Regex("(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})(?:\\s*წელი)?", RegexOption.IGNORE_CASE)
private val continuationStopHeadings = setOf(
    "subject",
    "person",
    "seizure/injunction",
    "tax lien/mortgage",
    "pledge/leasing on intangible or movable property",
    "debtor registry",
    "არ არის რეგისტრირებული",
)
private val textualMonths = mapOf(
    "january" to 1,
    "february" to 2,
    "march" to 3,
    "april" to 4,
    "may" to 5,
    "june" to 6,
    "july" to 7,
    "august" to 8,
    "september" to 9,
    "october" to 10,
    "november" to 11,
    "december" to 12,
    "იანვარი" to 1,
    "თებერვალი" to 2,
    "მარტი" to 3,
    "აპრილი" to 4,
    "მაისი" to 5,
    "ივნისი" to 6,
    "ივლისი" to 7,
    "აგვისტო" to 8,
    "სექტემბერი" to 9,
    "ოქტომბერი" to 10,
    "ნოემბერი" to 11,
    "დეკემბერი" to 12,
)
