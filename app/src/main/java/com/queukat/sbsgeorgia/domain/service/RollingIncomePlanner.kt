package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.RollingIncomeMonth
import com.queukat.sbsgeorgia.domain.model.RollingIncomeSummary
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

fun buildRollingIncomeSummary(entries: List<IncomeEntry>, asOf: LocalDate): RollingIncomeSummary {
    val currentMonth = YearMonth.from(asOf)
    val firstMonth = currentMonth.minusMonths(11)
    val relevantEntries = entries.filter {
        !it.incomeDate.isBefore(firstMonth.atDay(1)) &&
            !it.incomeDate.isAfter(asOf) &&
            it.declarationInclusion != DeclarationInclusion.EXCLUDED
    }
    val entriesByMonth = relevantEntries.groupBy { YearMonth.from(it.incomeDate) }
    val months = (0L..11L).map { offset ->
        val month = firstMonth.plusMonths(offset)
        val monthEntries = entriesByMonth[month].orEmpty()
        val amounts = monthEntries.map { entry ->
            if (entry.declarationInclusion != DeclarationInclusion.INCLUDED) {
                null
            } else {
                entry.gelEquivalent ?: entry.originalAmount.takeUnless { entry.requiresFxResolution() }
            }
        }
        RollingIncomeMonth(
            month = month,
            knownIncomeGel = amounts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add),
            entryCount = monthEntries.size,
            unresolvedCount = amounts.count { it == null }
        )
    }
    val history = months.dropLast(1).takeLast(6)
    // A missing month is not proof of zero income. Do not forecast across gaps or unresolved rows.
    val canForecast = months.none { it.unresolvedCount > 0 } && history.all { it.entryCount > 0 }
    val median = if (canForecast) {
        val ordered = history.map { it.knownIncomeGel }.sorted()
        (ordered[2] + ordered[3]).divide(BigDecimal("2"), 2, RoundingMode.HALF_UP)
    } else {
        null
    }
    return RollingIncomeSummary(asOf, months, median)
}
