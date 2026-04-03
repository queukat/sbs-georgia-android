@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.fxoverride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.formatIsoDate

@Composable
fun FxOverrideRoute(
    innerPadding: PaddingValues,
    entryId: Long,
    onBack: () -> Unit,
) {
    val viewModel: FxOverrideViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(entryId) {
        viewModel.initialize(entryId)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is FxOverrideEffect.Saved) {
                onBack()
            }
        }
    }

    FxOverrideScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onBack = onBack,
        onUnitsChanged = viewModel::updateUnits,
        onRateToGelChanged = viewModel::updateRateToGel,
        onSave = viewModel::save,
    )
}

@Composable
fun FxOverrideScreen(
    innerPadding: PaddingValues,
    uiState: FxOverrideUiState,
    onBack: () -> Unit,
    onUnitsChanged: (String) -> Unit,
    onRateToGelChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = stringResource(R.string.fx_override_title),
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
            AppSection(title = stringResource(R.string.fx_override_section_entry)) {
                KeyValueRow(
                    stringResource(R.string.fx_override_date),
                    uiState.incomeDate?.formatIsoDate() ?: stringResource(R.string.fx_override_unknown),
                )
                KeyValueRow(
                    stringResource(R.string.fx_override_original_amount),
                    "${uiState.originalAmount} ${uiState.originalCurrency}",
                )
            }
            AppSection(title = stringResource(R.string.fx_override_section_override)) {
                OutlinedTextField(
                    value = uiState.units,
                    onValueChange = onUnitsChanged,
                    label = { Text(stringResource(R.string.fx_override_units)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                DecimalField(
                    label = stringResource(R.string.fx_override_rate_to_gel),
                    value = uiState.rateToGel,
                    onValueChange = onRateToGelChanged,
                )
                uiState.previewGelEquivalent?.let {
                    KeyValueRow(stringResource(R.string.fx_override_preview_gel_equivalent), it)
                }
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onSave, enabled = !uiState.isSaving) {
                    Text(
                        stringResource(
                            if (uiState.isSaving) {
                                R.string.fx_override_saving
                            } else {
                                R.string.fx_override_save
                            },
                        ),
                    )
                }
            }
        }
    }
}
