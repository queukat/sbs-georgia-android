package com.queukat.sbsgeorgia.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.chartCumulativePoints
import com.queukat.sbsgeorgia.domain.usecase.chartMonthlyIncomePoints
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChartsViewModel
@Inject
constructor(observeAllSnapshotsUseCase: ObserveAllSnapshotsUseCase, clock: Clock) :
    ViewModel() {
    private val initialYear = YearMonth.now(clock).year
    private val selectedYear = MutableStateFlow(initialYear)

    val uiState =
        combine(
            observeAllSnapshotsUseCase(),
            selectedYear
        ) { snapshots, selectedYear ->
            buildChartsUiState(snapshots = snapshots, selectedYear = selectedYear)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChartsUiState(year = initialYear)
        )

    fun selectYear(year: Int) {
        selectedYear.value = year
    }
}

internal fun buildChartsUiState(snapshots: List<MonthlyDeclarationSnapshot>, selectedYear: Int): ChartsUiState {
    val availableYears =
        snapshots
            .map { it.period.incomeMonth.year }
            .distinct()
            .sortedDescending()
    val effectiveYear =
        if (selectedYear in availableYears) {
            selectedYear
        } else {
            availableYears.firstOrNull() ?: selectedYear
        }
    val yearSnapshots =
        snapshots
            .filter { it.period.incomeMonth.year == effectiveYear }
            .sortedBy { it.period.incomeMonth }
    val peakMonth =
        yearSnapshots
            .filter { it.graph20TotalGel.signum() > 0 }
            .maxByOrNull { it.graph20TotalGel }

    return ChartsUiState(
        year = effectiveYear,
        availableYears = availableYears,
        snapshots = yearSnapshots,
        monthlyIncomePoints = chartMonthlyIncomePoints(yearSnapshots),
        cumulativePoints = chartCumulativePoints(yearSnapshots),
        ytdIncomeGel =
        yearSnapshots.lastOrNull()?.graph15CumulativeGel ?: BigDecimal.ZERO,
        peakMonthLabel = peakMonth?.period?.incomeMonth?.formatMonthYear(),
        peakMonthIncomeGel = peakMonth?.graph20TotalGel,
        incomeMonthsCount = yearSnapshots.count { it.graph20TotalGel.signum() > 0 },
        unresolvedMonthsCount = yearSnapshots.count { it.unresolvedFxCount > 0 },
        unresolvedEntriesCount = yearSnapshots.sumOf { it.unresolvedFxCount }
    )
}
