package com.queukat.sbsgeorgia.ui.importstatement

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import java.time.LocalDate

data class ImportStatementUiState(
    val sourceFileName: String? = null,
    val sourceFingerprint: String? = null,
    val rows: List<ImportStatementRowUiState> = emptyList(),
    val selectedIncomeCount: Int = 0,
    val detectedTaxPaymentCount: Int = 0,
    val recognizedOutgoingCount: Int = 0,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

data class ImportStatementRowUiState(
    val transactionFingerprint: String,
    val incomeDate: LocalDate?,
    val description: String,
    val additionalInformation: String?,
    val paidOutLabel: String?,
    val paidInLabel: String?,
    val balanceLabel: String?,
    val suggestedInclusion: DeclarationInclusion,
    val finalInclusion: DeclarationInclusion,
    val amount: String,
    val currency: String,
    val sourceCategory: String,
    val isTaxPaymentCandidate: Boolean = false,
    val duplicate: Boolean,
)

sealed interface ImportStatementEffect {
    data class Message(val text: String) : ImportStatementEffect
}

internal fun ImportStatementRowUiState.isInvalidForIncludedImport(): Boolean =
    finalInclusion == DeclarationInclusion.INCLUDED && (
        incomeDate == null ||
            amount.toBigDecimalOrNull()?.signum() != 1 ||
            !isIsoLikeCurrencyCode(currency) ||
            sourceCategory.isBlank()
        )
