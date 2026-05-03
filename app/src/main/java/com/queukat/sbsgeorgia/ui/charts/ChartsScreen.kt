@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.usecase.ChartPoint
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.formatAmount

@Composable
fun ChartsRoute(innerPadding: PaddingValues, onBack: () -> Unit) {
    val viewModel: ChartsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChartsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onYearSelected = viewModel::selectYear,
        onBack = onBack
    )
}

@Composable
fun ChartsScreen(
    innerPadding: PaddingValues,
    uiState: ChartsUiState,
    onYearSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = stringResource(R.string.charts_title, uiState.year),
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
            if (uiState.availableYears.size > 1) {
                AppSection(title = stringResource(R.string.charts_section_year_selector)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableYears.forEach { year ->
                            FilterChip(
                                selected = uiState.year == year,
                                onClick = { onYearSelected(year) },
                                label = { Text(year.toString()) }
                            )
                        }
                    }
                }
            }

            AppSection(title = stringResource(R.string.charts_section_year_overview)) {
                KeyValueRow(
                    stringResource(R.string.charts_ytd_income),
                    formatAmount(uiState.ytdIncomeGel, "GEL")
                )
                KeyValueRow(
                    stringResource(R.string.charts_unresolved_months),
                    uiState.unresolvedMonthsCount.toString()
                )
                KeyValueRow(
                    stringResource(R.string.charts_peak_month),
                    uiState.peakMonthLabel ?: stringResource(R.string.charts_peak_month_empty)
                )
            }

            AppSection(title = stringResource(R.string.charts_section_monthly_income)) {
                if (uiState.monthlyIncomePoints.isEmpty()) {
                    Text(stringResource(R.string.charts_no_monthly_data))
                } else if (uiState.monthlyIncomePoints.all { it.value.signum() == 0 }) {
                    Text(stringResource(R.string.charts_no_income_for_selected_year))
                } else {
                    Text(
                        stringResource(R.string.charts_monthly_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BarChart(points = uiState.monthlyIncomePoints)
                    ChartSummary(points = uiState.monthlyIncomePoints)
                }
            }

            AppSection(title = stringResource(R.string.charts_section_yearly_cumulative)) {
                if (uiState.cumulativePoints.isEmpty()) {
                    Text(stringResource(R.string.charts_no_cumulative_data))
                } else if (uiState.cumulativePoints.all { it.value.signum() == 0 }) {
                    Text(stringResource(R.string.charts_no_income_for_selected_year))
                } else {
                    Text(
                        stringResource(R.string.charts_cumulative_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CumulativeLineChart(points = uiState.cumulativePoints)
                    ChartSummary(points = uiState.cumulativePoints)
                }
            }
        }
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>) {
    val maxValue = points.maxOfOrNull { it.value } ?: java.math.BigDecimal.ONE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChartScaleLabels(maxValue = maxValue, chartHeight = 140.dp)
        Row(
            modifier =
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .semantics {
                    contentDescription = chartContentDescription(points)
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            points.forEach { point ->
                val ratio =
                    if (maxValue.signum() == 0) {
                        0f
                    } else {
                        point.value.divide(maxValue, 4, java.math.RoundingMode.HALF_UP).toFloat()
                    }
                Column(
                    modifier = Modifier.width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatAmount(point.value, CHART_CURRENCY),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Box(
                        modifier = Modifier.height(140.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height((12 + (ratio * 128f)).dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        ) {}
                    }
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CumulativeLineChart(points: List<ChartPoint>) {
    val chartWidth = (points.size * 56).dp
    val maxValue = points.maxOfOrNull { it.value } ?: java.math.BigDecimal.ONE
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChartScaleLabels(maxValue = maxValue, chartHeight = 180.dp)
        Column(
            modifier =
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .semantics {
                    contentDescription = chartContentDescription(points)
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(
                modifier =
                Modifier
                    .width(chartWidth)
                    .height(180.dp)
            ) {
                val usableHeight = size.height * 0.82f
                val baseline = usableHeight
                val stepX = if (points.size <=
                    1
                ) {
                    size.width / 2f
                } else {
                    size.width / (points.size - 1)
                }

                for (index in 0..3) {
                    val y = baseline - (baseline / 3f) * index
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f
                    )
                }

                val offsets =
                    points.mapIndexed { index, point ->
                        val ratio =
                            if (maxValue.signum() == 0) {
                                0f
                            } else {
                                point.value.divide(
                                    maxValue,
                                    4,
                                    java.math.RoundingMode.HALF_UP
                                ).toFloat()
                            }
                        Offset(
                            x = if (points.size <= 1) size.width / 2f else index * stepX,
                            y = baseline - (usableHeight * ratio)
                        )
                    }

                if (offsets.isNotEmpty()) {
                    val path =
                        Path().apply {
                            moveTo(offsets.first().x, offsets.first().y)
                            offsets.drop(1).forEach { offset ->
                                lineTo(offset.x, offset.y)
                            }
                        }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    offsets.forEach { offset ->
                        drawCircle(
                            color = lineColor,
                            radius = 8f,
                            center = offset
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.width(chartWidth),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartScaleLabels(maxValue: java.math.BigDecimal, chartHeight: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier.height(chartHeight),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatAmount(maxValue, CHART_CURRENCY),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = formatAmount(maxValue.divide(java.math.BigDecimal("2")), CHART_CURRENCY),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = formatAmount(java.math.BigDecimal.ZERO, CHART_CURRENCY),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ChartSummary(points: List<ChartPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        points.forEach { point ->
            KeyValueRow(point.label, formatAmount(point.value, CHART_CURRENCY))
        }
    }
}

private fun chartContentDescription(points: List<ChartPoint>): String = points.joinToString(separator = ". ") { point ->
    "${point.label}: ${formatAmount(point.value, CHART_CURRENCY)}"
}

private const val CHART_CURRENCY = "GEL"
