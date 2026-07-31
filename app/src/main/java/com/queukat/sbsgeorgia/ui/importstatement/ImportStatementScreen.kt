@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.importstatement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.ui.common.ActionFlowRow
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.SbsScreenScaffold
import com.queukat.sbsgeorgia.ui.common.SbsStickyActionContainer
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import java.time.YearMonth

@Composable
fun ImportStatementRoute(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenMonths: () -> Unit
) {
    val viewModel: ImportStatementViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.loadDocument(uri)
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is ImportStatementEffect.Message) {
                snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    ImportStatementScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPickPdf = { pickerLauncher.launch(arrayOf("application/pdf")) },
        onIncludeAsTaxableChanged = viewModel::includeAsTaxable,
        onDateChanged = viewModel::updateDate,
        onAmountChanged = viewModel::updateAmount,
        onCurrencyChanged = viewModel::updateCurrency,
        onSourceCategoryChanged = viewModel::updateSourceCategory,
        onExcludePendingReviewRows = viewModel::excludePendingReviewRows,
        onImportApproved = viewModel::importApprovedRows,
        onOpenMonth = onOpenMonth,
        onOpenMonths = onOpenMonths
    )
}

@Composable
fun ImportStatementScreen(
    innerPadding: PaddingValues,
    uiState: ImportStatementUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onIncludeAsTaxableChanged: (String, Boolean) -> Unit,
    onDateChanged: (String, java.time.LocalDate) -> Unit,
    onAmountChanged: (String, String) -> Unit,
    onCurrencyChanged: (String, String) -> Unit,
    onSourceCategoryChanged: (String, String) -> Unit,
    onExcludePendingReviewRows: () -> Unit,
    onImportApproved: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenMonths: () -> Unit
) {
    var selectedFilter by rememberSaveable(uiState.sourceFingerprint, uiState.rows.size) {
        mutableStateOf(
            if (uiState.rows.needsReviewCount() > 0) {
                ImportStatementFilter.NEEDS_REVIEW
            } else {
                ImportStatementFilter.WILL_IMPORT
            }
        )
    }
    val success = uiState.importSuccess
    val filteredRows = uiState.rows.filterFor(selectedFilter)

    SbsScreenScaffold(
        innerPadding = innerPadding,
        title = stringResource(R.string.import_statement_title),
        onBack = onBack,
        topActions = {
            TextButton(onClick = onPickPdf, enabled = !uiState.isLoading) {
                Text(
                    stringResource(
                        if (uiState.isLoading) {
                            R.string.import_statement_parsing
                        } else {
                            R.string.import_statement_pick_pdf
                        }
                    )
                )
            }
        },
        bottomAction = {
            if (success == null && uiState.rows.isNotEmpty()) {
                ImportStatementBottomBar(
                    uiState = uiState,
                    onImportApproved = onImportApproved
                )
            }
        },
        snackbarHostState = snackbarHostState
    ) { contentPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (success != null) {
                item {
                    ImportStatementSuccessState(
                        success = success,
                        onOpenMonth = onOpenMonth,
                        onOpenMonths = onOpenMonths
                    )
                }
            } else {
                item {
                    ImportStatementFlowSummary(uiState = uiState)
                }
                if (uiState.rows.isNotEmpty()) {
                    item {
                        ImportStatementReviewSummary(
                            rows = uiState.rows,
                            onExcludePendingReviewRows = {
                                onExcludePendingReviewRows()
                                selectedFilter =
                                    if (uiState.rows.taxPaymentCandidateCount() > 0) {
                                        ImportStatementFilter.TAX_PAYMENTS
                                    } else {
                                        ImportStatementFilter.WILL_IMPORT
                                    }
                            },
                            onTaxPaymentsSelected = {
                                selectedFilter = ImportStatementFilter.TAX_PAYMENTS
                            }
                        )
                    }
                    item {
                        ImportStatementFilterTabs(
                            selectedFilter = selectedFilter,
                            rows = uiState.rows,
                            onFilterSelected = { selectedFilter = it }
                        )
                    }
                    if (filteredRows.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.import_statement_filter_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("import-filter-empty")
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredRows,
                            key = { index, row -> "${row.transactionFingerprint}:$index" }
                        ) { _, row ->
                            ImportStatementRowCard(
                                row = row,
                                onIncludeAsTaxableChanged = onIncludeAsTaxableChanged,
                                onDateChanged = onDateChanged,
                                onAmountChanged = onAmountChanged,
                                onCurrencyChanged = onCurrencyChanged,
                                onSourceCategoryChanged = onSourceCategoryChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStatementFlowSummary(uiState: ImportStatementUiState) {
    AppSection(title = stringResource(R.string.import_statement_section_flow)) {
        Text(stringResource(R.string.import_statement_flow_body))
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        uiState.sourceFileName?.let {
            Text(
                stringResource(R.string.import_statement_file_selected, it),
                modifier = Modifier.testTag("import-selected-file")
            )
        }
        uiState.infoMessage?.let { Text(it) }
        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("import-statement-error")
            )
        }
        if (uiState.rows.isEmpty()) {
            Text(stringResource(R.string.import_statement_pick_hint))
        }
    }
}

@Composable
private fun ImportStatementReviewSummary(
    rows: List<ImportStatementRowUiState>,
    onExcludePendingReviewRows: () -> Unit,
    onTaxPaymentsSelected: () -> Unit
) {
    AppSection(
        title = stringResource(R.string.import_statement_summary_title),
        modifier = Modifier.testTag("import-review-summary")
    ) {
        val pendingReviewDecisionCount = rows.pendingReviewDecisionCount()
        SummaryRow(
            label = stringResource(R.string.import_statement_summary_will_import),
            value = rows.willImportCount(),
            testTag = "import-summary-will-import"
        )
        SummaryRow(
            label = stringResource(R.string.import_statement_summary_needs_review),
            value = rows.needsReviewCount(),
            testTag = "import-summary-needs-review"
        )
        SummaryRow(
            label = stringResource(R.string.import_statement_summary_excluded),
            value = rows.excludedCount(),
            testTag = "import-summary-excluded"
        )
        SummaryRow(
            label = stringResource(R.string.import_statement_summary_duplicates),
            value = rows.duplicateCount(),
            testTag = "import-summary-duplicates"
        )
        SummaryRow(
            label = stringResource(R.string.import_statement_summary_tax_payments),
            value = rows.taxPaymentCandidateCount(),
            testTag = "import-summary-tax-payments",
            onClick = onTaxPaymentsSelected
        )
        if (pendingReviewDecisionCount > 0) {
            Text(
                text = stringResource(
                    R.string.import_statement_review_pending_decisions,
                    pendingReviewDecisionCount
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("import-review-pending-decisions")
            )
            TextButton(
                onClick = onExcludePendingReviewRows,
                modifier = Modifier.testTag("import-review-exclude-pending")
            ) {
                Text(stringResource(R.string.import_statement_review_exclude_pending))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int, testTag: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && value > 0) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun ImportStatementFilterTabs(
    selectedFilter: ImportStatementFilter,
    rows: List<ImportStatementRowUiState>,
    onFilterSelected: (ImportStatementFilter) -> Unit
) {
    ActionFlowRow(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("import-filter-tabs"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImportStatementFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.testTag("import-filter-${filter.testTag}"),
                label = {
                    Text(
                        stringResource(
                            filter.titleRes,
                            rows.countFor(filter)
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ImportStatementRowCard(
    row: ImportStatementRowUiState,
    onIncludeAsTaxableChanged: (String, Boolean) -> Unit,
    onDateChanged: (String, java.time.LocalDate) -> Unit,
    onAmountChanged: (String, String) -> Unit,
    onCurrencyChanged: (String, String) -> Unit,
    onSourceCategoryChanged: (String, String) -> Unit
) {
    var expanded by rememberSaveable(row.transactionFingerprint) { mutableStateOf(false) }
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("import-row-${row.transactionFingerprint}")
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = row.incomeDate?.formatIsoDate()
                            ?: stringResource(R.string.common_select_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = row.description.ifBlank {
                            stringResource(R.string.common_not_detected)
                        },
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = row.reviewAmountLabel(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(row.statusLabelRes()),
                    color =
                    if (row.isInvalidForIncludedImport()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("import-row-status-${row.transactionFingerprint}")
                )
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("import-row-toggle-${row.transactionFingerprint}")
            ) {
                Text(
                    stringResource(
                        if (expanded) {
                            R.string.import_statement_hide_details
                        } else {
                            R.string.import_statement_show_details
                        }
                    )
                )
            }
            if (expanded) {
                ImportStatementRowDetails(
                    row = row,
                    onIncludeAsTaxableChanged = onIncludeAsTaxableChanged,
                    onDateChanged = onDateChanged,
                    onAmountChanged = onAmountChanged,
                    onCurrencyChanged = onCurrencyChanged,
                    onSourceCategoryChanged = onSourceCategoryChanged
                )
            }
        }
    }
}

@Composable
private fun ImportStatementRowDetails(
    row: ImportStatementRowUiState,
    onIncludeAsTaxableChanged: (String, Boolean) -> Unit,
    onDateChanged: (String, java.time.LocalDate) -> Unit,
    onAmountChanged: (String, String) -> Unit,
    onCurrencyChanged: (String, String) -> Unit,
    onSourceCategoryChanged: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        row.additionalInformation?.takeIf { it.isNotBlank() }?.let { Text(it) }
        row.paidIn?.toDisplayLabel()?.let {
            Text(stringResource(R.string.import_statement_paid_in, it))
        }
        row.paidOut?.toDisplayLabel()?.let {
            Text(stringResource(R.string.import_statement_paid_out, it))
        }
        row.balance?.toDisplayLabel()?.let {
            Text(stringResource(R.string.import_statement_balance, it))
        }
        ActionFlowRow {
            FilterChip(
                selected = row.finalInclusion == DeclarationInclusion.INCLUDED,
                onClick = {
                    onIncludeAsTaxableChanged(row.transactionFingerprint, true)
                },
                enabled = !row.duplicate,
                modifier = Modifier.testTag("import-taxable-${row.transactionFingerprint}"),
                label = { Text(stringResource(R.string.import_statement_taxable_income)) }
            )
            FilterChip(
                selected = row.finalInclusion == DeclarationInclusion.EXCLUDED,
                onClick = {
                    onIncludeAsTaxableChanged(row.transactionFingerprint, false)
                },
                enabled = !row.duplicate,
                modifier = Modifier.testTag("import-exclude-${row.transactionFingerprint}"),
                label = {
                    Text(
                        stringResource(
                            if (row.duplicate) {
                                R.string.import_statement_duplicate
                            } else {
                                R.string.import_statement_exclude
                            }
                        )
                    )
                }
            )
        }
        Text(row.reviewHint(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        DatePickerField(
            label = stringResource(R.string.import_statement_income_date),
            value = row.incomeDate,
            onValueChange = { onDateChanged(row.transactionFingerprint, it) },
            placeholderText = stringResource(R.string.common_select_date),
            enabled = !row.duplicate
        )
        DecimalField(
            label = stringResource(R.string.import_statement_amount),
            value = row.amount,
            onValueChange = { onAmountChanged(row.transactionFingerprint, it) },
            testTag = "import-amount-${row.transactionFingerprint}",
            enabled = !row.duplicate
        )
        OutlinedTextField(
            value = row.currency,
            onValueChange = { onCurrencyChanged(row.transactionFingerprint, it) },
            enabled = !row.duplicate,
            label = { Text(stringResource(R.string.import_statement_currency)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("import-currency-${row.transactionFingerprint}"),
            singleLine = true
        )
        OutlinedTextField(
            value = row.sourceCategory,
            onValueChange = { onSourceCategoryChanged(row.transactionFingerprint, it) },
            enabled = !row.duplicate,
            label = { Text(stringResource(R.string.import_statement_source_category)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("import-category-${row.transactionFingerprint}"),
            singleLine = true
        )
        if (row.isInvalidForIncludedImport()) {
            Text(
                stringResource(R.string.import_statement_row_invalid_hint),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ImportStatementBottomBar(uiState: ImportStatementUiState, onImportApproved: () -> Unit) {
    SbsStickyActionContainer(
        isLoading = uiState.isImporting,
        statusMessage =
        if (uiState.invalidIncludedCount > 0) {
            stringResource(
                R.string.import_statement_invalid_rows_blocking_import,
                uiState.invalidIncludedCount
            )
        } else {
            null
        }
    ) {
        Button(
            onClick = onImportApproved,
            enabled = uiState.canImport && !uiState.isImporting && !uiState.isLoading,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("import-statement-import-button")
        ) {
            Text(
                if (uiState.isImporting) {
                    stringResource(R.string.import_statement_importing)
                } else {
                    if (uiState.selectedIncomeCount > 0) {
                        stringResource(
                            R.string.import_statement_import_operations,
                            uiState.selectedIncomeCount
                        )
                    } else {
                        stringResource(R.string.import_statement_save_statement)
                    }
                }
            )
        }
    }
}

@Composable
private fun ImportStatementSuccessState(
    success: ImportStatementImportSuccessUiState,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenMonths: () -> Unit
) {
    AppSection(
        title = stringResource(R.string.import_statement_success_title),
        modifier = Modifier.testTag("import-success-state")
    ) {
        Text(
            stringResource(
                R.string.import_statement_success_imported,
                success.importedIncomeCount
            ),
            style = MaterialTheme.typography.titleMedium
        )
        success.detailMessage?.takeIf { it.isNotBlank() }?.let { Text(it) }
        ActionFlowRow {
            Button(
                onClick = {
                    success.targetMonth?.let(onOpenMonth)
                },
                enabled = success.targetMonth != null,
                modifier =
                Modifier
                    .weight(1f)
                    .testTag("import-success-open-month")
            ) {
                Text(stringResource(R.string.import_statement_success_open_month))
            }
            TextButton(
                onClick = onOpenMonths,
                modifier =
                Modifier
                    .weight(1f)
                    .testTag("import-success-open-months")
            ) {
                Text(stringResource(R.string.import_statement_success_open_months))
            }
        }
    }
}

private fun ImportStatementRowUiState.statusLabelRes(): Int = when {
    duplicate -> R.string.import_statement_duplicate
    isInvalidForIncludedImport() -> R.string.import_statement_row_status_blocked
    isTaxPaymentCandidate -> R.string.import_statement_summary_tax_payments
    needsReview() -> R.string.common_needs_review
    finalInclusion == DeclarationInclusion.INCLUDED -> R.string.import_statement_row_status_will_import
    else -> R.string.import_statement_row_status_excluded
}

@Composable
private fun ImportStatementRowUiState.reviewHint(): String = when {
    duplicate -> stringResource(R.string.import_statement_duplicate_hint)
    incomeDate == null -> stringResource(R.string.import_statement_missing_date_hint)
    isTaxPaymentCandidate -> stringResource(R.string.import_statement_tax_payment_hint)
    suggestedInclusion == DeclarationInclusion.INCLUDED ->
        stringResource(R.string.import_statement_taxable_hint)
    suggestedInclusion == DeclarationInclusion.REVIEW_REQUIRED ->
        stringResource(R.string.import_statement_review_hint)
    else -> stringResource(R.string.import_statement_excluded_hint)
}

private fun ImportStatementRowUiState.reviewAmountLabel(): String = listOf(amount, currency)
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .ifBlank {
        paidIn?.toDisplayLabel()
            ?: paidOut?.toDisplayLabel()
            ?: "-"
    }

private fun StatementMoney.toDisplayLabel(): String = listOf(
    amount.stripTrailingZeros().toPlainString(),
    currency.orEmpty()
)
    .filter { it.isNotBlank() }
    .joinToString(" ")
