package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxUseCasesTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun resolveMonthFxUsesExactCachedOfficialRate() = runTest {
        val incomeRepository = FakeIncomeRepository(
            initialEntries = listOf(
                sampleEntry(
                    id = 1L,
                    amount = "100.00",
                    currency = "USD",
                ),
            ),
        )
        val fxRateRepository = FakeFxRateRepository(
            bestRate = FxRate(
                rateDate = LocalDate.of(2026, 3, 15),
                currencyCode = "USD",
                units = 1,
                rateToGel = BigDecimal("2.70"),
                source = FxRateSource.OFFICIAL_NBG_JSON,
                manualOverride = false,
            ),
        )

        val result = ResolveMonthFxUseCase(
            fxRateRepository = fxRateRepository,
            incomeRepository = incomeRepository,
            clock = fixedClock,
        ).invoke(incomeRepository.entries.values.toList())

        val updatedEntry = incomeRepository.entries.getValue(1L)
        assertEquals("270.00", updatedEntry.gelEquivalent?.toPlainString())
        assertEquals(FxRateSource.OFFICIAL_NBG_JSON, updatedEntry.rateSource)
        assertEquals(1, result.resolvedEntryCount)
        assertEquals(0, result.unresolvedEntryCount)
    }

    @Test
    fun resolveMonthFxKeepsEntryUnresolvedWhenExactRateIsMissing() = runTest {
        val incomeRepository = FakeIncomeRepository(
            initialEntries = listOf(
                sampleEntry(
                    id = 2L,
                    amount = "50.00",
                    currency = "USD",
                ),
            ),
        )
        val fxRateRepository = FakeFxRateRepository(
            fetchResult = FxRateFetchResult.NotFound,
        )

        val result = ResolveMonthFxUseCase(
            fxRateRepository = fxRateRepository,
            incomeRepository = incomeRepository,
            clock = fixedClock,
        ).invoke(incomeRepository.entries.values.toList())

        val unresolvedEntry = incomeRepository.entries.getValue(2L)
        assertNull(unresolvedEntry.gelEquivalent)
        assertEquals(0, result.resolvedEntryCount)
        assertEquals(1, result.unresolvedEntryCount)
    }

    @Test
    fun applyManualFxOverrideUsesUnitsInConversionFormula() = runTest {
        val incomeRepository = FakeIncomeRepository(
            initialEntries = listOf(
                sampleEntry(
                    id = 3L,
                    amount = "10000",
                    currency = "JPY",
                ),
            ),
        )
        val fxRateRepository = FakeFxRateRepository()

        val updatedEntry = ApplyManualFxOverrideUseCase(
            fxRateRepository = fxRateRepository,
            incomeRepository = incomeRepository,
            clock = fixedClock,
        ).invoke(
            entryId = 3L,
            units = 100,
            rateToGel = BigDecimal("2.50"),
        )

        assertEquals("250.00", updatedEntry.gelEquivalent?.toPlainString())
        assertEquals(FxRateSource.MANUAL_OVERRIDE, updatedEntry.rateSource)
        assertEquals(true, updatedEntry.manualFxOverride)
    }

    @Test
    fun requiresFxResolutionReturnsTrueForUnresolvedNonGelEntry() {
        assertTrue(
            sampleEntry(
                id = 4L,
                amount = "10.00",
                currency = "USD",
            ).requiresFxResolution(),
        )
    }

    @Test
    fun requiresFxResolutionReturnsFalseForGelOrResolvedEntry() {
        val gelEntry = sampleEntry(
            id = 5L,
            amount = "10.00",
            currency = "GEL",
        )
        val resolvedEntry = sampleEntry(
            id = 6L,
            amount = "10.00",
            currency = "USD",
        ).copy(gelEquivalent = BigDecimal("27.00"))

        assertFalse(gelEntry.requiresFxResolution())
        assertFalse(resolvedEntry.requiresFxResolution())
    }

    private fun sampleEntry(
        id: Long,
        amount: String,
        currency: String,
    ): IncomeEntry = IncomeEntry(
        id = id,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = LocalDate.of(2026, 3, 15),
        originalAmount = BigDecimal(amount),
        originalCurrency = currency,
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

private class FakeIncomeRepository(
    initialEntries: List<IncomeEntry> = emptyList(),
) : IncomeRepository {
    val entries: MutableMap<Long, IncomeEntry> = initialEntries.associateBy { it.id }.toMutableMap()
    private val state = MutableStateFlow(entries.values.sortedBy { it.id })

    override fun observeAll(): Flow<List<IncomeEntry>> = state

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = state.map { items ->
        items.filter { YearMonth.from(it.incomeDate) == yearMonth }
    }

    override suspend fun getById(id: Long): IncomeEntry? = entries[id]

    override suspend fun upsert(entry: IncomeEntry): Long {
        val persistedId = if (entry.id == 0L) {
            (entries.keys.maxOrNull() ?: 0L) + 1L
        } else {
            entry.id
        }
        entries[persistedId] = entry.copy(id = persistedId)
        state.value = entries.values.sortedBy { it.id }
        return persistedId
    }

    override suspend fun deleteById(id: Long) {
        entries.remove(id)
        state.value = entries.values.sortedBy { it.id }
    }
}

private class FakeFxRateRepository(
    private val bestRate: FxRate? = null,
    private val fetchResult: FxRateFetchResult = FxRateFetchResult.NotFound,
) : FxRateRepository {
    override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate? = bestRate

    override suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult = fetchResult

    override suspend fun upsertManualOverride(
        rateDate: LocalDate,
        currencyCode: String,
        units: Int,
        rateToGel: BigDecimal,
    ): FxRate = FxRate(
        rateDate = rateDate,
        currencyCode = currencyCode,
        units = units,
        rateToGel = rateToGel,
        source = FxRateSource.MANUAL_OVERRIDE,
        manualOverride = true,
    )
}
