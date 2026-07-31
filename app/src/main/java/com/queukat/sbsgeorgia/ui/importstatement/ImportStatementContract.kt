package com.queukat.sbsgeorgia.ui.importstatement

import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import java.time.LocalDate
import java.time.YearMonth

data class ImportStatementUiState(
    val sourceFileName: String? = null,
    val sourceFingerprint: String? = null,
    val rows: List<ImportStatementRowUiState> = emptyList(),
    val selectedIncomeCount: Int = 0,
    val detectedTaxPaymentCount: Int = 0,
    val recognizedOutgoingCount: Int = 0,
    val invalidIncludedCount: Int = 0,
    val canImport: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importSuccess: ImportStatementImportSuccessUiState? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

data class ImportStatementImportSuccessUiState(
    val importedIncomeCount: Int,
    val storedTransactionCount: Int,
    val skippedDuplicateCount: Int,
    val excludedCount: Int,
    val targetMonth: YearMonth?,
    val detailMessage: String?
)

data class ImportStatementRowUiState(
    val transactionFingerprint: String,
    val incomeDate: LocalDate?,
    val description: String,
    val additionalInformation: String?,
    val paidOut: StatementMoney?,
    val paidIn: StatementMoney?,
    val balance: StatementMoney?,
    val suggestedInclusion: DeclarationInclusion,
    val finalInclusion: DeclarationInclusion,
    val amount: String,
    val currency: String,
    val sourceCategory: String,
    val isTaxPaymentCandidate: Boolean = false,
    val duplicate: Boolean,
    val reviewDecisionMade: Boolean = false
)

sealed interface ImportStatementEffect {
    data class Message(val text: String) : ImportStatementEffect
}

internal fun ImportStatementRowUiState.isInvalidForIncludedImport(): Boolean =
    finalInclusion == DeclarationInclusion.INCLUDED &&
        (
            incomeDate == null ||
                amount.toBigDecimalOrNull()?.signum() != 1 ||
                !isIsoLikeCurrencyCode(currency) ||
                sourceCategory.isBlank()
            )

internal fun ImportStatementRowUiState.needsReview(): Boolean = !duplicate &&
    (
        isInvalidForIncludedImport() ||
            isPendingManualReviewDecision()
        )

internal fun ImportStatementRowUiState.requiresManualReviewDecision(): Boolean =
    !duplicate && suggestedInclusion == DeclarationInclusion.REVIEW_REQUIRED

internal fun ImportStatementRowUiState.isPendingManualReviewDecision(): Boolean =
    requiresManualReviewDecision() && !reviewDecisionMade

internal fun List<ImportStatementRowUiState>.invalidIncludedCount(): Int =
    count(ImportStatementRowUiState::isInvalidForIncludedImport)

internal fun List<ImportStatementRowUiState>.willImportCount(): Int =
    count { it.finalInclusion == DeclarationInclusion.INCLUDED && !it.duplicate }

internal fun List<ImportStatementRowUiState>.canConfirmImport(): Boolean =
    any { !it.duplicate } && invalidIncludedCount() == 0

internal fun List<ImportStatementRowUiState>.needsReviewCount(): Int = count(
    ImportStatementRowUiState::needsReview
)

internal fun List<ImportStatementRowUiState>.pendingReviewDecisionCount(): Int = count(
    ImportStatementRowUiState::isPendingManualReviewDecision
)

internal fun List<ImportStatementRowUiState>.excludedCount(): Int =
    count { it.finalInclusion == DeclarationInclusion.EXCLUDED && !it.duplicate }

internal fun List<ImportStatementRowUiState>.duplicateCount(): Int = count(
    ImportStatementRowUiState::duplicate
)

internal fun List<ImportStatementRowUiState>.taxPaymentCandidateCount(): Int =
    count { it.isTaxPaymentCandidate && !it.duplicate }

internal enum class ImportStatementFilter(val titleRes: Int, val testTag: String) {
    NEEDS_REVIEW(
        R.string.import_statement_filter_needs_review,
        "needs-review"
    ),
    TAX_PAYMENTS(
        R.string.import_statement_filter_tax_payments,
        "tax-payments"
    ),
    WILL_IMPORT(
        R.string.import_statement_filter_will_import,
        "will-import"
    ),
    EXCLUDED(
        R.string.import_statement_filter_excluded,
        "excluded"
    ),
    DUPLICATES(
        R.string.import_statement_filter_duplicates,
        "duplicates"
    )
}

internal fun List<ImportStatementRowUiState>.filterFor(filter: ImportStatementFilter): List<ImportStatementRowUiState> =
    when (filter) {
        ImportStatementFilter.NEEDS_REVIEW -> filter(ImportStatementRowUiState::needsReview)
        ImportStatementFilter.TAX_PAYMENTS ->
            filter { it.isTaxPaymentCandidate && !it.duplicate }
        ImportStatementFilter.WILL_IMPORT ->
            filter { it.finalInclusion == DeclarationInclusion.INCLUDED && !it.duplicate }
        ImportStatementFilter.EXCLUDED ->
            filter { it.finalInclusion == DeclarationInclusion.EXCLUDED && !it.duplicate }
        ImportStatementFilter.DUPLICATES -> filter(ImportStatementRowUiState::duplicate)
    }

internal fun List<ImportStatementRowUiState>.countFor(filter: ImportStatementFilter): Int = filterFor(filter).size
