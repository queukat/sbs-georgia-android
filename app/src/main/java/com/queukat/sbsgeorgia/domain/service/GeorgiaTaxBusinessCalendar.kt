package com.queukat.sbsgeorgia.domain.service

import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeorgiaTaxBusinessCalendar
@Inject
constructor() {
    fun adjustToNextBusinessDay(date: LocalDate): LocalDate {
        var candidate = date
        while (isNonBusinessDay(candidate)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun isNonBusinessDay(date: LocalDate): Boolean = date.dayOfWeek in weekendDays || date in holidays(date.year)

    internal fun holidays(year: Int): Set<LocalDate> =
        fixedHolidays(year) + orthodoxEasterHolidays(year) + governmentOverrideDaysOff(year)

    private fun fixedHolidays(year: Int): Set<LocalDate> = setOf(
        LocalDate.of(year, 1, 1),
        LocalDate.of(year, 1, 2),
        LocalDate.of(year, 1, 7),
        LocalDate.of(year, 1, 19),
        LocalDate.of(year, 3, 3),
        LocalDate.of(year, 3, 8),
        LocalDate.of(year, 4, 9),
        LocalDate.of(year, 5, 9),
        LocalDate.of(year, 5, 12),
        LocalDate.of(year, 5, 17),
        LocalDate.of(year, 5, 26),
        LocalDate.of(year, 8, 28),
        LocalDate.of(year, 10, 14),
        LocalDate.of(year, 11, 23)
    )

    private fun orthodoxEasterHolidays(year: Int): Set<LocalDate> {
        val easterSunday = orthodoxEasterSunday(year)
        return setOf(
            easterSunday.minusDays(2),
            easterSunday.minusDays(1),
            easterSunday,
            easterSunday.plusDays(1)
        )
    }

    private fun orthodoxEasterSunday(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = ((d + e + 114) % 31) + 1

        // Georgia follows Orthodox Easter dates. For the app's supported modern years
        // the Julian-to-Gregorian offset is 13 days.
        return LocalDate.of(year, month, day).plusDays(13)
    }

    private fun governmentOverrideDaysOff(year: Int): Set<LocalDate> = additionalGovernmentDaysOff[year].orEmpty()

    private companion object {
        val weekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        // Rare government-declared extra days off can be patched here without changing
        // the core holiday rules.
        val additionalGovernmentDaysOff: Map<Int, Set<LocalDate>> = emptyMap()
    }
}
