package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DeclarationUseCasesTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun collectRelevantSnapshotYearsIncludesImportedHistoryAndCurrentYear() {
        val years = collectRelevantSnapshotYears(
            clock = fixedClock,
            config = SmallBusinessStatusConfig(
                effectiveDate = LocalDate.of(2023, 12, 1),
                defaultTaxRatePercent = BigDecimal("1.0"),
            ),
            entries = listOf(
                sampleIncomeEntry(id = 1L, date = LocalDate.of(2024, 8, 12)),
                sampleIncomeEntry(id = 2L, date = LocalDate.of(2025, 11, 7)),
            ),
            records = listOf(
                MonthlyDeclarationRecord(
                    yearMonth = YearMonth.of(2022, 12),
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = false,
                ),
            ),
        )

        assertEquals(listOf(2026, 2025, 2024, 2023, 2022), years)
    }

    private fun sampleIncomeEntry(
        id: Long,
        date: LocalDate,
    ): IncomeEntry = IncomeEntry(
        id = id,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = date,
        originalAmount = BigDecimal("100.00"),
        originalCurrency = "USD",
        sourceCategory = "Software services",
        note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = null,
        rateSource = FxRateSource.NONE,
        manualFxOverride = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
