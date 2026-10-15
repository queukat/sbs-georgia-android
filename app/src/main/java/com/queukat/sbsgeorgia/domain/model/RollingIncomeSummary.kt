package com.queukat.sbsgeorgia.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Recorded SBS income is an advisory proxy, not a determination of VAT-taxable turnover. */
data class RollingIncomeMonth(
    val month: YearMonth,
    val knownIncomeGel: BigDecimal,
    val entryCount: Int,
    val unresolvedCount: Int
)

data class RollingIncomeSummary(
    val asOf: LocalDate,
    val months: List<RollingIncomeMonth>,
    val monthlyMedianGel: BigDecimal?
) {
    val knownTotalGel: BigDecimal = months.fold(BigDecimal.ZERO) { sum, month -> sum + month.knownIncomeGel }
    val completedElevenGel: BigDecimal = knownTotalGel - months.last().knownIncomeGel
    val unresolvedCount: Int = months.sumOf { it.unresolvedCount }
    val leavingNextMonthGel: BigDecimal = months.first().knownIncomeGel
    val nextMonthScenarioGel: BigDecimal? = monthlyMedianGel?.let(::projectNextMonth)
    val distanceToThresholdGel: BigDecimal = (REGISTRATION_THRESHOLD - knownTotalGel).max(BigDecimal.ZERO)
    val thresholdExceeded: Boolean = knownTotalGel > REGISTRATION_THRESHOLD
    val warningNeeded: Boolean = knownTotalGel >= WARNING_LEVEL
    val forecastWarningNeeded: Boolean = nextMonthScenarioGel?.let { it >= WARNING_LEVEL } == true

    fun projectNextMonth(expectedIncomeGel: BigDecimal): BigDecimal? =
        if (unresolvedCount == 0 && expectedIncomeGel.signum() >= 0) {
            knownTotalGel - leavingNextMonthGel + expectedIncomeGel
        } else {
            null
        }

    companion object {
        val WARNING_LEVEL: BigDecimal = BigDecimal("90000")
        val REGISTRATION_THRESHOLD: BigDecimal = BigDecimal("100000")
    }
}
