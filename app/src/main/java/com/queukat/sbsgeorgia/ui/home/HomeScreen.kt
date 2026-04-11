package com.queukat.sbsgeorgia.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.formatAmount

private const val SUPPORT_DEVELOPER_URL = "https://ko-fi.com/queukat"

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
    val uriHandler = LocalUriHandler.current

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
            DonutMark(size = 22.dp)
        }
    }
}

@Composable
private fun DonutMark(
    size: Dp,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = Modifier.size(size)) {
        val outerDiameter = this.size.minDimension * 0.82f
        val outerTopLeft = Offset(
            x = (this.size.width - outerDiameter) / 2f,
            y = (this.size.height - outerDiameter) / 2f,
        )
        val outerSize = Size(outerDiameter, outerDiameter)
        val strokeWidth = outerDiameter * 0.34f

        drawArc(
            color = colors.secondaryContainer,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = strokeWidth),
        )
        drawArc(
            color = colors.primary,
            startAngle = 195f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        val holeRadius = outerDiameter * 0.16f
        drawCircle(
            color = colors.surface,
            radius = holeRadius,
            center = center,
        )

        val sprinkleRadius = outerDiameter * 0.04f
        drawCircle(
            color = colors.tertiary,
            radius = sprinkleRadius,
            center = center + Offset(-outerDiameter * 0.18f, -outerDiameter * 0.13f),
        )
        drawCircle(
            color = colors.onPrimaryContainer,
            radius = sprinkleRadius * 0.9f,
            center = center + Offset(outerDiameter * 0.06f, -outerDiameter * 0.18f),
        )
        drawCircle(
            color = colors.tertiary,
            radius = sprinkleRadius * 0.95f,
            center = center + Offset(outerDiameter * 0.15f, outerDiameter * 0.03f),
        )
    }
}
