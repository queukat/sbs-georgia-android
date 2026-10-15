package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingIncomePlannerTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun spansYearsAndSeparatesElevenCompletedMonthsFromCurrentMonth() {
        val summary = buildRollingIncomeSummary(
            listOf(
                entry("2025-10-31", "999"),
                entry("2025-11-01", "100"),
                entry("2026-09-30", "200"),
                entry("2026-10-15", "50"),
                entry("2026-10-16", "999"),
                entry("2026-11-01", "999")
            ),
            today
        )
        assertEquals(12, summary.months.size)
        assertEquals(YearMonth.of(2025, 11), summary.months.first().month)
        assertEquals(BigDecimal("350"), summary.knownTotalGel)
        assertEquals(BigDecimal("300"), summary.completedElevenGel)
        assertEquals(BigDecimal("100"), summary.leavingNextMonthGel)
        assertEquals(BigDecimal("750"), summary.projectNextMonth(BigDecimal("500")))
    }

    @Test
    fun usesStoredFxAndNativeGelButDoesNotCountExcludedOrUnresolvedIncome() {
        val summary = buildRollingIncomeSummary(
            listOf(
                entry("2026-09-01", "10"),
                entry("2026-09-02", "20").copy(originalCurrency = "USD", gelEquivalent = BigDecimal("54.25")),
                entry("2026-09-03", "30").copy(originalCurrency = "USD"),
                entry("2026-09-04", "40").copy(declarationInclusion = DeclarationInclusion.REVIEW_REQUIRED),
                entry("2026-09-05", "50").copy(declarationInclusion = DeclarationInclusion.EXCLUDED)
            ),
            today
        )
        assertEquals(BigDecimal("64.25"), summary.knownTotalGel)
        assertEquals(2, summary.unresolvedCount)
        assertNull(summary.monthlyMedianGel)
        assertNull(summary.projectNextMonth(BigDecimal("500")))
    }

    @Test
    fun distinguishesEarlyWarningEqualityAndStrictExceedance() {
        val low = buildRollingIncomeSummary(listOf(entry("2026-10-01", "89999.99")), today)
        val warning = buildRollingIncomeSummary(listOf(entry("2026-10-01", "90000")), today)
        val equal = buildRollingIncomeSummary(listOf(entry("2026-10-01", "100000")), today)
        val over = buildRollingIncomeSummary(listOf(entry("2026-10-01", "100000.01")), today)
        assertFalse(low.warningNeeded)
        assertTrue(warning.warningNeeded)
        assertFalse(equal.thresholdExceeded)
        assertTrue(over.thresholdExceeded)
        assertEquals(BigDecimal.ZERO, over.distanceToThresholdGel)
    }

    @Test
    fun forecastUsesSixCompletedMonthsMedianAndDropsOldBonus() {
        val entries = (4..9).map { month -> entry("2026-%02d-01".format(month), "8000") } +
            listOf(entry("2025-11-01", "40000"), entry("2026-10-01", "5000"))
        val summary = buildRollingIncomeSummary(entries, today)
        assertEquals(BigDecimal("8000.00"), summary.monthlyMedianGel)
        assertEquals(BigDecimal("93000"), summary.knownTotalGel)
        assertEquals(BigDecimal("61000.00"), summary.nextMonthScenarioGel)
        assertFalse(summary.forecastWarningNeeded)
    }

    @Test
    fun warnsBeforeProjectedCrossingAndAllowsManualEstimateAcrossHistoryGaps() {
        val entries = (4..9).map { month -> entry("2026-%02d-01".format(month), "14000") }
        val summary = buildRollingIncomeSummary(entries, today)
        assertFalse(summary.warningNeeded)
        assertTrue(summary.forecastWarningNeeded)
        assertEquals(BigDecimal("98000.00"), summary.nextMonthScenarioGel)
        val gap = buildRollingIncomeSummary(entries.drop(1), today)
        assertNull(gap.monthlyMedianGel)
        assertEquals(BigDecimal("101000"), gap.projectNextMonth(BigDecimal("31000")))
        assertNull(gap.projectNextMonth(BigDecimal("-1")))
    }

    @Test
    fun monthRolloverAndLeapDayMoveWindowWithoutNewEntries() {
        val entries = listOf(entry("2024-02-29", "123"), entry("2024-03-01", "456"))
        val february = buildRollingIncomeSummary(entries, LocalDate.of(2025, 2, 28))
        val january = buildRollingIncomeSummary(entries, LocalDate.of(2025, 1, 31))
        assertEquals(BigDecimal("579"), january.knownTotalGel)
        assertEquals(BigDecimal("456"), february.knownTotalGel)
        assertEquals(12, february.months.size)
    }

    @Test
    fun emptyHistoryHasNoForecastOrInventedIncome() {
        val summary = buildRollingIncomeSummary(emptyList(), today)
        assertEquals(BigDecimal.ZERO, summary.knownTotalGel)
        assertNull(summary.monthlyMedianGel)
        assertTrue(summary.months.all { it.entryCount == 0 })
    }

    private fun entry(date: String, amount: String) = IncomeEntry(
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = LocalDate.parse(date),
        originalAmount = BigDecimal(amount),
        originalCurrency = "GEL",
        sourceCategory = "Services",
        note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = null,
        rateSource = FxRateSource.NONE,
        manualFxOverride = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )
}
