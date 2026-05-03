@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.workflow

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.workflowStatusLabel
import java.time.YearMonth

@Composable
fun WorkflowStatusRoute(innerPadding: PaddingValues, yearMonth: YearMonth, onBack: () -> Unit) {
    val viewModel: WorkflowStatusViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(yearMonth) {
        viewModel.initialize(yearMonth)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is WorkflowStatusEffect.Saved) {
                onBack()
            }
        }
    }

    WorkflowStatusScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onBack = onBack,
        onStatusChanged = viewModel::updateStatus,
        onZeroDeclarationPreparedChanged = viewModel::updateZeroDeclarationPrepared,
        onDeclarationFiledDateChanged = viewModel::updateDeclarationFiledDate,
        onClearDeclarationFiledDate = viewModel::clearDeclarationFiledDate,
        onPaymentSentDateChanged = viewModel::updatePaymentSentDate,
        onClearPaymentSentDate = viewModel::clearPaymentSentDate,
        onPaymentCreditedDateChanged = viewModel::updatePaymentCreditedDate,
        onClearPaymentCreditedDate = viewModel::clearPaymentCreditedDate,
        onPaymentAmountChanged = viewModel::updatePaymentAmount,
        onNotesChanged = viewModel::updateNotes,
        onSave = viewModel::save
    )
}

@Composable
fun WorkflowStatusScreen(
    innerPadding: PaddingValues,
    uiState: WorkflowStatusUiState,
    onBack: () -> Unit,
    onStatusChanged: (MonthlyWorkflowStatus) -> Unit,
    onZeroDeclarationPreparedChanged: (Boolean) -> Unit,
    onDeclarationFiledDateChanged: (java.time.LocalDate) -> Unit,
    onClearDeclarationFiledDate: () -> Unit,
    onPaymentSentDateChanged: (java.time.LocalDate) -> Unit,
    onClearPaymentSentDate: () -> Unit,
    onPaymentCreditedDateChanged: (java.time.LocalDate) -> Unit,
    onClearPaymentCreditedDate: () -> Unit,
    onPaymentAmountChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title =
                uiState.yearMonth?.toString() ?: stringResource(R.string.workflow_status_title),
                onBack = onBack
            )
        }
    ) { contentPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppSection(title = stringResource(R.string.workflow_section_due_state)) {
                KeyValueRow(
                    stringResource(R.string.workflow_derived_status),
                    uiState.derivedStatus?.let { workflowStatusLabel(it) }
                        ?: stringResource(R.string.fx_override_unknown)
                )
                KeyValueRow(
                    stringResource(R.string.workflow_due_date),
                    uiState.dueDate?.formatIsoDate() ?: stringResource(
                        R.string.fx_override_unknown
                    )
                )
                if (uiState.derivedStatus == MonthlyWorkflowStatus.OVERDUE) {
                    Text(
                        stringResource(R.string.workflow_due_state_overdue_hint),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            AppSection(title = stringResource(R.string.workflow_section_status)) {
                FlowRow {
                    uiState.editableStatuses.forEach { status ->
                        FilterChip(
                            selected = uiState.baseStatus == status,
                            onClick = { onStatusChanged(status) },
                            label = { Text(workflowStatusLabel(status)) }
                        )
                    }
                }
            }
            AppSection(title = stringResource(R.string.workflow_section_dates_notes)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.workflow_zero_prepared))
                    Switch(
                        checked = uiState.zeroDeclarationPrepared,
                        onCheckedChange = onZeroDeclarationPreparedChanged
                    )
                }
                HorizontalDivider()
                DatePickerField(
                    label = stringResource(R.string.workflow_declaration_filed_date),
                    value = uiState.declarationFiledDate,
                    onValueChange = onDeclarationFiledDateChanged,
                    placeholderText = stringResource(R.string.common_select_date)
                )
                if (uiState.declarationFiledDate != null) {
                    TextButton(onClick = onClearDeclarationFiledDate) {
                        Text(stringResource(R.string.workflow_clear_declaration_filed_date))
                    }
                }
                DatePickerField(
                    label = stringResource(R.string.workflow_payment_sent_date),
                    value = uiState.paymentSentDate,
                    onValueChange = onPaymentSentDateChanged,
                    placeholderText = stringResource(R.string.common_select_date)
                )
                if (uiState.paymentSentDate != null) {
                    TextButton(onClick = onClearPaymentSentDate) {
                        Text(stringResource(R.string.workflow_clear_payment_sent_date))
                    }
                }
                DatePickerField(
                    label = stringResource(R.string.workflow_payment_credited_date),
                    value = uiState.paymentCreditedDate,
                    onValueChange = onPaymentCreditedDateChanged,
                    placeholderText = stringResource(R.string.common_select_date)
                )
                if (uiState.paymentCreditedDate != null) {
                    TextButton(onClick = onClearPaymentCreditedDate) {
                        Text(stringResource(R.string.workflow_clear_payment_credited_date))
                    }
                }
                DecimalField(
                    label = stringResource(R.string.workflow_payment_amount),
                    value = uiState.paymentAmount,
                    onValueChange = onPaymentAmountChanged
                )
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChanged,
                    label = { Text(stringResource(R.string.workflow_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onSave, enabled = !uiState.isSaving) {
                    Text(
                        stringResource(
                            if (uiState.isSaving) {
                                R.string.workflow_saving
                            } else {
                                R.string.workflow_save
                            }
                        )
                    )
                }
            }
        }
    }
}
