package com.queukat.sbsgeorgia.domain.model

import java.math.BigDecimal

/** Compare-and-set request: FX work may not restore a deleted row or overwrite an edit. */
data class IncomeFxUpdate(
    val expected: IncomeEntry,
    val gelEquivalent: BigDecimal,
    val rateSource: FxRateSource,
    val manualOverride: Boolean,
    val updatedAtEpochMillis: Long
) {
    fun applyTo(entry: IncomeEntry): IncomeEntry = entry.copy(
        gelEquivalent = gelEquivalent,
        rateSource = rateSource,
        manualFxOverride = manualOverride,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}
