package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreview
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TbcStatementParser @Inject constructor() {
    fun parse(
        sourceFileName: String,
        sourceFingerprint: String,
        extractedText: String,
    ): ImportedStatementPreview {
        val lines = normalizeTbcStatementLines(extractedText)
        val logicalLines = buildLogicalTransactionLines(lines)
        val openingBalance = detectOpeningBalance(lines)
        val statementCurrency = detectStatementCurrency(lines) ?: openingBalance?.currency

        val rows = mutableListOf<ImportedStatementPreviewRow>()
        var skippedLineCount = 0
        var previousBalance: BigDecimal? = openingBalance?.amount

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
}
