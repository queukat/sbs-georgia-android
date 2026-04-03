package com.queukat.sbsgeorgia.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.usecase.ObserveCurrentYearSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.chartCumulativePoints
import com.queukat.sbsgeorgia.domain.usecase.chartMonthlyIncomePoints
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChartsViewModel @Inject constructor(
    observeCurrentYearSnapshotsUseCase: ObserveCurrentYearSnapshotsUseCase,
    clock: Clock,
) : ViewModel() {
    private val year = YearMonth.now(clock).year

    val uiState = observeCurrentYearSnapshotsUseCase(year)
        .map { snapshots ->
            val peakMonth = snapshots.maxByOrNull { it.graph20TotalGel }
            ChartsUiState(
                year = year,
                snapshots = snapshots,
                monthlyIncomePoints = chartMonthlyIncomePoints(snapshots),
                cumulativePoints = chartCumulativePoints(snapshots),
                ytdIncomeGel = snapshots.lastOrNull()?.graph15CumulativeGel ?: BigDecimal.ZERO,
                peakMonthLabel = peakMonth?.period?.incomeMonth?.formatMonthYear(),
                unresolvedMonthsCount = snapshots.count { it.unresolvedFxCount > 0 },
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChartsUiState(year = year),
        )
}
