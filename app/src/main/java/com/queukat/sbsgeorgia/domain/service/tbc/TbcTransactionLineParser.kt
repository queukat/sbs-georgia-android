package com.queukat.sbsgeorgia.domain.service.tbc

import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import java.math.BigDecimal
import java.time.LocalDate

internal fun parseTransactionLine(
    line: String,
    statementCurrency: String?,
    previousBalance: BigDecimal?
): ImportedStatementPreviewRow? = parseColumnSeparatedTransactionLine(line, statementCurrency)
    ?: parseCollapsedTransactionLine(line, statementCurrency, previousBalance)

private fun parseColumnSeparatedTransactionLine(
    line: String,
    statementCurrency: String?
): ImportedStatementPreviewRow? {
    val parts = line.split(TbcStatementFormat.columnSeparator).map {
        it.trim()
    }.filter { it.isNotBlank() }
    if (parts.size < 5) return null

    val dateToken = parts.first()
    if (!TbcStatementFormat.dateRegex.matches(dateToken)) return null

    val incomeDate =
        runCatching { LocalDate.parse(dateToken, TbcStatementFormat.dateFormatter) }.getOrNull()
            ?: return null
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
        fallbackCurrency = statementCurrency
    )
}

private fun parseCollapsedTransactionLine(
    line: String,
    statementCurrency: String?,
    previousBalance: BigDecimal?
): ImportedStatementPreviewRow? {
    val dateToken = line.take(TbcStatementFormat.DATE_TOKEN_LENGTH)
    if (!TbcStatementFormat.dateRegex.matches(dateToken)) return null

    val incomeDate =
        runCatching { LocalDate.parse(dateToken, TbcStatementFormat.dateFormatter) }.getOrNull()
            ?: return null
    val balanceMatch = TbcStatementFormat.trailingAmountRegex.find(line) ?: return null
    val balanceAmount =
        balanceMatch.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
    var transactionText =
        line
            .substring(TbcStatementFormat.DATE_TOKEN_LENGTH, balanceMatch.range.first)
            .replace(TbcStatementFormat.multiWhitespaceRegex, " ")
            .trim()
    if (transactionText.isBlank()) return null

    val explicitAmount =
        TbcStatementFormat.trailingAmountRegex
            .find(transactionText)
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
                    val inferredDirection =
                        inferCollapsedDirection(
                            amount = explicitAmount,
                            lineText = transactionText,
                            currency = statementCurrency
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
            val inferredDirection =
                inferCollapsedDirection(
                    amount = explicitAmount,
                    lineText = transactionText,
                    currency = statementCurrency
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
        fallbackCurrency = statementCurrency
    )
}

private fun inferCollapsedDirection(
    amount: BigDecimal,
    lineText: String,
    currency: String?
): Pair<StatementMoney?, StatementMoney?> {
    val money = StatementMoney(amount = amount, currency = currency)
    val normalized = lineText.lowercase()
    return when {
        incomingDirectionHints.any { it in normalized } -> null to money
        outgoingDirectionHints.any { it in normalized } -> money to null
        else -> null to null
    }
}

private fun stripTrailingAmount(text: String, amount: BigDecimal): String =
    text.replace(Regex("${Regex.escape(amount.toPlainString())}\\s*$"), "").trim()

internal fun hasTrailingAmount(text: String): Boolean {
    val trimmed = text.trimEnd()
    return TbcStatementFormat.trailingAmountRegex
        .find(trimmed)
        ?.range
        ?.last == trimmed.lastIndex
}

private fun splitCondensedDescription(text: String): Pair<String, String?> {
    val normalized = text.replace(TbcStatementFormat.multiWhitespaceRegex, " ").trim()
    val marker = TbcStatementFormat.additionalInfoSplitMarkers.firstOrNull {
        normalized.contains(it, ignoreCase = true)
    }
    if (marker != null) {
        val markerIndex = normalized.indexOf(marker, ignoreCase = true)
        return normalized.substring(0, markerIndex).trim() to
            normalized.substring(markerIndex).trim()
    }

    TbcStatementFormat.knownDescriptionPrefixes.forEach { prefix ->
        if (normalized.startsWith(prefix, ignoreCase = true)) {
            return normalized.substring(0, prefix.length).trim() to
                normalized.substring(prefix.length).trim().ifBlank { null }
        }
    }

    return normalized to null
}

private fun parseMoney(value: String, fallbackCurrency: String?): StatementMoney? {
    if (value == "-" || value == "—") return null
    val currency =
        TbcStatementFormat.currencyRegex
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase() ?: fallbackCurrency
    val amountText =
        value
            .replace(TbcStatementFormat.currencyRegex, "")
            .replace(",", "")
            .trim()
    if (amountText.isBlank()) return null
    val amount = runCatching { BigDecimal(amountText) }.getOrNull() ?: return null
    return StatementMoney(amount = amount, currency = currency)
}
