package com.queukat.sbsgeorgia.ui.charts

import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartsUiStateBuilderTest {
    @Test
    fun zeroIncomeDoesNotCreateAPeakMonth() {
        val state =
            buildChartsUiState(
                snapshots =
                listOf(
                    snapshot(YearMonth.of(2026, 1), monthly = "0.00", cumulative = "0.00"),
                    snapshot(YearMonth.of(2026, 2), monthly = "0.00", cumulative = "0.00")
                ),
                selectedYear = 2026
            )

        assertNull(state.peakMonthLabel)
        assertNull(state.peakMonthIncomeGel)
        assertEquals(0, state.incomeMonthsCount)
    }

    @Test
    fun overviewCountsIncomeAndUnresolvedFxAndKeepsPeakAmount() {
        val state =
            buildChartsUiState(
                snapshots =
                listOf(
                    snapshot(
                        YearMonth.of(2026, 1),
                        monthly = "100.00",
                        cumulative = "100.00",
                        unresolvedFxCount = 2
                    ),
                    snapshot(
                        YearMonth.of(2026, 2),
                        monthly = "900.00",
                        cumulative = "1000.00",
                        unresolvedFxCount = 1
                    ),
                    snapshot(YearMonth.of(2026, 3), monthly = "0.00", cumulative = "1000.00")
                ),
                selectedYear = 2026
            )

        assertEquals(BigDecimal("900.00"), state.peakMonthIncomeGel)
        assertTrue(state.peakMonthLabel?.isNotBlank() == true)
        assertEquals(2, state.incomeMonthsCount)
        assertEquals(2, state.unresolvedMonthsCount)
        assertEquals(3, state.unresolvedEntriesCount)
        assertEquals(BigDecimal("1000.00"), state.ytdIncomeGel)
    }

    @Test
    fun unavailableSelectionFallsBackToNewestAvailableYear() {
        val state =
            buildChartsUiState(
                snapshots =
                listOf(
                    snapshot(YearMonth.of(2025, 12), monthly = "10.00", cumulative = "10.00"),
                    snapshot(YearMonth.of(2026, 1), monthly = "20.00", cumulative = "20.00")
                ),
                selectedYear = 2024
            )

        assertEquals(2026, state.year)
        assertEquals(listOf(2026, 2025), state.availableYears)
    }

    private fun snapshot(
        month: YearMonth,
        monthly: String,
        cumulative: String,
        unresolvedFxCount: Int = 0
    ): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
        period =
        MonthlyDeclarationPeriod(
            incomeMonth = month,
            filingWindow =
            FilingWindow(
                start = month.plusMonths(1).atDay(1),
                endInclusive = month.plusMonths(1).atDay(15),
                dueDate = month.plusMonths(1).atDay(15)
            ),
            inScope = true,
            outOfScope = false
        ),
        workflowStatus = MonthlyWorkflowStatus.DRAFT,
        graph20TotalGel = BigDecimal(monthly),
        graph15CumulativeGel = BigDecimal(cumulative),
        originalCurrencyTotals = emptyList(),
        estimatedTaxAmountGel = BigDecimal.ZERO,
        unresolvedFxCount = unresolvedFxCount,
        zeroDeclarationSuggested = false,
        zeroDeclarationPrepared = false,
        reviewNeeded = false,
        setupRequired = false,
        record = null
    )
}
