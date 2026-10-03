package com.queukat.sbsgeorgia.domain.service

import java.math.BigDecimal

/** Ordinary SBS only. Special tourism thresholds and other tax regimes are out of scope. */
object SmallBusinessTaxPolicy {
    val annualThresholdGel: BigDecimal = BigDecimal("500000")
    private val standardRate = BigDecimal.ONE
    private val higherRate = BigDecimal("3")

    fun rateFor(cumulativeIncomeGel: BigDecimal, configuredRatePercent: BigDecimal): BigDecimal =
        if (configuredRatePercent.compareTo(standardRate) == 0 && cumulativeIncomeGel > annualThresholdGel) {
            higherRate
        } else {
            // Preserve explicit legacy/manual rate settings; never silently reinterpret them.
            configuredRatePercent
        }
}
