package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

data class MonthFxResolutionResult(val resolvedEntryCount: Int, val unresolvedEntryCount: Int, val message: String)

class ResolveMonthFxUseCase
@Inject
constructor(
    private val fxRateRepository: FxRateRepository,
    private val incomeRepository: IncomeRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(entries: List<IncomeEntry>): MonthFxResolutionResult {
        val unresolvedEntries = entries.filter(IncomeEntry::requiresFxResolution)
        if (unresolvedEntries.isEmpty()) {
            return MonthFxResolutionResult(
                resolvedEntryCount = 0,
                unresolvedEntryCount = 0,
                message = "No unresolved FX entries were found."
            )
        }

        val groups = unresolvedEntries.groupBy {
            it.incomeDate to
                it.originalCurrency.uppercase()
        }
        var resolvedCount = 0
        var unresolvedCount = 0
        val errors = mutableListOf<String>()

        groups.forEach { (_, groupedEntries) ->
            val sampleEntry = groupedEntries.first()
            val resolvedRate =
                fxRateRepository.getBestRate(
                    sampleEntry.incomeDate,
                    sampleEntry.originalCurrency
                )
                    ?: when (
                        val fetchedRate =
                            fxRateRepository.fetchOfficialRate(
                                sampleEntry.incomeDate,
                                sampleEntry.originalCurrency
                            )
                    ) {
                        is FxRateFetchResult.Success -> fetchedRate.rate
                        FxRateFetchResult.NotFound -> null
                        is FxRateFetchResult.Error -> {
                            errors += fetchedRate.message
                            null
                        }
                    }

            if (resolvedRate == null) {
                unresolvedCount += groupedEntries.size
            } else {
                groupedEntries.forEach { entry ->
                    incomeRepository.upsert(
                        entry.applyFxRate(
                            resolvedRate.units,
                            resolvedRate.rateToGel,
                            resolvedRate.source,
                            resolvedRate.manualOverride,
                            clock
                        )
                    )
                    resolvedCount += 1
                }
            }
        }

        val message =
            when {
                resolvedCount > 0 && unresolvedCount == 0 -> "Resolved FX for $resolvedCount entries."
                resolvedCount > 0 -> "Resolved FX for $resolvedCount entries, $unresolvedCount still need review."
                errors.isNotEmpty() -> errors.first()
                else -> "$unresolvedCount entries still need manual FX review."
            }

        return MonthFxResolutionResult(
            resolvedEntryCount = resolvedCount,
            unresolvedEntryCount = unresolvedCount,
            message = message
        )
    }
}

class ApplyManualFxOverrideUseCase
@Inject
constructor(
    private val fxRateRepository: FxRateRepository,
    private val incomeRepository: IncomeRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(entryId: Long, units: Int, rateToGel: BigDecimal): IncomeEntry {
        val entry =
            requireNotNull(incomeRepository.getById(entryId)) {
                "Income entry $entryId was not found."
            }
        val fxRate =
            fxRateRepository.upsertManualOverride(
                rateDate = entry.incomeDate,
                currencyCode = entry.originalCurrency,
                units = units,
                rateToGel = rateToGel
            )
        val updatedEntry =
            entry.applyFxRate(
                units = fxRate.units,
                rateToGel = fxRate.rateToGel,
                rateSource = FxRateSource.MANUAL_OVERRIDE,
                manualOverride = true,
                clock = clock
            )
        incomeRepository.upsert(updatedEntry)
        return updatedEntry
    }
}

private fun IncomeEntry.applyFxRate(
    units: Int,
    rateToGel: BigDecimal,
    rateSource: FxRateSource,
    manualOverride: Boolean,
    clock: Clock
): IncomeEntry {
    val divisor = BigDecimal(units.coerceAtLeast(1))
    val gelAmount =
        originalAmount
            .multiply(rateToGel)
            .divide(divisor, 2, RoundingMode.HALF_UP)

    return copy(
        gelEquivalent = gelAmount,
        rateSource = rateSource,
        manualFxOverride = manualOverride,
        updatedAtEpochMillis = clock.millis()
    )
}
