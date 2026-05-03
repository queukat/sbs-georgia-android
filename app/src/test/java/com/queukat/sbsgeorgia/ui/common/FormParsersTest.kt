package com.queukat.sbsgeorgia.ui.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormParsersTest {
    @Test
    fun parseOptionalIsoDateReturnsEmptyForBlankInput() {
        val result = DateInputParser.parseOptionalIsoDate("   ")

        assertTrue(result is DateParseResult.Empty)
        assertNull(result.dateOrNull())
    }

    @Test
    fun parseOptionalIsoDateReturnsValidDateForIsoInput() {
        val result = DateInputParser.parseOptionalIsoDate("2026-04-25")

        assertEquals(
            DateParseResult.Valid(LocalDate.of(2026, 4, 25)),
            result
        )
        assertEquals(LocalDate.of(2026, 4, 25), result.dateOrNull())
    }

    @Test
    fun parseOptionalIsoDateReturnsInvalidForMalformedInput() {
        val result = DateInputParser.parseOptionalIsoDate("25/04/2026")

        assertEquals(DateParseResult.Invalid("25/04/2026"), result)
        assertNull(result.dateOrNull())
    }
}
