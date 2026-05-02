@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.monthdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.copyPlainTextToClipboard
import com.queukat.sbsgeorgia.ui.common.fxRateSourceLabel
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import com.queukat.sbsgeorgia.ui.common.sourceCategoryLabel
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun MonthDetailRoute(
    innerPadding: PaddingValues,
    yearMonth: YearMonth,
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onOpenPaymentHelper: (YearMonth) -> Unit,
    onOpenFxOverride: (Long) -> Unit,
    onOpenWorkflowStatus: (YearMonth) -> Unit,
) {
    val viewModel: MonthDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(yearMonth) {
        viewModel.initialize(yearMonth)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is MonthDetailEffect.Message) {
                snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    MonthDetailScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddIncome = onAddIncome,
        onEditEntry = onEditEntry,
        onOpenPaymentHelper = onOpenPaymentHelper,
        onOpenFxOverride = onOpenFxOverride,
        onOpenWorkflowStatus = onOpenWorkflowStatus,
        onDeleteEntry = viewModel::deleteEntry,
        onResolveOfficialRates = viewModel::resolveOfficialRates,
        onToggleZeroPrepared = viewModel::toggleZeroPrepared,
    )
}

@Composable
fun MonthDetailScreen(
    innerPadding: PaddingValues,
    uiState: MonthDetailUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onOpenPaymentHelper: (YearMonth) -> Unit,
    onOpenFxOverride: (Long) -> Unit,
    onOpenWorkflowStatus: (YearMonth) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onResolveOfficialRates: () -> Unit,
    onToggleZeroPrepared: () -> Unit,
) {
    val snapshot = uiState.snapshot
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val copyBundle = uiState.copyBundle
    val copiedTemplate = stringResource(R.string.common_copied_template, "%1\$s")
    val graph20Label = stringResource(R.string.snapshot_graph_20)
    val graph15Label = stringResource(R.string.snapshot_graph_15_cumulative)
    val paymentTextLabel = stringResource(R.string.month_detail_copy_payment_text)
    val fullTextLabel = stringResource(R.string.month_detail_copy_all_text)
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        context.copyPlainTextToClipboard(label, value)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(copiedTemplate.replace("%1\$s", label))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = snapshot?.period?.incomeMonth?.formatMonthYear()
                    ?: stringResource(R.string.month_detail_title_fallback),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onAddIncome) {
                        Text(stringResource(R.string.month_detail_add_income))
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { contentPadding ->
        pendingDeleteEntryId?.let { entryId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteEntryId = null },
                title = { Text(stringResource(R.string.month_detail_delete_confirm_title)) },
                text = { Text(stringResource(R.string.month_detail_delete_confirm_body)) },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteEntryId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteEntryId = null
                            onDeleteEntry(entryId)
                        },
                    ) {
                        Text(stringResource(R.string.month_detail_delete_confirm_action))
                    }
                },
            )
        }
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
                if (snapshot == null) {
                    Text(stringResource(R.string.month_detail_unavailable))
                } else {
                    AppSection(title = stringResource(R.string.month_detail_section_summary)) {
                        SnapshotSummary(snapshot = snapshot)
                        if (snapshot.zeroDeclarationSuggested || snapshot.zeroDeclarationPrepared) {
                            OutlinedButton(onClick = onToggleZeroPrepared) {
                                Text(
                                    stringResource(
                                        if (snapshot.zeroDeclarationPrepared) {
                                            R.string.month_detail_zero_prepared
                                        } else {
                                            R.string.month_detail_zero_not_prepared
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (snapshot != null) {
                    AppSection(title = stringResource(R.string.month_detail_section_readiness)) {
                        when {
                            snapshot.period.outOfScope -> {
                                Text(stringResource(R.string.month_detail_out_of_scope))
                            }
                            !uiState.isFilingWindowOpen -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_filing_opens_on,
                                        snapshot.period.filingWindow.start.formatIsoDate(),
                                    ),
                                )
                            }
                            snapshot.reviewNeeded && snapshot.unresolvedFxCount == 0 -> {
                                Text(stringResource(R.string.month_detail_review_needed))
                            }
                            snapshot.unresolvedFxCount > 0 -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_unresolved_fx,
                                        snapshot.unresolvedFxCount,
                                    ),
                                )
                                OutlinedButton(
                                    onClick = onResolveOfficialRates,
                                    enabled = !uiState.isResolvingFx,
                                ) {
                                    Text(
                                        stringResource(
                                            if (uiState.isResolvingFx) {
                                                R.string.month_detail_resolving_fx
                                            } else {
                                                R.string.month_detail_resolve_fx
                                            },
                                        ),
                                    )
                                }
                            }
                            snapshot.zeroDeclarationSuggested -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_zero_guidance,
                                        snapshot.period.filingWindow.dueDate.formatIsoDate(),
                                    ),
                                )
                            }
                            else -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_ready,
                                        snapshot.period.filingWindow.dueDate.formatIsoDate(),
                                    ),
                                )
                            }
                        }
                        uiState.yearMonth?.let { month ->
                            OutlinedButton(onClick = { onOpenWorkflowStatus(month) }) {
                                Text(stringResource(R.string.month_detail_edit_status))
                            }
                        }
                    }
                }
            }
            item {
                if (snapshot != null && copyBundle != null && !snapshot.period.outOfScope) {
                    val canCopyDeclarationValues = uiState.isFilingWindowOpen &&
                        snapshot.unresolvedFxCount == 0 &&
                        !snapshot.reviewNeeded
                    val canCopyPaymentText = canCopyDeclarationValues && copyBundle.paymentComment.isNotBlank()

                    AppSection(title = stringResource(R.string.month_detail_section_copy_tools)) {
                        when {
                            !uiState.isFilingWindowOpen -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_copy_waiting_for_window,
                                        snapshot.period.filingWindow.start.formatIsoDate(),
                                    ),
                                )
                            }
                            snapshot.unresolvedFxCount > 0 -> {
                                Text(stringResource(R.string.month_detail_copy_blocked_unresolved_fx))
                            }
                            snapshot.reviewNeeded -> {
                                Text(stringResource(R.string.month_detail_copy_blocked_review))
                            }
                        }

                        KeyValueRow(graph20Label, copyBundle.graph20)
                        KeyValueRow(graph15Label, copyBundle.graph15)
                        KeyValueRow(stringResource(R.string.snapshot_estimated_tax), copyBundle.taxAmount)
                        KeyValueRow(stringResource(R.string.payment_helper_treasury_code), copyBundle.treasuryCode)
                        KeyValueRow(
                            stringResource(R.string.payment_helper_comment),
                            if (copyBundle.paymentComment.isBlank()) {
                                stringResource(R.string.payment_helper_complete_settings_first)
                            } else {
                                copyBundle.paymentComment
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = graph20Label,
                                        value = copyBundle.graph20,
                                    )
                                },
                                enabled = canCopyDeclarationValues,
                            ) {
                                Text(stringResource(R.string.month_detail_copy_graph_20))
                            }
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = graph15Label,
                                        value = copyBundle.graph15,
                                    )
                                },
                                enabled = canCopyDeclarationValues,
                            ) {
                                Text(stringResource(R.string.month_detail_copy_graph_15))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = paymentTextLabel,
                                        value = copyBundle.paymentText,
                                    )
                                },
                                enabled = canCopyPaymentText,
                            ) {
                                Text(stringResource(R.string.month_detail_copy_payment_text))
                            }
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = fullTextLabel,
                                        value = copyBundle.fullText,
                                    )
                                },
                                enabled = canCopyDeclarationValues,
                            ) {
                                Text(stringResource(R.string.month_detail_copy_all_text))
                            }
                        }
                    }
                }
            }
            item {
                AppSection(title = stringResource(R.string.month_detail_section_entries)) {
                    if (uiState.entries.isEmpty()) {
                        Text(stringResource(R.string.month_detail_no_entries))
                    }
                }
            }
            items(uiState.entries) { entry ->
                AppSection(title = formatAmount(entry.originalAmount, entry.originalCurrency)) {
                    KeyValueRow(stringResource(R.string.month_detail_date), entry.incomeDate.formatIsoDate())
                    KeyValueRow(stringResource(R.string.month_detail_category), sourceCategoryLabel(entry.sourceCategory))
                    KeyValueRow(
                        stringResource(R.string.month_detail_included),
                        stringResource(
                            if (entry.declarationInclusion == DeclarationInclusion.INCLUDED) {
                                R.string.common_yes
                            } else {
                                R.string.common_no
                            },
                        ),
                    )
                    entry.gelEquivalent?.let { gelEquivalent ->
                        KeyValueRow(stringResource(R.string.month_detail_gel_equivalent), formatAmount(gelEquivalent, "GEL"))
                        KeyValueRow(stringResource(R.string.month_detail_fx_source), fxRateSourceLabel(entry.rateSource))
                    }
                    if (entry.requiresFxResolution()) {
                        Text(
                            stringResource(R.string.month_detail_unresolved_fx_hint),
                            modifier = Modifier.testTag("month-detail-unresolved-fx-message"),
                        )
                        TextButton(onClick = { onOpenFxOverride(entry.id) }) {
                            Text(stringResource(R.string.month_detail_manual_fx_override))
                        }
                    }
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { onEditEntry(entry.id) }) {
                            Text(stringResource(R.string.month_detail_edit))
                        }
                        TextButton(onClick = { pendingDeleteEntryId = entry.id }) {
                            Text(stringResource(R.string.month_detail_delete))
                        }
                    }
                }
            }
        }
    }
}
