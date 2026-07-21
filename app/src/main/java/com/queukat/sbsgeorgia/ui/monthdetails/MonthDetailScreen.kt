@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.monthdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.ui.common.ActionFlowRow
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DeclarationCopyValues
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsScreenScaffold
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.copyPlainTextToClipboard
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import com.queukat.sbsgeorgia.ui.common.fxRateSourceLabel
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
    onOpenFxOverride: (Long) -> Unit,
    onOpenWorkflowStatus: (YearMonth) -> Unit,
    onOpenPaymentHelper: (YearMonth) -> Unit
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
        onOpenFxOverride = onOpenFxOverride,
        onOpenWorkflowStatus = onOpenWorkflowStatus,
        onOpenPaymentHelper = onOpenPaymentHelper,
        onDeleteEntry = viewModel::deleteEntry,
        onResolveOfficialRates = viewModel::resolveOfficialRates,
        onToggleZeroPrepared = viewModel::toggleZeroPrepared
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
    onOpenFxOverride: (Long) -> Unit,
    onOpenWorkflowStatus: (YearMonth) -> Unit,
    onOpenPaymentHelper: (YearMonth) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onResolveOfficialRates: () -> Unit,
    onToggleZeroPrepared: () -> Unit
) {
    val snapshot = uiState.snapshot
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val copyBundle = uiState.copyBundle
    val copyToolsRequester = remember { BringIntoViewRequester() }
    val entriesRequester = remember { BringIntoViewRequester() }
    val copiedTemplate = stringResource(R.string.common_copied_template, "%1\$s")
    val paymentTextLabel = stringResource(R.string.month_detail_copy_payment_text)
    val fullTextLabel = stringResource(R.string.month_detail_copy_all_text)
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val copyActionsAvailable =
        snapshot != null && copyBundle != null && uiState.actionState?.canCopyDeclarationValues == true
    val hasDeclarationValues = copyBundle?.declarationValues?.isNotEmpty() == true
    val canCopyDeclarationValues = copyActionsAvailable && hasDeclarationValues
    val activeMonth = uiState.yearMonth ?: snapshot?.period?.incomeMonth

    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        context.copyPlainTextToClipboard(label, value)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(copiedTemplate.replace("%1\$s", label))
        }
    }

    fun scrollToSection(requester: BringIntoViewRequester) {
        coroutineScope.launch {
            requester.bringIntoView()
        }
    }

    SbsScreenScaffold(
        innerPadding = innerPadding,
        title =
        snapshot?.period?.incomeMonth?.formatMonthYear()
            ?: stringResource(R.string.month_detail_title_fallback),
        onBack = onBack,
        topActions = {
            TextButton(onClick = onAddIncome) {
                Text(stringResource(R.string.month_detail_add_income))
            }
        },
        snackbarHostState = snackbarHostState
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
                        }
                    ) {
                        Text(stringResource(R.string.month_detail_delete_confirm_action))
                    }
                }
            )
        }
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (snapshot == null) {
                    Text(stringResource(R.string.month_detail_unavailable))
                } else {
                    MonthNextActionSection(
                        snapshot = snapshot,
                        isFilingWindowOpen = uiState.isFilingWindowOpen,
                        isResolvingFx = uiState.isResolvingFx,
                        canCopyDeclarationValues = canCopyDeclarationValues,
                        month = activeMonth,
                        onResolveOfficialRates = onResolveOfficialRates,
                        onReviewEntries = { scrollToSection(entriesRequester) },
                        onCopyValues = { scrollToSection(copyToolsRequester) },
                        onOpenPaymentHelper = onOpenPaymentHelper,
                        onOpenWorkflowStatus = onOpenWorkflowStatus
                    )
                }
            }
            item {
                if (snapshot != null) {
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
                                        }
                                    )
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
                                        snapshot.period.filingWindow.start
                                            .formatIsoDate()
                                    )
                                )
                            }
                            snapshot.reviewNeeded && snapshot.unresolvedFxCount == 0 -> {
                                Text(stringResource(R.string.month_detail_review_needed))
                            }
                            snapshot.unresolvedFxCount > 0 -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_unresolved_fx,
                                        snapshot.unresolvedFxCount
                                    )
                                )
                            }
                            snapshot.zeroDeclarationSuggested -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_zero_guidance,
                                        snapshot.period.filingWindow.dueDate
                                            .formatIsoDate()
                                    )
                                )
                            }
                            else -> {
                                Text(
                                    stringResource(
                                        R.string.month_detail_ready,
                                        snapshot.period.filingWindow.dueDate
                                            .formatIsoDate()
                                    )
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
                if (snapshot != null && copyBundle != null && copyActionsAvailable) {
                    val canCopyPaymentText = uiState.actionState?.canCopyPaymentText == true

                    AppSection(
                        title =
                        stringResource(
                            if (hasDeclarationValues) {
                                R.string.month_detail_section_copy_tools
                            } else {
                                R.string.month_detail_section_payment_tools
                            }
                        ),
                        modifier = Modifier.bringIntoViewRequester(copyToolsRequester)
                    ) {
                        DeclarationCopyValues(
                            values = copyBundle.declarationValues,
                            enabled = canCopyDeclarationValues,
                            testTagPrefix = "month-detail",
                            onCopy = { label, value -> copy(label = label, value = value) }
                        )
                        KeyValueRow(
                            stringResource(R.string.snapshot_estimated_tax),
                            copyBundle.taxAmount
                        )
                        KeyValueRow(
                            stringResource(R.string.payment_helper_treasury_code),
                            copyBundle.treasuryCode
                        )
                        KeyValueRow(
                            stringResource(R.string.payment_helper_comment),
                            if (copyBundle.paymentComment.isBlank()) {
                                stringResource(R.string.payment_helper_complete_settings_first)
                            } else {
                                copyBundle.paymentComment
                            }
                        )

                        ActionFlowRow {
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = paymentTextLabel,
                                        value = copyBundle.paymentText
                                    )
                                },
                                enabled = canCopyPaymentText
                            ) {
                                Text(stringResource(R.string.month_detail_copy_payment_text))
                            }
                            OutlinedButton(
                                onClick = {
                                    copy(
                                        label = fullTextLabel,
                                        value = copyBundle.fullText
                                    )
                                },
                                enabled = canCopyDeclarationValues
                            ) {
                                Text(stringResource(R.string.month_detail_copy_all_text))
                            }
                        }
                    }
                }
            }
            item {
                AppSection(
                    title = stringResource(R.string.month_detail_section_entries),
                    modifier = Modifier.bringIntoViewRequester(entriesRequester)
                ) {
                    if (uiState.entries.isEmpty()) {
                        Text(stringResource(R.string.month_detail_no_entries))
                    }
                }
            }
            items(uiState.entries) { entry ->
                AppSection(title = formatAmount(entry.originalAmount, entry.originalCurrency)) {
                    KeyValueRow(
                        stringResource(R.string.month_detail_date),
                        entry.incomeDate.formatIsoDate()
                    )
                    KeyValueRow(
                        stringResource(R.string.month_detail_category),
                        sourceCategoryLabel(entry.sourceCategory)
                    )
                    KeyValueRow(
                        stringResource(R.string.month_detail_included),
                        stringResource(
                            if (entry.declarationInclusion == DeclarationInclusion.INCLUDED) {
                                R.string.common_yes
                            } else {
                                R.string.common_no
                            }
                        )
                    )
                    entry.gelEquivalent?.let { gelEquivalent ->
                        KeyValueRow(
                            stringResource(R.string.month_detail_gel_equivalent),
                            formatAmount(gelEquivalent, "GEL")
                        )
                        KeyValueRow(
                            stringResource(R.string.month_detail_fx_source),
                            fxRateSourceLabel(entry.rateSource)
                        )
                        uiState.fxRateDetails[entry.id]?.let { rate ->
                            FxRateDetails(entry = entry, rate = rate)
                        }
                    }
                    if (entry.requiresFxResolution()) {
                        Text(
                            stringResource(R.string.month_detail_unresolved_fx_hint),
                            modifier = Modifier.testTag("month-detail-unresolved-fx-message")
                        )
                        TextButton(onClick = { onOpenFxOverride(entry.id) }) {
                            Text(stringResource(R.string.month_detail_manual_fx_override))
                        }
                    }
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ActionFlowRow {
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

@Composable
private fun MonthNextActionSection(
    snapshot: MonthlyDeclarationSnapshot,
    isFilingWindowOpen: Boolean,
    isResolvingFx: Boolean,
    canCopyDeclarationValues: Boolean,
    month: YearMonth?,
    onResolveOfficialRates: () -> Unit,
    onReviewEntries: () -> Unit,
    onCopyValues: () -> Unit,
    onOpenPaymentHelper: (YearMonth) -> Unit,
    onOpenWorkflowStatus: (YearMonth) -> Unit
) {
    val baseStatus = snapshot.record?.workflowStatus ?: snapshot.workflowStatus

    AppSection(title = stringResource(R.string.month_detail_section_next_action)) {
        when {
            snapshot.period.outOfScope -> {
                Text(stringResource(R.string.month_detail_next_out_of_scope_hint))
            }
            snapshot.unresolvedFxCount > 0 -> {
                Text(
                    stringResource(
                        R.string.month_detail_next_resolve_fx_hint,
                        snapshot.unresolvedFxCount
                    )
                )
                Button(
                    onClick = onResolveOfficialRates,
                    enabled = !isResolvingFx,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("month-detail-next-resolve-fx-button")
                ) {
                    Text(
                        stringResource(
                            if (isResolvingFx) {
                                R.string.month_detail_resolving_fx
                            } else {
                                R.string.month_detail_next_resolve_fx
                            }
                        )
                    )
                }
            }
            snapshot.reviewNeeded -> {
                Text(stringResource(R.string.month_detail_next_review_entries_hint))
                Button(
                    onClick = onReviewEntries,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("month-detail-next-review-entries-button")
                ) {
                    Text(stringResource(R.string.month_detail_next_review_entries))
                }
            }
            baseStatus == MonthlyWorkflowStatus.PAYMENT_SENT && month != null -> {
                Text(stringResource(R.string.month_detail_next_mark_payment_credited_hint))
                Button(
                    onClick = { onOpenWorkflowStatus(month) },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("month-detail-next-mark-payment-credited-button")
                ) {
                    Text(stringResource(R.string.month_detail_next_mark_payment_credited))
                }
            }
            baseStatus in paymentPreparationStatuses && month != null -> {
                Text(stringResource(R.string.month_detail_next_prepare_payment_hint))
                Button(
                    onClick = { onOpenPaymentHelper(month) },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("month-detail-next-prepare-payment-button")
                ) {
                    Text(stringResource(R.string.month_detail_next_prepare_payment))
                }
            }
            canCopyDeclarationValues -> {
                Text(stringResource(R.string.month_detail_next_copy_values_hint))
                Button(
                    onClick = onCopyValues,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("month-detail-next-copy-values-button")
                ) {
                    Text(stringResource(R.string.month_detail_next_copy_values))
                }
            }
            !isFilingWindowOpen -> {
                Text(
                    stringResource(
                        R.string.month_detail_next_wait_for_window_hint,
                        snapshot.period.filingWindow.start.formatIsoDate()
                    )
                )
            }
            baseStatus in completedPaymentStatuses -> {
                Text(stringResource(R.string.month_detail_next_done_hint))
            }
            else -> {
                Text(stringResource(R.string.month_detail_next_prepare_details_hint))
            }
        }
    }
}

private val paymentPreparationStatuses =
    setOf(
        MonthlyWorkflowStatus.FILED,
        MonthlyWorkflowStatus.TAX_PAYMENT_PENDING
    )

private val completedPaymentStatuses =
    setOf(
        MonthlyWorkflowStatus.PAYMENT_CREDITED,
        MonthlyWorkflowStatus.SETTLED
    )

@Composable
private fun FxRateDetails(entry: IncomeEntry, rate: FxRate) {
    KeyValueRow(
        stringResource(R.string.month_detail_fx_rate_date),
        rate.rateDate.formatIsoDate()
    )
    KeyValueRow(
        stringResource(R.string.month_detail_fx_rate),
        stringResource(
            R.string.month_detail_fx_rate_value,
            rate.units,
            rate.currencyCode,
            rate.rateToGel.stripTrailingZeros().toPlainString()
        )
    )
    entry.gelEquivalent?.let { gelEquivalent ->
        Text(
            text =
            stringResource(
                R.string.month_detail_fx_formula,
                entry.originalAmount.stripTrailingZeros().toPlainString(),
                entry.originalCurrency,
                rate.rateToGel.stripTrailingZeros().toPlainString(),
                rate.units,
                formatAmount(gelEquivalent, "GEL")
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
