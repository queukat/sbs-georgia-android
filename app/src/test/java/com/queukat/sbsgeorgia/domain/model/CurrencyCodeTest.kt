package com.queukat.sbsgeorgia.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyCodeTest {
    @Test
    fun normalizeCurrencyCodeTrimsAndUppercasesInput() {
        assertEquals("USD", normalizeCurrencyCode(" usd "))
    }

    @Test
    fun isIsoLikeCurrencyCodeAcceptsThreeLatinLettersOnly() {
        assertTrue(isIsoLikeCurrencyCode("gel"))
        assertTrue(isIsoLikeCurrencyCode(" usd "))
        assertFalse(isIsoLikeCurrencyCode("US"))
        assertFalse(isIsoLikeCurrencyCode("lari"))
        assertFalse(isIsoLikeCurrencyCode("???"))
    }
}
