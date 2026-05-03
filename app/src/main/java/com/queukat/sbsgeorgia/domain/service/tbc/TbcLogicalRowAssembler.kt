package com.queukat.sbsgeorgia.domain.service.tbc

import com.queukat.sbsgeorgia.domain.model.StatementMoney

internal fun buildLogicalTransactionLines(lines: List<String>): List<String> {
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

internal fun isTransactionStart(line: String): Boolean = line.trim().matches(TbcStatementFormat.transactionLineRegex)

internal fun detectStatementCurrency(lines: List<String>): String? {
    lines.forEach { line ->
        TbcStatementFormat.currencyDetectionRegexes.forEach { regex ->
            val currency = regex.find(line)?.groupValues?.getOrNull(1)
            if (!currency.isNullOrBlank()) {
                return currency.uppercase()
            }
        }
    }
    return null
}

internal fun detectOpeningBalance(lines: List<String>): StatementMoney? {
    lines.forEach { line ->
        val match = TbcStatementFormat.openingBalanceRegex.find(line) ?: return@forEach
        val amount = match.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return@forEach
        return StatementMoney(
            amount = amount,
            currency = match.groupValues[2].uppercase()
        )
    }
    return null
}

private fun shouldSkipLine(line: String): Boolean {
    val normalized = line.lowercase().replace(TbcStatementFormat.multiWhitespaceRegex, " ").trim()
    return TbcStatementFormat.headerHints.any { it in normalized } ||
        normalized in TbcStatementFormat.headerLineFragments ||
        normalized.startsWith("page ") ||
        normalized.startsWith("გვერდი ") ||
        normalized.matches(TbcStatementFormat.pageCounterRegex) ||
        normalized.startsWith("account statement") ||
        normalized.startsWith("statement from account") ||
        normalized.startsWith("ამონაწერი ანგარიშიდან") ||
        normalized.startsWith("ანგარიშის ამონაწერი") ||
        normalized.startsWith("account holder") ||
        normalized.startsWith("ანგარიშის მფლობელი") ||
        normalized.startsWith("generated on") ||
        normalized.startsWith("გენერირებულია") ||
        normalized.matches(TbcStatementFormat.statementPeriodRegex) ||
        normalized.startsWith("opening balance") ||
        normalized.startsWith("closing balance")
}
