package com.queukat.sbsgeorgia.domain.service

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeorgiaTaxBusinessCalendarTest {
    private val calendar = GeorgiaTaxBusinessCalendar()

    @Test
    fun `marks fixed public holiday as non business day`() {
        assertTrue(calendar.isNonBusinessDay(LocalDate.of(2026, 5, 26)))
    }

    @Test
    fun `moves fixed public holiday to next business day`() {
        assertEquals(
            LocalDate.of(2026, 5, 27),
            calendar.adjustToNextBusinessDay(LocalDate.of(2026, 5, 26)),
        )
    }

    @Test
    fun `marks orthodox easter monday as non business day`() {
        assertTrue(calendar.isNonBusinessDay(LocalDate.of(2026, 4, 13)))
    }

    @Test
    fun `adjusts through holiday weekend chain`() {
        assertEquals(
            LocalDate.of(2026, 4, 14),
            calendar.adjustToNextBusinessDay(LocalDate.of(2026, 4, 12)),
        )
    }
}
