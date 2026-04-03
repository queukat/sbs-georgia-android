package com.queukat.sbsgeorgia.ui.importstatement

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.usecase.ConfirmStatementImportUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadStatementImportPreviewUseCase
import com.queukat.sbsgeorgia.ui.common.canonicalSourceCategory
import com.queukat.sbsgeorgia.ui.common.displaySourceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ImportStatementViewModel @Inject constructor(
    private val loadStatementImportPreviewUseCase: LoadStatementImportPreviewUseCase,
    private val confirmStatementImportUseCase: ConfirmStatementImportUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportStatementUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ImportStatementEffect>()
    val effects = _effects.asSharedFlow()

    fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
            runCatching {
                loadStatementImportPreviewUseCase(uri.toString())
            }.onSuccess { result ->
                if (result.alreadyImported) {
                    _uiState.value = ImportStatementUiState(
                        errorMessage = appContext.getString(R.string.import_statement_message_duplicate_file),
                    )
                    return@onSuccess
                }

                val preview = result.preview
                if (preview == null) {
                    _uiState.value = ImportStatementUiState(
                        errorMessage = appContext.getString(R.string.import_statement_error_no_preview),
                    )
                    return@onSuccess
                }

                val rows = preview.rows.map { row ->
                    ImportStatementRowUiState(
                        transactionFingerprint = row.transactionFingerprint,
                        incomeDate = row.incomeDate ?: LocalDate.now(),
                        description = row.description,
                        additionalInformation = row.additionalInformation,
                        paidOutLabel = row.paidOut?.toDisplayLabel(),
                        paidInLabel = row.paidIn?.toDisplayLabel(),
                        balanceLabel = row.balance?.toDisplayLabel(),
                        suggestedInclusion = row.suggestedInclusion,
                        finalInclusion = if (row.duplicate) {
                            DeclarationInclusion.EXCLUDED
                        } else if (row.suggestedInclusion == DeclarationInclusion.INCLUDED) {
                            DeclarationInclusion.INCLUDED
                        } else {
                            DeclarationInclusion.EXCLUDED
                        },
                        amount = row.suggestedAmount.toPlainString(),
                        currency = row.suggestedCurrency.orEmpty(),
                        sourceCategory = displaySourceCategory(appContext, row.suggestedSourceCategory),
                        duplicate = row.duplicate,
                    )
                }
                _uiState.value = ImportStatementUiState(
                    sourceFileName = preview.sourceFileName,
                    sourceFingerprint = preview.sourceFingerprint,
                    rows = rows,
                    selectedIncomeCount = rows.count { it.finalInclusion == DeclarationInclusion.INCLUDED && !it.duplicate },
                    infoMessage = if (preview.skippedLineCount > 0) {
                        appContext.getString(R.string.import_statement_message_skipped_lines, preview.skippedLineCount)
                    } else {
                        null
                    },
                )
            }.onFailure { error ->
                _uiState.value = ImportStatementUiState(
                    errorMessage = if (error.message?.startsWith("No TBC statement transaction rows were recognized.") == true) {
                        appContext.getString(R.string.import_statement_error_unsupported_pdf)
                    } else {
                        appContext.getString(R.string.import_statement_error_parse_failed)
                    },
                )
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun includeAsTaxable(transactionFingerprint: String, included: Boolean) {
        updateRow(transactionFingerprint) { row ->
            row.copy(
                finalInclusion = if (included) DeclarationInclusion.INCLUDED else DeclarationInclusion.EXCLUDED,
            )
        }
    }

    fun updateDate(transactionFingerprint: String, value: LocalDate) {
        updateRow(transactionFingerprint) { row -> row.copy(incomeDate = value) }
    }

    fun updateAmount(transactionFingerprint: String, value: String) {
        updateRow(transactionFingerprint) { row -> row.copy(amount = value) }
    }

    fun updateCurrency(transactionFingerprint: String, value: String) {
        updateRow(transactionFingerprint) { row -> row.copy(currency = value.uppercase()) }
    }

    fun updateSourceCategory(transactionFingerprint: String, value: String) {
        updateRow(transactionFingerprint) { row -> row.copy(sourceCategory = value) }
    }

    fun importApprovedRows() {
        val current = _uiState.value
        val sourceFileName = current.sourceFileName
        val sourceFingerprint = current.sourceFingerprint
        if (sourceFileName.isNullOrBlank() || sourceFingerprint.isNullOrBlank()) {
            _uiState.value = current.copy(errorMessage = appContext.getString(R.string.import_statement_error_pick_pdf_first))
            return
        }

        val rows = current.rows
        val invalidIncludedRow = rows.firstOrNull { row ->
            row.finalInclusion == DeclarationInclusion.INCLUDED && (
                row.amount.toBigDecimalOrNull() == null ||
                    row.amount.toBigDecimalOrNull()?.signum() != 1 ||
                    row.currency.isBlank() ||
                    row.sourceCategory.isBlank()
                )
        }
        if (invalidIncludedRow != null) {
            _uiState.value = current.copy(
                errorMessage = appContext.getString(R.string.import_statement_error_invalid_rows),
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isImporting = true, errorMessage = null)
            runCatching {
                confirmStatementImportUseCase(
                    sourceFileName = sourceFileName,
                    sourceFingerprint = sourceFingerprint,
                    rows = rows.map { it.toApprovedRow() },
                )
            }.onSuccess { result ->
                val summaryMessage = appContext.getString(
                    R.string.import_statement_message_import_summary,
                    result.importResult.importedIncomeCount,
                    result.importResult.storedTransactionCount,
                    result.importResult.skippedDuplicateCount,
                    result.importResult.excludedCount,
                )
                val fxMessage = when {
                    result.autoResolvedFxEntryCount > 0 && result.remainingUnresolvedFxEntryCount == 0 ->
                        appContext.getString(
                            R.string.import_statement_message_fx_auto_resolved_all,
                            result.autoResolvedFxEntryCount,
                        )
                    result.autoResolvedFxEntryCount > 0 ->
                        appContext.getString(
                            R.string.import_statement_message_fx_auto_resolved_partial,
                            result.autoResolvedFxEntryCount,
                            result.remainingUnresolvedFxEntryCount,
                        )
                    result.remainingUnresolvedFxEntryCount > 0 ->
                        appContext.getString(
                            R.string.import_statement_message_fx_manual_review_needed,
                            result.remainingUnresolvedFxEntryCount,
                        )
                    else -> null
                }
                _uiState.value = ImportStatementUiState(
                    infoMessage = listOfNotNull(summaryMessage, fxMessage).joinToString("\n"),
                )
                _effects.emit(
                    ImportStatementEffect.Message(
                        appContext.getString(
                            R.string.import_statement_message_import_snackbar,
                            result.importResult.importedIncomeCount,
                            sourceFileName,
                        ),
                    ),
                )
            }.onFailure {
                _uiState.value = current.copy(
                    isImporting = false,
                    errorMessage = appContext.getString(R.string.import_statement_error_import_failed),
                )
                return@launch
            }
        }
    }

    private fun updateRow(
        transactionFingerprint: String,
        transform: (ImportStatementRowUiState) -> ImportStatementRowUiState,
    ) {
        val updatedRows = _uiState.value.rows.map { row ->
            if (row.transactionFingerprint == transactionFingerprint && !row.duplicate) {
                transform(row)
            } else {
                row
            }
        }
        _uiState.value = _uiState.value.copy(
            rows = updatedRows,
            selectedIncomeCount = updatedRows.count {
                it.finalInclusion == DeclarationInclusion.INCLUDED && !it.duplicate
            },
            errorMessage = null,
        )
    }

    private fun ImportStatementRowUiState.toApprovedRow(): ApprovedImportedStatementRow = ApprovedImportedStatementRow(
        transactionFingerprint = transactionFingerprint,
        incomeDate = incomeDate,
        description = description,
        additionalInformation = additionalInformation,
        paidOut = paidOutLabel?.let(::parseMoneyLabel),
        paidIn = paidInLabel?.let(::parseMoneyLabel),
        balance = balanceLabel?.let(::parseMoneyLabel),
        suggestedInclusion = suggestedInclusion,
        finalInclusion = finalInclusion,
        amount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        currency = currency.trim().uppercase(),
        sourceCategory = canonicalSourceCategory(appContext, sourceCategory),
        duplicate = duplicate,
    )

    private fun parseMoneyLabel(value: String): StatementMoney {
        val parts = value.trim().split(" ")
        return if (parts.size >= 2) {
            StatementMoney(
                amount = parts.first().replace(",", "").toBigDecimal(),
                currency = parts.last(),
            )
        } else {
            StatementMoney(
                amount = parts.first().replace(",", "").toBigDecimal(),
                currency = null,
            )
        }
    }

    private fun StatementMoney.toDisplayLabel(): String =
        listOf(amount.stripTrailingZeros().toPlainString(), currency.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
