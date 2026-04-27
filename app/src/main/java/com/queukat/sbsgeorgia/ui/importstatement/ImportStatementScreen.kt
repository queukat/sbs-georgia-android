@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.importstatement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar

@Composable
fun ImportStatementRoute(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val viewModel: ImportStatementViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
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
        onImportApproved = viewModel::importApprovedRows,
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
    onImportApproved: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = stringResource(R.string.import_statement_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onPickPdf) {
                        Text(
                            stringResource(
                                if (uiState.isLoading) {
                                    R.string.import_statement_parsing
                                } else {
                                    R.string.import_statement_pick_pdf
                                },
                            ),
                        )
                    }
                    if (uiState.rows.isNotEmpty()) {
                        TextButton(
                            onClick = onImportApproved,
                            enabled = uiState.canImport && !uiState.isImporting && !uiState.isLoading,
                            modifier = Modifier.testTag("import-statement-import-button"),
                        ) {
                            Text(
                                stringResource(
                                    if (uiState.isImporting) {
                                        R.string.import_statement_importing
                                    } else {
                                        R.string.import_statement_import
                                    },
                                ),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppSection(title = stringResource(R.string.import_statement_section_flow)) {
                    Text(stringResource(R.string.import_statement_flow_body))
                    if (uiState.isLoading || uiState.isImporting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    uiState.sourceFileName?.let {
                        Text(
                            stringResource(R.string.import_statement_file_selected, it),
                            modifier = Modifier.testTag("import-selected-file"),
                        )
                    }
                    uiState.infoMessage?.let { Text(it) }
                    uiState.errorMessage?.let { Text(it) }
                    if (uiState.rows.isEmpty()) {
                        Text(stringResource(R.string.import_statement_pick_hint))
                    } else {
                        Text(stringResource(R.string.import_statement_rows_recognized, uiState.rows.size, uiState.selectedIncomeCount))
                        Text(
                            stringResource(
                                R.string.import_statement_tax_payment_rows_recognized,
                                uiState.detectedTaxPaymentCount,
                                uiState.recognizedOutgoingCount,
                            ),
                        )
                        if (uiState.invalidIncludedCount > 0) {
                            Text(
                                stringResource(
                                    R.string.import_statement_invalid_rows_blocking_import,
                                    uiState.invalidIncludedCount,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            items(uiState.rows, key = { it.transactionFingerprint }) { row ->
                AppSection(title = row.description) {
                    row.additionalInformation?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    row.paidInLabel?.let { Text(stringResource(R.string.import_statement_paid_in, it)) }
                    row.paidOutLabel?.let { Text(stringResource(R.string.import_statement_paid_out, it)) }
                    row.balanceLabel?.let { Text(stringResource(R.string.import_statement_balance, it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = row.finalInclusion == DeclarationInclusion.INCLUDED,
                            onClick = { onIncludeAsTaxableChanged(row.transactionFingerprint, true) },
                            enabled = !row.duplicate,
                            modifier = Modifier.testTag("import-taxable-${row.transactionFingerprint}"),
                            label = { Text(stringResource(R.string.import_statement_taxable_income)) },
                        )
                        FilterChip(
                            selected = row.finalInclusion == DeclarationInclusion.EXCLUDED,
                            onClick = { onIncludeAsTaxableChanged(row.transactionFingerprint, false) },
                            enabled = !row.duplicate,
                            modifier = Modifier.testTag("import-exclude-${row.transactionFingerprint}"),
                            label = {
                                Text(
                                    stringResource(
                                        if (row.duplicate) {
                                            R.string.import_statement_duplicate
                                        } else {
                                            R.string.import_statement_exclude
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    Text(
                        when {
                            row.duplicate -> stringResource(R.string.import_statement_duplicate_hint)
                            row.incomeDate == null -> stringResource(R.string.import_statement_missing_date_hint)
                            row.isTaxPaymentCandidate -> stringResource(R.string.import_statement_tax_payment_hint)
                            row.suggestedInclusion == DeclarationInclusion.INCLUDED -> stringResource(R.string.import_statement_taxable_hint)
                            row.suggestedInclusion == DeclarationInclusion.REVIEW_REQUIRED -> stringResource(R.string.import_statement_review_hint)
                            else -> stringResource(R.string.import_statement_excluded_hint)
                        },
                    )
                    DatePickerField(
                        label = stringResource(R.string.import_statement_income_date),
                        value = row.incomeDate,
                        onValueChange = { onDateChanged(row.transactionFingerprint, it) },
                        placeholderText = stringResource(R.string.common_select_date),
                        enabled = !row.duplicate,
                    )
                    DecimalField(
                        label = stringResource(R.string.import_statement_amount),
                        value = row.amount,
                        onValueChange = { onAmountChanged(row.transactionFingerprint, it) },
                        testTag = "import-amount-${row.transactionFingerprint}",
                        enabled = !row.duplicate,
                    )
                    OutlinedTextField(
                        value = row.currency,
                        onValueChange = { onCurrencyChanged(row.transactionFingerprint, it) },
                        enabled = !row.duplicate,
                        label = { Text(stringResource(R.string.import_statement_currency)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import-currency-${row.transactionFingerprint}"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = row.sourceCategory,
                        onValueChange = { onSourceCategoryChanged(row.transactionFingerprint, it) },
                        enabled = !row.duplicate,
                        label = { Text(stringResource(R.string.import_statement_source_category)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import-category-${row.transactionFingerprint}"),
                        singleLine = true,
                    )
                    if (row.isInvalidForIncludedImport()) {
                        Text(
                            stringResource(R.string.import_statement_row_invalid_hint),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
