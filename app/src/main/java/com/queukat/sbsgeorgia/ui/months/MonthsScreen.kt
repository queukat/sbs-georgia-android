@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.months

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.SbsActionStatus
import com.queukat.sbsgeorgia.ui.common.SbsSecondaryButton
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import java.time.YearMonth

@Composable
fun MonthsRoute(
    innerPadding: PaddingValues,
    onMonthClick: (YearMonth) -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit,
    onOpenCharts: () -> Unit
) {
    val viewModel: MonthsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MonthsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onMonthClick = onMonthClick,
        onSettleMonth = viewModel::settleMonth,
        onAddIncome = onAddIncome,
        onImportStatement = onImportStatement,
        onOpenCharts = onOpenCharts
    )
}

@Composable
fun MonthsScreen(
    innerPadding: PaddingValues,
    uiState: MonthsUiState,
    onMonthClick: (YearMonth) -> Unit,
    onSettleMonth: (YearMonth) -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit,
    onOpenCharts: () -> Unit
) {
    var pendingQuickSettleMonth by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = stringResource(R.string.months_title),
                actions = {
                    TextButton(onClick = onImportStatement) {
                        Text(stringResource(R.string.months_import_pdf))
                    }
                    TextButton(onClick = onAddIncome) {
                        Text(stringResource(R.string.months_add_income))
                    }
                }
            )
        }
    ) { contentPadding ->
        pendingQuickSettleMonth?.let { monthText ->
            val yearMonth = YearMonth.parse(monthText)
            AlertDialog(
                onDismissRequest = { pendingQuickSettleMonth = null },
                title = { Text(stringResource(R.string.months_complete_confirm_title)) },
                text = { Text(stringResource(R.string.months_complete_confirm_body)) },
                dismissButton = {
                    TextButton(onClick = { pendingQuickSettleMonth = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingQuickSettleMonth = null
                            onSettleMonth(yearMonth)
                        },
                        modifier = Modifier.testTag("months-confirm-complete-month-button")
                    ) {
                        Text(stringResource(R.string.months_complete_confirm_action))
                    }
                },
                modifier = Modifier.testTag("months-complete-month-confirm-dialog")
            )
        }
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding =
            PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "months-charts-action") {
                SbsSecondaryButton(
                    label = stringResource(R.string.months_open_charts),
                    onClick = onOpenCharts,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("months-open-charts-button"),
                    leadingIcon = Icons.Outlined.BarChart
                )
            }
            if (uiState.sections.isEmpty()) {
                item {
                    Text(stringResource(R.string.months_empty))
                }
            }
            uiState.sections.forEach { section ->
                item(key = "year-${section.year}") {
                    Text(
                        text = section.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(
                    items = section.items,
                    key = { item ->
                        item.snapshot.period.incomeMonth
                            .toString()
                    }
                ) { item ->
                    val snapshot = item.snapshot
                    AppSection(
                        title = snapshot.period.incomeMonth.formatMonthYear(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        SnapshotSummary(snapshot = snapshot)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SbsSecondaryButton(
                                label = stringResource(R.string.months_open_month),
                                onClick = { onMonthClick(snapshot.period.incomeMonth) },
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag(
                                        "months-open-month-button-" +
                                            snapshot.period.incomeMonth
                                    )
                            )
                            when {
                                snapshot.period.outOfScope -> Unit
                                item.monthAlreadySettled -> {
                                    SbsActionStatus(
                                        label = stringResource(R.string.months_month_settled),
                                        icon = Icons.Outlined.CheckCircle,
                                        modifier =
                                        Modifier
                                            .weight(1f)
                                            .testTag(
                                                "months-closed-status-" +
                                                    snapshot.period.incomeMonth
                                            )
                                    )
                                }
                                item.canQuickSettleMonth -> {
                                    SbsSecondaryButton(
                                        label = stringResource(R.string.months_mark_month_settled),
                                        onClick = {
                                            pendingQuickSettleMonth =
                                                snapshot.period.incomeMonth.toString()
                                        },
                                        modifier =
                                        Modifier
                                            .weight(1f)
                                            .testTag(
                                                "months-complete-month-button-" +
                                                    snapshot.period.incomeMonth
                                            )
                                    )
                                }
                                else -> {
                                    item.filingOpensOn?.let { filingOpenDate ->
                                        SbsActionStatus(
                                            label =
                                            stringResource(
                                                R.string.months_filing_opens_on,
                                                filingOpenDate.formatIsoDate()
                                            ),
                                            icon = Icons.Outlined.Schedule,
                                            modifier =
                                            Modifier
                                                .weight(1f)
                                                .testTag(
                                                    "months-filing-status-" +
                                                        snapshot.period.incomeMonth
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
