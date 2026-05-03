package com.queukat.sbsgeorgia.ui.charts

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.usecase.ChartPoint
import java.math.BigDecimal

data class ChartsUiState(
    val year: Int,
    val availableYears: List<Int> = emptyList(),
    val snapshots: List<MonthlyDeclarationSnapshot> = emptyList(),
    val monthlyIncomePoints: List<ChartPoint> = emptyList(),
    val cumulativePoints: List<ChartPoint> = emptyList(),
    val ytdIncomeGel: BigDecimal = BigDecimal.ZERO,
    val peakMonthLabel: String? = null,
    val unresolvedMonthsCount: Int = 0
)
