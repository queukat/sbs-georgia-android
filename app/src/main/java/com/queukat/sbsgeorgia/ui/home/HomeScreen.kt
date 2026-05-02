package com.queukat.sbsgeorgia.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SimpleChip
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.copyPlainTextToClipboard
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import com.queukat.sbsgeorgia.ui.common.sharePlainTextToTelegramOrChooser
import java.time.YearMonth
import kotlinx.coroutines.launch

private const val SUPPORT_DEVELOPER_URL = "https://ko-fi.com/queukat"

@Composable
fun HomeRoute(
    innerPadding: PaddingValues,
    onOpenMonths: () -> Unit,
    onOpenDueMonth: (YearMonth) -> Unit,
    onOpenCharts: () -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onOpenMonths = onOpenMonths,
        onOpenDueMonth = onOpenDueMonth,
        onOpenCharts = onOpenCharts,
        onAddIncome = onAddIncome,
        onImportStatement = onImportStatement,
        onOpenSettings = onOpenSettings,
        onSettleCurrentDuePeriod = viewModel::settleCurrentDuePeriod,
    )
}

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    uiState: HomeUiState,
    onOpenMonths: () -> Unit,
    onOpenDueMonth: (YearMonth) -> Unit,
    onOpenCharts: () -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit,
    onOpenSettings: () -> Unit,
    onSettleCurrentDuePeriod: () -> Unit,
) {
    val summary = uiState.summary
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val copiedTemplate = stringResource(R.string.common_copied_template, "%1\$s")
    val shareTitle = stringResource(R.string.home_share_declaration_values_title)

    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        context.copyPlainTextToClipboard(label, value)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(copiedTemplate.replace("%1\$s", label))
        }
    }

    fun shareToTelegram(value: String) {
        context.sharePlainTextToTelegramOrChooser(
            title = shareTitle,
            value = value,
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                DeveloperSupportButton(
                    onClick = { uriHandler.openUri(SUPPORT_DEVELOPER_URL) },
                )
            }
            Text(
                text = stringResource(R.string.home_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (summary != null) {
                AppSection(title = stringResource(R.string.home_section_due_period)) {
                    val duePeriod = summary.currentDuePeriod
                    if (duePeriod == null) {
                        Text(stringResource(R.string.home_no_due_period))
                    } else {
                        SnapshotSummary(snapshot = duePeriod)
                        DuePeriodQuickAccess(
                            quickAccess = uiState.duePeriodQuickAccess,
                            onOpenDueMonth = onOpenDueMonth,
                            onSettleCurrentDuePeriod = onSettleCurrentDuePeriod,
                            onCopy = ::copy,
                            onShareToTelegram = ::shareToTelegram,
                        )
                    }
                }

                AppSection(title = stringResource(R.string.home_section_dashboard)) {
                    KeyValueRow(
                        stringResource(R.string.home_setup_complete),
                        stringResource(if (summary.setupComplete) R.string.common_yes else R.string.common_no),
                    )
                    KeyValueRow(stringResource(R.string.home_ytd_income), formatAmount(summary.ytdIncomeGel, "GEL"))
                    KeyValueRow(stringResource(R.string.home_unresolved_fx_entries), summary.unresolvedFxCount.toString())
                    KeyValueRow(stringResource(R.string.home_unsettled_months), summary.unsettledMonthsCount.toString())
                    KeyValueRow(stringResource(R.string.home_paid_taxes), formatAmount(summary.paidTaxAmountGel, "GEL"))
                    if (summary.paymentMismatchMonthsCount > 0) {
                        KeyValueRow(
                            stringResource(R.string.home_tax_mismatch_months),
                            summary.paymentMismatchMonthsCount.toString(),
                        )
                    }
                    summary.nextReminderDay?.let {
                        KeyValueRow(
                            stringResource(R.string.home_next_reminder),
                            stringResource(R.string.home_next_reminder_day, it),
                        )
                    }
                }

                if (!summary.setupComplete) {
                    AppSection(title = stringResource(R.string.home_section_setup_required)) {
                        Text(stringResource(R.string.home_setup_required_body))
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open-settings-button"),
                        ) {
                            Text(stringResource(R.string.home_open_settings))
                        }
                    }
                }
            }

            AppSection(title = stringResource(R.string.home_section_quick_actions)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onAddIncome,
                        modifier = Modifier.testTag("add-income-button"),
                    ) {
                        Text(stringResource(R.string.home_add_income))
                    }
                    OutlinedButton(
                        onClick = onOpenMonths,
                        modifier = Modifier.testTag("open-months-button"),
                    ) {
                        Text(stringResource(R.string.home_open_months))
                    }
                    OutlinedButton(onClick = onOpenCharts) {
                        Text(stringResource(R.string.home_open_charts))
                    }
                    OutlinedButton(onClick = onImportStatement) {
                        Text(stringResource(R.string.home_import_pdf))
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.home_open_settings_short))
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(innerPadding),
        )
    }
}

@Composable
private fun DuePeriodQuickAccess(
    quickAccess: HomeDuePeriodQuickAccess?,
    onOpenDueMonth: (YearMonth) -> Unit,
    onSettleCurrentDuePeriod: () -> Unit,
    onCopy: (String, String) -> Unit,
    onShareToTelegram: (String) -> Unit,
) {
    if (quickAccess == null) return

    val snapshot = quickAccess.snapshot
    val copyBundle = quickAccess.copyBundle
    val graph20Label = stringResource(R.string.snapshot_graph_20)
    val graph15Label = stringResource(R.string.snapshot_graph_15_cumulative)
    val paymentTextLabel = stringResource(R.string.month_detail_copy_payment_text)
    val fullTextLabel = stringResource(R.string.month_detail_copy_all_text)

    Text(
        text = stringResource(R.string.home_due_period_quick_access),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = snapshot.period.incomeMonth.formatMonthYear(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (copyBundle != null && !snapshot.period.outOfScope) {
        val canCopyPaymentText = quickAccess.canCopyDeclarationValues && copyBundle.paymentComment.isNotBlank()
        when {
            quickAccess.filingOpensOn != null -> {
                Text(
                    stringResource(
                        R.string.month_detail_copy_waiting_for_window,
                        quickAccess.filingOpensOn.formatIsoDate(),
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

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onCopy(graph20Label, copyBundle.graph20) },
                enabled = quickAccess.canCopyDeclarationValues,
                modifier = Modifier.testTag("home-copy-graph-20-button"),
            ) {
                Text(stringResource(R.string.month_detail_copy_graph_20))
            }
            OutlinedButton(
                onClick = { onCopy(graph15Label, copyBundle.graph15) },
                enabled = quickAccess.canCopyDeclarationValues,
                modifier = Modifier.testTag("home-copy-graph-15-button"),
            ) {
                Text(stringResource(R.string.month_detail_copy_graph_15))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onCopy(paymentTextLabel, copyBundle.paymentText) },
                enabled = canCopyPaymentText,
                modifier = Modifier.testTag("home-copy-payment-text-button"),
            ) {
                Text(stringResource(R.string.month_detail_copy_payment_text))
            }
            OutlinedButton(
                onClick = { onCopy(fullTextLabel, copyBundle.fullText) },
                enabled = quickAccess.canCopyDeclarationValues,
                modifier = Modifier.testTag("home-copy-all-text-button"),
            ) {
                Text(stringResource(R.string.month_detail_copy_all_text))
            }
            OutlinedButton(
                onClick = { onShareToTelegram(copyBundle.fullText) },
                enabled = quickAccess.canCopyDeclarationValues,
                modifier = Modifier.testTag("home-share-telegram-button"),
            ) {
                Text(stringResource(R.string.home_share_to_telegram))
            }
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onOpenDueMonth(snapshot.period.incomeMonth) },
            modifier = Modifier.testTag("home-open-due-month-button"),
        ) {
            Text(stringResource(R.string.home_open_due_month))
        }
        when {
            snapshot.period.outOfScope -> Unit
            quickAccess.monthAlreadySettled -> {
                SimpleChip(stringResource(R.string.months_month_settled))
            }
            quickAccess.canQuickSettleMonth -> {
                OutlinedButton(
                    onClick = onSettleCurrentDuePeriod,
                    modifier = Modifier.testTag("home-close-due-month-button"),
                ) {
                    Text(stringResource(R.string.months_mark_month_settled))
                }
            }
        }
    }
}

@Composable
private fun DeveloperSupportButton(
    onClick: () -> Unit,
) {
    val contentDescription = stringResource(R.string.home_support_developer_content_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
            }
            .testTag("home-support-developer-button"),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DonutMark(size = 24.dp)
        }
    }
}

@Composable
private fun DonutMark(
    size: Dp,
) {
    Icon(
        painter = painterResource(R.drawable.ic_donut_outline),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size),
    )
}
