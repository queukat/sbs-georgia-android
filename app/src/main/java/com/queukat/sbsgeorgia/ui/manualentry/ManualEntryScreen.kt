@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.manualentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.sourceCategoryLabel
import java.time.LocalDate

@Composable
fun ManualEntryRoute(
    innerPadding: PaddingValues,
    entryId: Long?,
    onBack: () -> Unit,
) {
    val viewModel: ManualEntryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(entryId) {
        viewModel.initialize(entryId)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is ManualEntryEffect.Saved) {
                onBack()
            }
        }
    }

    ManualEntryScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onBack = onBack,
        onDateChanged = viewModel::updateDate,
        onAmountChanged = viewModel::updateAmount,
        onCurrencyChanged = viewModel::updateCurrency,
        onCategoryChanged = viewModel::updateCategory,
        onNoteChanged = viewModel::updateNote,
        onIncludedChanged = viewModel::updateIncluded,
        onSave = viewModel::save,
    )
}

@Composable
fun ManualEntryScreen(
    innerPadding: PaddingValues,
    uiState: ManualEntryUiState,
    onBack: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCurrencyChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onIncludedChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = stringResource(
                    if (uiState.entryId == null) {
                        R.string.manual_entry_title_new
                    } else {
                        R.string.manual_entry_title_edit
                    },
                ),
                onBack = onBack,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppSection(title = stringResource(R.string.manual_entry_section_entry)) {
                DatePickerField(
                    label = stringResource(R.string.manual_entry_income_date),
                    value = uiState.incomeDate,
                    onValueChange = onDateChanged,
                    testTag = "manual-entry-date-field",
                )
                DecimalField(
                    label = stringResource(R.string.manual_entry_amount),
                    value = uiState.amount,
                    onValueChange = onAmountChanged,
                    testTag = "manual-entry-amount-field",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GEL", "USD", "EUR").forEach { currency ->
                        FilterChip(
                            selected = uiState.currency == currency,
                            onClick = { onCurrencyChanged(currency) },
                            label = { Text(currency) },
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.sourceCategory,
                    onValueChange = onCategoryChanged,
                    label = { Text(stringResource(R.string.manual_entry_source_category)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual-entry-category-field"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceCategoryPresets.manualSuggestions.forEach { suggestion ->
                        val suggestionLabel = sourceCategoryLabel(suggestion)
                        FilterChip(
                            selected = uiState.sourceCategory == suggestion || uiState.sourceCategory == suggestionLabel,
                            onClick = { onCategoryChanged(suggestionLabel) },
                            label = { Text(suggestionLabel) },
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onNoteChanged,
                    label = { Text(stringResource(R.string.manual_entry_note)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual-entry-note-field"),
                    minLines = 3,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.manual_entry_include_graph_20))
                    Switch(
                        checked = uiState.declarationIncluded,
                        onCheckedChange = onIncludedChanged,
                    )
                }
                if (!uiState.currency.equals("GEL", ignoreCase = true)) {
                    Text(stringResource(R.string.manual_entry_unresolved_fx_hint))
                }
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.testTag("manual-entry-save-button"),
                ) {
                    Text(
                        stringResource(
                            if (uiState.entryId == null) {
                                R.string.manual_entry_save
                            } else {
                                R.string.manual_entry_save_changes
                            },
                        ),
                    )
                }
            }
        }
    }
}
