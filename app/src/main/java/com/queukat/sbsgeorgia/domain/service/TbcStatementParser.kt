package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreview
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TbcStatementParser @Inject constructor() {
    fun parse(
        sourceFileName: String,
        sourceFingerprint: String,
        extractedText: String,
    ): ImportedStatementPreview {
        val lines = extractedText
            .lines()
            .map { it.replace('\u00A0', ' ').trim() }
            .filter { it.isNotBlank() }
        val logicalLines = buildLogicalTransactionLines(lines)
        val openingBalance = detectOpeningBalance(lines)
        val statementCurrency = detectStatementCurrency(lines) ?: openingBalance?.currency

        val rows = mutableListOf<ImportedStatementPreviewRow>()
        var skippedLineCount = 0
        var previousBalance = openingBalance?.amount

        logicalLines.forEach { line ->
            val sanitizedLine = sanitizeLogicalLine(line)
            val row = parseTransactionLine(
                line = sanitizedLine,
                statementCurrency = statementCurrency,
                previousBalance = previousBalance,
            )
            if (row != null) {
                rows += row
                previousBalance = row.balance?.amount ?: previousBalance
            } else if (isTransactionStart(line)) {
                skippedLineCount += 1
            }
        }

        require(rows.isNotEmpty()) {
            "No TBC statement transaction rows were recognized. This importer only supports the current TBC v1 statement layout."
        }

        return ImportedStatementPreview(
            sourceFileName = sourceFileName,
            sourceFingerprint = sourceFingerprint,
            rows = rows,
            skippedLineCount = skippedLineCount,
        )
    }

    private fun parseTransactionLine(
        line: String,
        statementCurrency: String?,
        previousBalance: BigDecimal?,
    ): ImportedStatementPreviewRow? {
        return parseColumnSeparatedTransactionLine(line, statementCurrency)
            ?: parseCollapsedTransactionLine(line, statementCurrency, previousBalance)
    }

    private fun parseColumnSeparatedTransactionLine(
        line: String,
        statementCurrency: String?,
    ): ImportedStatementPreviewRow? {
        val parts = line.split(columnSeparator).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 5) return null

        val dateToken = parts.first()
        if (!dateRegex.matches(dateToken)) return null

        val incomeDate = runCatching { LocalDate.parse(dateToken, dateFormatter) }.getOrNull() ?: return null
        val trailingColumns = parts.takeLast(3)
        val leadingColumns = parts.drop(1).dropLast(3)
        if (leadingColumns.isEmpty()) return null

        return buildPreviewRow(
            incomeDate = incomeDate,
            description = leadingColumns.first(),
            additionalInformation = leadingColumns.drop(1).joinToString(" ").ifBlank { null },
            paidOut = parseMoney(trailingColumns[0], statementCurrency),
            paidIn = parseMoney(trailingColumns[1], statementCurrency),
            balance = parseMoney(trailingColumns[2], statementCurrency),
            fallbackAmount = null,
            fallbackCurrency = statementCurrency,
        )
    }

    private fun parseCollapsedTransactionLine(
        line: String,
        statementCurrency: String?,
        previousBalance: BigDecimal?,
    ): ImportedStatementPreviewRow? {
        val dateToken = line.take(dateTokenLength)
        if (!dateRegex.matches(dateToken)) return null

        val incomeDate = runCatching { LocalDate.parse(dateToken, dateFormatter) }.getOrNull() ?: return null
        val balanceMatch = trailingAmountRegex.find(line) ?: return null
        val balanceAmount = balanceMatch.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        var transactionText = line.substring(dateTokenLength, balanceMatch.range.first)
            .replace(multiWhitespaceRegex, " ")
            .trim()
        if (transactionText.isBlank()) return null

        val explicitAmount = trailingAmountRegex.find(transactionText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
        val balance = StatementMoney(amount = balanceAmount, currency = statementCurrency)

        val paidOut: StatementMoney?
        val paidIn: StatementMoney?
        val fallbackAmount: BigDecimal?
        when {
            previousBalance != null -> {
                val delta = balanceAmount.subtract(previousBalance)
                val movementAmount = delta.abs()
                if (movementAmount > BigDecimal.ZERO) {
                    transactionText = stripTrailingAmount(transactionText, movementAmount)
                }
                when {
                    delta.signum() > 0 -> {
                        paidOut = null
                        paidIn = StatementMoney(amount = movementAmount, currency = statementCurrency)
                        fallbackAmount = null
                    }
                    delta.signum() < 0 -> {
                        paidOut = StatementMoney(amount = movementAmount, currency = statementCurrency)
                        paidIn = null
                        fallbackAmount = null
                    }
                    explicitAmount != null -> {
                        transactionText = stripTrailingAmount(transactionText, explicitAmount)
                        val inferredDirection = inferCollapsedDirection(
                            amount = explicitAmount,
                            lineText = transactionText,
                            currency = statementCurrency,
                        )
                        paidOut = inferredDirection.first
                        paidIn = inferredDirection.second
                        fallbackAmount = if (paidOut == null && paidIn == null) explicitAmount else null
                    }
                    else -> {
                        paidOut = null
                        paidIn = null
                        fallbackAmount = null
                    }
                }
            }
            explicitAmount != null -> {
                transactionText = stripTrailingAmount(transactionText, explicitAmount)
                val inferredDirection = inferCollapsedDirection(
                    amount = explicitAmount,
                    lineText = transactionText,
                    currency = statementCurrency,
                )
                paidOut = inferredDirection.first
                paidIn = inferredDirection.second
                fallbackAmount = if (paidOut == null && paidIn == null) explicitAmount else null
            }
            else -> return null
        }

        val (description, additionalInformation) = splitCondensedDescription(transactionText)

        return buildPreviewRow(
            incomeDate = incomeDate,
            description = description,
            additionalInformation = additionalInformation,
            paidOut = paidOut,
            paidIn = paidIn,
            balance = balance,
            fallbackAmount = fallbackAmount,
            fallbackCurrency = statementCurrency,
        )
    }

    private fun buildPreviewRow(
        incomeDate: LocalDate,
        description: String,
        additionalInformation: String?,
        paidOut: StatementMoney?,
        paidIn: StatementMoney?,
        balance: StatementMoney?,
        fallbackAmount: BigDecimal?,
        fallbackCurrency: String?,
    ): ImportedStatementPreviewRow {
        val suggestionText = listOf(description, additionalInformation.orEmpty())
            .joinToString(" ")
            .lowercase()
        val normalizedOutgoing = paidOut?.takeIf { it.amount > BigDecimal.ZERO }
        val normalizedIncoming = paidIn?.takeIf { it.amount > BigDecimal.ZERO }
        val isTaxPayment = TaxPaymentDetection.isLikelyTaxPayment(
            description = description,
            additionalInformation = additionalInformation,
            paidOut = normalizedOutgoing,
            paidIn = normalizedIncoming,
        )
        val suggestedInclusion = when {
            normalizedIncoming != null && nonTaxableHints.any { it in suggestionText } -> DeclarationInclusion.EXCLUDED
            normalizedIncoming != null && taxableHints.any { it in suggestionText } -> DeclarationInclusion.INCLUDED
            normalizedIncoming != null -> DeclarationInclusion.REVIEW_REQUIRED
            normalizedOutgoing != null -> DeclarationInclusion.EXCLUDED
            fallbackAmount != null -> DeclarationInclusion.REVIEW_REQUIRED
            else -> DeclarationInclusion.EXCLUDED
        }
        val suggestedAmount = when {
            normalizedIncoming != null -> normalizedIncoming.amount
            normalizedOutgoing != null -> normalizedOutgoing.amount
            fallbackAmount != null -> fallbackAmount
            else -> BigDecimal.ZERO
        }
        val suggestedSourceCategory = when {
            isTaxPayment -> SourceCategoryPresets.TAX_PAYMENT
            nonTaxableHints.any { it in suggestionText } && bankFeeHints.any { it in suggestionText } ->
                SourceCategoryPresets.BANK_FEE
            normalizedOutgoing != null && nonTaxableHints.any { it in suggestionText } ->
                SourceCategoryPresets.OWN_ACCOUNT_TRANSFER
            normalizedIncoming != null && taxableHints.any { it in suggestionText } ->
                SourceCategoryPresets.SOFTWARE_SERVICES
            normalizedIncoming != null ->
                SourceCategoryPresets.IMPORTED_STATEMENT_INCOME
            else ->
                SourceCategoryPresets.IMPORTED_STATEMENT_REVIEW
        }

        return ImportedStatementPreviewRow(
            transactionFingerprint = fingerprintFor(
                incomeDate = incomeDate,
                description = description,
                additionalInformation = additionalInformation,
                paidOut = paidOut,
                paidIn = paidIn,
                balance = balance,
            ),
            incomeDate = incomeDate,
            description = description,
            additionalInformation = additionalInformation,
            paidOut = paidOut,
            paidIn = paidIn,
            balance = balance,
            suggestedInclusion = suggestedInclusion,
            suggestedSourceCategory = suggestedSourceCategory,
            suggestedAmount = suggestedAmount,
            suggestedCurrency = paidIn?.currency ?: paidOut?.currency ?: balance?.currency ?: fallbackCurrency,
        )
    }

    private fun shouldSkipLine(line: String): Boolean {
        val normalized = line.lowercase().replace(multiWhitespaceRegex, " ").trim()
        return headerHints.any { it in normalized } ||
            normalized in headerLineFragments ||
            normalized.startsWith("page ") ||
            normalized.startsWith("გვერდი ") ||
            normalized.matches(pageCounterRegex) ||
            normalized.startsWith("account statement") ||
            normalized.startsWith("statement from account") ||
            normalized.startsWith("ამონაწერი ანგარიშიდან") ||
            normalized.startsWith("ანგარიშის ამონაწერი") ||
            normalized.startsWith("account holder") ||
            normalized.startsWith("ანგარიშის მფლობელი") ||
            normalized.startsWith("generated on") ||
            normalized.startsWith("გენერირებულია") ||
            normalized.matches(statementPeriodRegex) ||
            normalized.startsWith("opening balance") ||
            normalized.startsWith("closing balance")
    }

    private fun sanitizeLogicalLine(line: String): String {
        val trimmed = line.trim()
        var cutoff = trimmed.length

        inlineArtifactRegexes.forEach { regex ->
            val markerIndex = regex.find(trimmed)?.range?.first ?: return@forEach
            if (markerIndex >= dateTokenLength && hasTrailingAmount(trimmed.substring(0, markerIndex))) {
                cutoff = minOf(cutoff, markerIndex)
            }
        }

        return trimmed.substring(0, cutoff).trim()
    }

    private fun buildLogicalTransactionLines(lines: List<String>): List<String> {
        val logicalLines = mutableListOf<String>()
        var pendingLine: String? = null

        fun flushPending() {
            pendingLine?.let(logicalLines::add)
            pendingLine = null
        }

        lines.forEach { line ->
            when {
                isTransactionStart(line) -> {
                    flushPending()
                    pendingLine = line
                }
                shouldSkipLine(line) -> Unit
                pendingLine != null -> {
                    pendingLine = pendingLine + "  " + line
                }
            }
        }

        flushPending()
        return logicalLines
    }

    private fun isTransactionStart(line: String): Boolean = line.trim().matches(transactionLineRegex)

    private fun detectStatementCurrency(lines: List<String>): String? {
        lines.forEach { line ->
            currencyDetectionRegexes.forEach { regex ->
                val currency = regex.find(line)?.groupValues?.getOrNull(1)
                if (!currency.isNullOrBlank()) {
                    return currency.uppercase()
                }
            }
        }
        return null
    }

    private fun detectOpeningBalance(lines: List<String>): StatementMoney? {
        lines.forEach { line ->
            val match = openingBalanceRegex.find(line) ?: return@forEach
            val amount = match.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return@forEach
            return StatementMoney(
                amount = amount,
                currency = match.groupValues[2].uppercase(),
            )
        }
        return null
    }

    private fun inferCollapsedDirection(
        amount: BigDecimal,
        lineText: String,
        currency: String?,
    ): Pair<StatementMoney?, StatementMoney?> {
        val money = StatementMoney(amount = amount, currency = currency)
        val normalized = lineText.lowercase()
        return when {
            incomingDirectionHints.any { it in normalized } -> null to money
            outgoingDirectionHints.any { it in normalized } -> money to null
            else -> null to null
        }
    }

    private fun stripTrailingAmount(
        text: String,
        amount: BigDecimal,
    ): String = text.replace(Regex("${Regex.escape(amount.toPlainString())}\\s*$"), "").trim()

    private fun hasTrailingAmount(text: String): Boolean {
        val trimmed = text.trimEnd()
        return trailingAmountRegex.find(trimmed)?.range?.last == trimmed.lastIndex
    }

    private fun splitCondensedDescription(text: String): Pair<String, String?> {
        val normalized = text.replace(multiWhitespaceRegex, " ").trim()
        val marker = additionalInfoSplitMarkers.firstOrNull { normalized.contains(it, ignoreCase = true) }
        if (marker != null) {
            val markerIndex = normalized.indexOf(marker, ignoreCase = true)
            return normalized.substring(0, markerIndex).trim() to normalized.substring(markerIndex).trim()
        }

        knownDescriptionPrefixes.forEach { prefix ->
            if (normalized.startsWith(prefix, ignoreCase = true)) {
                return normalized.substring(0, prefix.length).trim() to normalized.substring(prefix.length).trim().ifBlank { null }
            }
        }

        return normalized to null
    }

    private fun parseMoney(
        value: String,
        fallbackCurrency: String?,
    ): StatementMoney? {
        if (value == "-" || value == "—") return null
        val currency = currencyRegex.find(value)?.groupValues?.getOrNull(1)?.uppercase() ?: fallbackCurrency
        val amountText = value
            .replace(currencyRegex, "")
            .replace(",", "")
            .trim()
        if (amountText.isBlank()) return null
        val amount = runCatching { BigDecimal(amountText) }.getOrNull() ?: return null
        return StatementMoney(amount = amount, currency = currency)
    }

    private fun fingerprintFor(
        incomeDate: LocalDate,
        description: String,
        additionalInformation: String?,
        paidOut: StatementMoney?,
        paidIn: StatementMoney?,
        balance: StatementMoney?,
    ): String {
        val normalized = listOf(
            incomeDate.toString(),
            description.trim().lowercase(),
            additionalInformation?.trim()?.lowercase().orEmpty(),
            moneyForFingerprint(paidOut),
            moneyForFingerprint(paidIn),
            moneyForFingerprint(balance),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun moneyForFingerprint(money: StatementMoney?): String =
        if (money == null) "" else "${money.amount.stripTrailingZeros().toPlainString()}:${money.currency.orEmpty()}"

    private companion object {
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
        val taxableHints = listOf(
            "software service",
            "software services",
            "consulting",
            "invoice",
            "development services",
            "service payment",
            "freelance",
            "პროგრამული მომსახურება",
            "პროგრამული სერვისი",
            "სერვისის გადახდა",
            "ინვოისი",
        )
        val nonTaxableHints = listOf(
            "internal transfer",
            "own account",
            "fee",
            "commission",
            "charge",
            "bank fee",
            "საკუთარ ანგარიშებს შორის გადარიცხვა",
            "შიდა გადარიცხვა",
            "საკომისიო",
            "კომისია",
        )
        val incomingDirectionHints = taxableHints + listOf(
            "client payment",
            "კლიენტის გადახდა",
        )
        val outgoingDirectionHints = nonTaxableHints + listOf(
            "transfer between your accounts",
            "საკუთარ ანგარიშებს შორის გადარიცხვა",
        )
        val bankFeeHints = listOf(
            "fee",
            "commission",
            "charge",
            "საკომისიო",
            "კომისია",
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
}
