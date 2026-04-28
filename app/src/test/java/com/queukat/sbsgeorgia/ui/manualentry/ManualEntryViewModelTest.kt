package com.queukat.sbsgeorgia.ui.manualentry

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManualEntryViewModelTest {
    @Test
    fun resolvedManualEntrySourceTypeRepairsLegacyManualImportedEntry() {
        val result = resolvedManualEntrySourceType(
            existingEntry().copy(
                sourceType = IncomeSourceType.MANUAL,
                sourceTransactionFingerprint = "tx-fingerprint",
            ),
        )

        assertEquals(IncomeSourceType.IMPORTED_STATEMENT, result)
    }

    @Test
    fun resolveManualEntryFxPersistenceClearsStaleFxWhenAmountChanges() {
        val result = resolveManualEntryFxPersistence(
            currency = " usd ",
            amount = BigDecimal("200.00"),
            incomeDate = LocalDate.of(2026, 3, 15),
            existing = existingEntry(),
        )

        assertEquals("USD", result.normalizedCurrency)
        assertNull(result.gelEquivalent)
        assertEquals(FxRateSource.NONE, result.rateSource)
        assertFalse(result.manualFxOverride)
    }

    @Test
    fun resolveManualEntryFxPersistenceResetsManualFxWhenCurrencyChangesToGel() {
        val amount = BigDecimal("200.00")

        val result = resolveManualEntryFxPersistence(
            currency = "gel",
            amount = amount,
            incomeDate = LocalDate.of(2026, 3, 15),
            existing = existingEntry(),
        )

        assertEquals("GEL", result.normalizedCurrency)
        assertEquals(amount, result.gelEquivalent)
        assertEquals(FxRateSource.NONE, result.rateSource)
        assertFalse(result.manualFxOverride)
    }

    @Test
    fun resolveManualEntryFxPersistenceKeepsExistingFxWhenFxFieldsStayTheSame() {
        val existing = existingEntry()

        val result = resolveManualEntryFxPersistence(
            currency = "USD",
            amount = BigDecimal("100.00"),
            incomeDate = LocalDate.of(2026, 3, 15),
            existing = existing,
        )

        assertEquals("USD", result.normalizedCurrency)
        assertEquals(existing.gelEquivalent, result.gelEquivalent)
        assertEquals(existing.rateSource, result.rateSource)
        assertEquals(existing.manualFxOverride, result.manualFxOverride)
    }

    private fun existingEntry(): IncomeEntry = IncomeEntry(
        id = 7L,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = LocalDate.of(2026, 3, 15),
        originalAmount = BigDecimal("100.00"),
        originalCurrency = "USD",
        sourceCategory = "Software services",
        note = "Invoice 001",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = BigDecimal("270.00"),
        rateSource = FxRateSource.MANUAL_OVERRIDE,
        manualFxOverride = true,
        sourceTransactionFingerprint = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
    )
}
