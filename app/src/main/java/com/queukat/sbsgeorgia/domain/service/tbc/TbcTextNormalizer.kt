package com.queukat.sbsgeorgia.domain.service

import java.time.format.DateTimeFormatter

internal fun normalizeTbcStatementLines(extractedText: String): List<String> = extractedText
    .lines()
    .map { it.replace('\u00A0', ' ').trim() }
    .filter { it.isNotBlank() }

internal fun sanitizeLogicalLine(line: String): String {
    val trimmed = line.trim()
    var cutoff = trimmed.length

    TbcStatementFormat.inlineArtifactRegexes.forEach { regex ->
        val markerIndex = regex.find(trimmed)?.range?.first ?: return@forEach
        if (markerIndex >= TbcStatementFormat.dateTokenLength && hasTrailingAmount(trimmed.substring(0, markerIndex))) {
            cutoff = minOf(cutoff, markerIndex)
        }
    }

    return trimmed.substring(0, cutoff).trim()
}

internal object TbcStatementFormat {
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val columnSeparator = Regex("\\s{2,}")
    val multiWhitespaceRegex = Regex("\\s+")
    const val dateTokenLength = 10
    val dateRegex = Regex("^\\d{2}/\\d{2}/\\d{4}$")
    val transactionLineRegex = Regex("^\\d{2}/\\d{2}/\\d{4}.*$")
    val pageCounterRegex = Regex("^\\d+\\s*-\\s*\\d+$")
    val currencyRegex = Regex("\\b([A-Z]{3})\\b")
    val trailingAmountRegex = Regex("([0-9][0-9,]*\\.\\d{2})\\s*$")
    val statementPeriodRegex = Regex("^ge\\d{2}[a-z0-9]+.*\\d{2}/\\d{2}/\\d{4}\\s*-\\s*\\d{2}/\\d{2}/\\d{4}.*$")
    val openingBalanceRegex =
        Regex("(?:საწყისი ნაშთი|Opening Balance).*?([0-9][0-9,]*\\.\\d{2})\\s*([A-Z]{3})", RegexOption.IGNORE_CASE)
    val headerHints = listOf(
        "date description additional information paid out paid in balance",
        "თარიღი დანიშნულება დამატებითი ინფორმაცია გასული თანხა შემოსული თანხა ბალანსი",
        "date / თარიღი description / დანიშნულება additional information / დამატებითი ინფორმაცია paid out / გასული თანხა paid in / შემოსული თანხა balance / ბალანსი",
    )
    val inlineArtifactRegexes = listOf(
        Regex("\\b\\d+\\s*-\\s*\\d+\\b\\s+(?=(?:account statement|statement from account|ამონაწერი ანგარიშიდან|ანგარიშის ამონაწერი|account holder|ანგარიშის მფლობელი|date\\b|თარიღი\\b))", RegexOption.IGNORE_CASE),
        Regex("(?=account statement|statement from account|ამონაწერი ანგარიშიდან|ანგარიშის ამონაწერი|account holder|ანგარიშის მფლობელი|generated on|გენერირებულია|opening balance|closing balance)", RegexOption.IGNORE_CASE),
        Regex("(?=date\\s*/\\s*თარიღი|თარიღი\\s+დანიშნულება|date\\s+description)", RegexOption.IGNORE_CASE),
        Regex("\\bge\\d{2}[a-z0-9]+.*\\d{2}/\\d{2}/\\d{4}\\s*-\\s*\\d{2}/\\d{2}/\\d{4}.*", RegexOption.IGNORE_CASE),
    )
    val headerLineFragments = setOf(
        "თარიღი",
        "date",
        "დანიშნულება",
        "description",
        "დამატებითი ინფორმაცია",
        "additional information",
        "გასული",
        "paid out",
        "შემოსული",
        "paid in",
        "თანხა",
        "ბალანსი",
        "balance",
    )
    val knownDescriptionPrefixes = listOf(
        "Transfer between your accounts",
        "for software services",
        "FOR SOFTWARE SERVICES",
        "Client Payment",
        "CLIENT PAYMENT",
        "კლიენტის გადახდა",
        "პროგრამული მომსახურება",
    )
    val additionalInfoSplitMarkers = listOf(
        "დებიტორები -",
        "Debtors -",
    )
    val currencyDetectionRegexes = listOf(
        Regex("\\bCurrency\\s*:?\\s*([A-Z]{3})\\b", RegexOption.IGNORE_CASE),
        Regex("\\bStatement\\s+currency\\s*:?\\s*([A-Z]{3})\\b", RegexOption.IGNORE_CASE),
        Regex("\\bAccount\\s+currency\\s*:?\\s*([A-Z]{3})\\b", RegexOption.IGNORE_CASE),
        Regex("ვალუტა\\s*:?\\s*([A-Z]{3})", RegexOption.IGNORE_CASE),
        Regex("ამონაწერის\\s+ვალუტა\\s*:?\\s*([A-Z]{3})", RegexOption.IGNORE_CASE),
        Regex("ანგარიშის\\s+ვალუტა\\s*:?\\s*([A-Z]{3})", RegexOption.IGNORE_CASE),
    )
}
