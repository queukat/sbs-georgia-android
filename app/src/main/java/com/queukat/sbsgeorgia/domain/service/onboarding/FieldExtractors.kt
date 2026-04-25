package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.ParsedDateField
import com.queukat.sbsgeorgia.domain.model.ParsedTextField
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun List<String>.extractTextField(
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

internal fun List<String>.extractTextFieldWithContinuation(
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

internal fun List<String>.extractRegistryAddressField(
    primaryLabels: List<String>,
    inlineStopLabels: List<String> = emptyList(),
): ParsedTextField {
    extractInlineRegistryAddress(primaryLabels, inlineStopLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.CONFIDENT)
    }
    extractNextLineRegistryAddress(primaryLabels, inlineStopLabels)?.let { value ->
        return ParsedTextField(value = value, confidence = ExtractionConfidence.REVIEW_REQUIRED)
    }
    return ParsedTextField()
}

internal fun List<String>.extractDateField(
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

internal fun List<String>.extractDateFromSentence(
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
        if (labels.any { label -> line.matchesStandaloneLabel(label) }) {
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
                return collectContinuationSegments(
                    startIndex = index + 1,
                    initialValue = initialValue,
                    inlineStopLabels = inlineStopLabels,
                )
                    .joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
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
        if (labels.any { label -> line.matchesStandaloneLabel(label) }) {
            val nextLine = getOrNull(index + 1)?.takeIf { it.isNotBlank() } ?: return null
            return collectContinuationSegments(
                startIndex = index + 2,
                initialValue = nextLine,
                inlineStopLabels = inlineStopLabels,
            )
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
    return null
}

private fun List<String>.extractInlineRegistryAddress(
    labels: List<String>,
    inlineStopLabels: List<String>,
): String? {
    forEachIndexed { index, line ->
        labels.forEach { label ->
            val match = Regex("^${Regex.escape(label)}\\s*[:\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
            if (match != null) {
                val initialValue = match.groupValues.getOrNull(1)?.trim().orEmpty()
                return repairRegistryAddress(
                    labelIndex = index,
                    segments = collectContinuationSegments(
                        startIndex = index + 1,
                        initialValue = initialValue,
                        inlineStopLabels = inlineStopLabels,
                    ),
                )
            }
        }
    }
    return null
}

private fun List<String>.extractNextLineRegistryAddress(
    labels: List<String>,
    inlineStopLabels: List<String>,
): String? {
    forEachIndexed { index, line ->
        if (labels.any { label -> line.matchesStandaloneLabel(label) }) {
            val nextLine = getOrNull(index + 1)?.takeIf { it.isNotBlank() } ?: return null
            return repairRegistryAddress(
                labelIndex = index,
                segments = collectContinuationSegments(
                    startIndex = index + 2,
                    initialValue = nextLine,
                    inlineStopLabels = inlineStopLabels,
                ),
            )
        }
    }
    return null
}

private fun List<String>.collectContinuationSegments(
    startIndex: Int,
    initialValue: String,
    inlineStopLabels: List<String>,
): List<String> {
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
    return segments
}

private fun List<String>.repairRegistryAddress(
    labelIndex: Int,
    segments: List<String>,
): String? {
    if (segments.isEmpty()) return null

    val cleanedSegments = segments
        .map { segment -> segment.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    if (cleanedSegments.isEmpty()) return null

    val firstAddressSegmentIndex = cleanedSegments.indexOfFirst { registryAddressAnchorRegex.containsMatchIn(it) }
    if (firstAddressSegmentIndex > 0) {
        repeat(firstAddressSegmentIndex) { cleanedSegments.removeAt(0) }
    }
    if (cleanedSegments.isNotEmpty()) {
        cleanedSegments[0] = cleanedSegments[0].trimToRegistryAddressAnchor()
    }

    var value = cleanedSegments.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    if (!registryAddressAnchorRegex.containsMatchIn(value)) {
        val prefix = findRegistryAddressPrefixNearLabel(labelIndex)
        if (!prefix.isNullOrBlank()) {
            value = "$prefix $value".replace(Regex("\\s+"), " ").trim()
        }
    }
    value = value.replace(Regex("\\s+:\\s+"), " ").trim()

    return value.ifBlank { null }
}

private fun List<String>.findRegistryAddressPrefixNearLabel(labelIndex: Int): String? {
    val startIndex = maxOf(0, labelIndex - 2)
    for (index in labelIndex - 1 downTo startIndex) {
        val line = this[index]
        val anchor = registryAddressAnchorRegex.find(line) ?: continue
        val suffix = line.substring(anchor.range.first).replace(Regex("\\s+"), " ").trim()
        if (suffix.isNotBlank()) {
            return suffix
        }
    }
    return null
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

private fun String.matchesStandaloneLabel(label: String): Boolean =
    Regex("^${Regex.escape(label)}\\s*[:\\-]?\\s*$", RegexOption.IGNORE_CASE).matches(trim())

private fun String.trimToRegistryAddressAnchor(): String {
    val anchor = registryAddressAnchorRegex.find(this) ?: return trim()
    return substring(anchor.range.first).trim()
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
private val registryAddressAnchorRegex = Regex("(Georgia\\s*,|საქართველო\\s*,|Tbilisi\\s*,|თბილისი\\s*,)", RegexOption.IGNORE_CASE)
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
