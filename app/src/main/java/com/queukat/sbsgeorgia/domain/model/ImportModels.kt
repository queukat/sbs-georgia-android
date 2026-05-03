package com.queukat.sbsgeorgia.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class StatementMoney(val amount: BigDecimal, val currency: String?)

data class ImportedStatementPreview(
    val sourceFileName: String,
    val sourceFingerprint: String,
    val rows: List<ImportedStatementPreviewRow>,
    val skippedLineCount: Int = 0
)

data class ImportedStatementPreviewRow(
    val transactionFingerprint: String,
    val incomeDate: LocalDate?,
    val description: String,
    val additionalInformation: String?,
    val paidOut: StatementMoney?,
    val paidIn: StatementMoney?,
    val balance: StatementMoney?,
    val suggestedInclusion: DeclarationInclusion,
    val suggestedSourceCategory: String,
    val suggestedAmount: BigDecimal,
    val suggestedCurrency: String?,
    val duplicate: Boolean = false
)

data class ApprovedImportedStatementRow(
    val transactionFingerprint: String,
    val incomeDate: LocalDate?,
    val description: String,
    val additionalInformation: String?,
    val paidOut: StatementMoney?,
    val paidIn: StatementMoney?,
    val balance: StatementMoney?,
    val suggestedInclusion: DeclarationInclusion,
    val finalInclusion: DeclarationInclusion,
    val amount: BigDecimal,
    val currency: String,
    val sourceCategory: String,
    val duplicate: Boolean
)

data class ImportedStatementImportInfo(
    val sourceFileName: String,
    val sourceFingerprint: String,
    val importedAtEpochMillis: Long
)

data class LoadImportPreviewResult(
    val preview: ImportedStatementPreview? = null,
    val existingImport: ImportedStatementImportInfo? = null,
    val message: String? = null
) {
    val alreadyImported: Boolean get() = existingImport != null
}

data class ConfirmImportedStatementResult(
    val importedIncomeCount: Int,
    val storedTransactionCount: Int,
    val skippedDuplicateCount: Int,
    val excludedCount: Int
)

data class ConfirmStatementImportWorkflowResult(
    val importResult: ConfirmImportedStatementResult,
    val autoResolvedFxEntryCount: Int,
    val remainingUnresolvedFxEntryCount: Int,
    val reviewRequiredTaxPaymentCount: Int = 0
)
