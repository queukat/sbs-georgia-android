package com.queukat.sbsgeorgia.domain.service.tbc

import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import java.math.BigDecimal
import java.time.LocalDate

internal fun parseTransactionLine(
    line: String,
    statementCurrency: String?,
    previousBalance: BigDecimal?
): ImportedStatementPreviewRow? = parseColumnSeparatedTransactionLine(
    line = line,
    statementCurrency = statementCurrency,
    previousBalance = previousBalance
)
    ?: parseCollapsedTransactionLine(line, statementCurrency, previousBalance)

private fun parseColumnSeparatedTransactionLine(
    line: String,
    statementCurrency: String?,
    previousBalance: BigDecimal?
): ImportedStatementPreviewRow? {
    val parts = line.split(TbcStatementFormat.columnSeparator).map {
        it.trim()
    }.filter { it.isNotBlank() }
    if (parts.size < 4) return null

    val dateToken = parts.first()
    if (!TbcStatementFormat.dateRegex.matches(dateToken)) return null

    val incomeDate =
        runCatching { LocalDate.parse(dateToken, TbcStatementFormat.dateFormatter) }.getOrNull()
            ?: return null
    val hasCompleteMoneyColumns = parts.takeLast(3).all(::isMoneyColumn)
    val moneyColumnCount = if (hasCompleteMoneyColumns) 3 else 2
    val trailingColumns = parts.takeLast(moneyColumnCount)
    val leadingColumns = parts.drop(1).dropLast(moneyColumnCount)
    if (leadingColumns.isEmpty()) return null

    val paidOut: StatementMoney?
    val paidIn: StatementMoney?
    val balance: StatementMoney?
    val fallbackAmount: BigDecimal?
    if (hasCompleteMoneyColumns) {
        paidOut = parseMoney(trailingColumns[0], statementCurrency)
        paidIn = parseMoney(trailingColumns[1], statementCurrency)
        balance = parseMoney(trailingColumns[2], statementCurrency)
        fallbackAmount = null
    } else {
        val movement = parseMoney(trailingColumns[0], statementCurrency) ?: return null
        balance = parseMoney(trailingColumns[1], statementCurrency) ?: return null
        val inferredDirection = inferDirectionFromBalanceOrText(
            amount = movement.amount,
            balance = balance.amount,
            previousBalance = previousBalance,
            lineText = leadingColumns.joinToString(" "),
            currency = movement.currency ?: statementCurrency
        )
        paidOut = inferredDirection.first
        paidIn = inferredDirection.second
        fallbackAmount = movement.amount.takeIf { paidOut == null && paidIn == null }
    }

    return buildPreviewRow(
        incomeDate = incomeDate,
        description = leadingColumns.first(),
        additionalInformation = leadingColumns.drop(1).joinToString(" ").ifBlank { null },
        paidOut = paidOut,
        paidIn = paidIn,
        balance = balance,
        fallbackAmount = fallbackAmount,
        fallbackCurrency = statementCurrency
    )
}

private fun isMoneyColumn(value: String): Boolean =
    value == "-" || value == "—" || parseMoney(value, fallbackCurrency = null) != null

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

    val explicitAmountMatch = TbcStatementFormat.trailingAmountRegex.find(transactionText)
    val explicitAmountText = explicitAmountMatch?.groupValues?.getOrNull(1)
    val explicitAmount = explicitAmountText?.replace(",", "")?.toBigDecimalOrNull()
    val balance = StatementMoney(amount = balanceAmount, currency = statementCurrency)

    val paidOut: StatementMoney?
    val paidIn: StatementMoney?
    val fallbackAmount: BigDecimal?
    when {
        previousBalance != null -> {
            val delta = balanceAmount.subtract(previousBalance)
            val deltaAmount = delta.abs().takeIf { it > BigDecimal.ZERO }
            val normalizedExplicitAmountText = explicitAmountText?.replace(",", "")
            val deltaAmountText = deltaAmount?.toPlainString()
            val attachedNumericPrefix =
                if (deltaAmountText != null &&
                    normalizedExplicitAmountText?.endsWith(deltaAmountText) == true
                ) {
                    normalizedExplicitAmountText.dropLast(deltaAmountText.length)
                } else {
                    null
                }
            val deltaMatchesPrintedSuffix =
                attachedNumericPrefix != null &&
                    attachedNumericPrefix.length >= ATTACHED_IDENTIFIER_MIN_LENGTH &&
                    attachedNumericPrefix.all(Char::isDigit)
            val movementAmount =
                when {
                    deltaAmount != null &&
                        (
                            explicitAmount == null ||
                                explicitAmount.compareTo(deltaAmount) == 0 ||
                                deltaMatchesPrintedSuffix
                            ) -> deltaAmount
                    else -> explicitAmount
                }
            if (movementAmount == null) {
                paidOut = null
                paidIn = null
                fallbackAmount = null
            } else {
                transactionText = stripTrailingAmount(transactionText, movementAmount)
                val inferredDirection = inferDirectionFromBalanceOrText(
                    amount = movementAmount,
                    balance = balanceAmount,
                    previousBalance = previousBalance,
                    lineText = transactionText,
                    currency = statementCurrency
                )
                paidOut = inferredDirection.first
                paidIn = inferredDirection.second
                fallbackAmount = movementAmount.takeIf { paidOut == null && paidIn == null }
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

private const val ATTACHED_IDENTIFIER_MIN_LENGTH = 6

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

private fun inferDirectionFromBalanceOrText(
    amount: BigDecimal,
    balance: BigDecimal,
    previousBalance: BigDecimal?,
    lineText: String,
    currency: String?
): Pair<StatementMoney?, StatementMoney?> {
    val delta = previousBalance?.let(balance::subtract)
    if (delta != null &&
        delta.signum() != 0 &&
        delta.abs().compareTo(amount) == 0
    ) {
        val money = StatementMoney(amount = amount, currency = currency)
        return if (delta.signum() > 0) null to money else money to null
    }

    return inferCollapsedDirection(
        amount = amount,
        lineText = lineText,
        currency = currency
    )
}

private fun stripTrailingAmount(text: String, amount: BigDecimal): String {
    val match = TbcStatementFormat.trailingAmountRegex.find(text) ?: return text.trim()
    val trailingAmount = match.groupValues[1].replace(",", "").toBigDecimalOrNull()
    if (trailingAmount?.compareTo(amount) == 0) {
        return text.removeRange(match.range).trim()
    }

    val trimmed = text.trimEnd()
    val plainAmount = amount.toPlainString()
    return if (trimmed.endsWith(plainAmount)) {
        trimmed.dropLast(plainAmount.length).trim()
    } else {
        trimmed
    }
}

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
