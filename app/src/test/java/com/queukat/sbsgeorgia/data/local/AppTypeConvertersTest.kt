package com.queukat.sbsgeorgia.data.local

import com.queukat.sbsgeorgia.domain.model.BaseCurrencyView
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypeConvertersTest {
    private val converters = AppTypeConverters()

    @Test
    fun encodesStableDbCodesForPersistedEnums() {
        assertEquals("system", converters.fromThemeMode(ThemeMode.SYSTEM))
        assertEquals("gel", converters.fromBaseCurrencyView(BaseCurrencyView.GEL))
        assertEquals("manual", converters.fromIncomeSourceType(IncomeSourceType.MANUAL))
        assertEquals("review_required", converters.fromDeclarationInclusion(DeclarationInclusion.REVIEW_REQUIRED))
        assertEquals("manual_override", converters.fromFxRateSource(FxRateSource.MANUAL_OVERRIDE))
    }

    @Test
    fun decodesLegacyEnumNamesForBackwardCompatibility() {
        assertEquals(ThemeMode.DARK, converters.toThemeMode("DARK"))
        assertEquals(BaseCurrencyView.GEL, converters.toBaseCurrencyView("GEL"))
        assertEquals(IncomeSourceType.IMPORTED_STATEMENT, converters.toIncomeSourceType("IMPORTED_STATEMENT"))
        assertEquals(DeclarationInclusion.INCLUDED, converters.toDeclarationInclusion("INCLUDED"))
        assertEquals(FxRateSource.OFFICIAL_NBG_JSON, converters.toFxRateSource("OFFICIAL_NBG_JSON"))
    }

    @Test
    fun decodesStableDbCodesForForwardCompatibility() {
        assertEquals(ThemeMode.LIGHT, converters.toThemeMode("light"))
        assertEquals(BaseCurrencyView.GEL, converters.toBaseCurrencyView("gel"))
        assertEquals(IncomeSourceType.MANUAL, converters.toIncomeSourceType("manual"))
        assertEquals(DeclarationInclusion.EXCLUDED, converters.toDeclarationInclusion("excluded"))
        assertEquals(FxRateSource.NONE, converters.toFxRateSource("none"))
    }
}
