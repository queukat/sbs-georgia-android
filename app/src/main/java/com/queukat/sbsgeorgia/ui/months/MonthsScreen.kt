@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.months

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.SimpleChip
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import java.time.YearMonth

@Composable
fun MonthsRoute(
    innerPadding: PaddingValues,
    onMonthClick: (YearMonth) -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit
) {
    val viewModel: MonthsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MonthsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onMonthClick = onMonthClick,
        onSettleMonth = viewModel::settleMonth,
        onAddIncome = onAddIncome,
        onImportStatement = onImportStatement
    )
}

@Composable
fun MonthsScreen(
    innerPadding: PaddingValues,
    uiState: MonthsUiState,
    onMonthClick: (YearMonth) -> Unit,
    onSettleMonth: (YearMonth) -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit
) {
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
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    AppSection(title = snapshot.period.incomeMonth.formatMonthYear()) {
                        SnapshotSummary(snapshot = snapshot)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { onMonthClick(snapshot.period.incomeMonth) }) {
                                Text(stringResource(R.string.months_open_month))
                            }
                            when {
                                snapshot.period.outOfScope -> Unit
                                item.monthAlreadySettled -> {
                                    SimpleChip(stringResource(R.string.months_month_settled))
                                }
                                item.canQuickSettleMonth -> {
                                    OutlinedButton(onClick = {
                                        onSettleMonth(snapshot.period.incomeMonth)
                                    }) {
                                        Text(stringResource(R.string.months_mark_month_settled))
                                    }
                                }
                                else -> {
                                    item.filingOpensOn?.let { filingOpenDate ->
                                        SimpleChip(
                                            stringResource(
                                                R.string.months_filing_opens_on,
                                                filingOpenDate.formatIsoDate()
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
