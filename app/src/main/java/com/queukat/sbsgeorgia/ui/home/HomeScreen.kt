package com.queukat.sbsgeorgia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.formatAmount

@Composable
fun HomeRoute(
    innerPadding: PaddingValues,
    onOpenMonths: () -> Unit,
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
        onOpenCharts = onOpenCharts,
        onAddIncome = onAddIncome,
        onImportStatement = onImportStatement,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    uiState: HomeUiState,
    onOpenMonths: () -> Unit,
    onOpenCharts: () -> Unit,
    onAddIncome: () -> Unit,
    onImportStatement: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val summary = uiState.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (summary != null) {
            AppSection(title = stringResource(R.string.home_section_dashboard)) {
                KeyValueRow(
                    stringResource(R.string.home_setup_complete),
                    stringResource(if (summary.setupComplete) R.string.common_yes else R.string.common_no),
                )
                KeyValueRow(stringResource(R.string.home_ytd_income), formatAmount(summary.ytdIncomeGel, "GEL"))
                KeyValueRow(stringResource(R.string.home_unresolved_fx_entries), summary.unresolvedFxCount.toString())
                KeyValueRow(stringResource(R.string.home_unsettled_months), summary.unsettledMonthsCount.toString())
                summary.nextReminderDay?.let {
                    KeyValueRow(
                        stringResource(R.string.home_next_reminder),
                        stringResource(R.string.home_next_reminder_day, it),
                    )
                }
            }

            AppSection(title = stringResource(R.string.home_section_due_period)) {
                val duePeriod = summary.currentDuePeriod
                if (duePeriod == null) {
                    Text(stringResource(R.string.home_no_due_period))
                } else {
                    SnapshotSummary(snapshot = duePeriod)
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
}
