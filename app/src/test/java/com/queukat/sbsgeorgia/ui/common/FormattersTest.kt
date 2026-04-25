package com.queukat.sbsgeorgia.ui.common

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatAmountFallsBackSafelyForUnknownCurrencyCode() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        try {
            assertEquals("123.46 XYZ", formatAmount(BigDecimal("123.456"), " xyz "))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun formatAmountFallsBackSafelyForBlankCurrencyCode() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        try {
            assertEquals("123.40", formatAmount(BigDecimal("123.4"), "   "))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
