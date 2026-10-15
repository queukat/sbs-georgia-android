package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeFxUpdate
import com.queukat.sbsgeorgia.domain.model.normalizeCurrencyCode
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

data class MonthFxResolutionResult(
    val resolvedEntryCount: Int,
    val unresolvedEntryCount: Int,
    val message: String,
    val skippedChangedEntryCount: Int = 0
)

class ResolveMonthFxUseCase
@Inject
constructor(
    private val fxRateRepository: FxRateRepository,
    private val incomeRepository: IncomeRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(entries: List<IncomeEntry>): MonthFxResolutionResult {
        val unresolvedEntries = entries.filter {
            it.declarationInclusion == DeclarationInclusion.INCLUDED && it.requiresFxResolution()
        }
        if (unresolvedEntries.isEmpty()) {
            return MonthFxResolutionResult(
                resolvedEntryCount = 0,
                unresolvedEntryCount = 0,
                message = "No unresolved FX entries were found."
            )
        }

        val groups = unresolvedEntries.groupBy {
            it.incomeDate to
                normalizeCurrencyCode(it.originalCurrency)
        }
        var resolvedCount = 0
        var unresolvedCount = 0
        var skippedChangedCount = 0
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
                val updates = groupedEntries.map { entry ->
                    entry.fxUpdate(
                        resolvedRate.units,
                        resolvedRate.rateToGel,
                        resolvedRate.source,
                        resolvedRate.manualOverride,
                        clock
                    )
                }
                val applied = incomeRepository.applyFxUpdates(updates)
                resolvedCount += applied
                skippedChangedCount += updates.size - applied
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
            message = message,
            skippedChangedEntryCount = skippedChangedCount
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
        require(units > 0) { "FX units must be greater than zero." }
        require(rateToGel > BigDecimal.ZERO) { "FX rate must be greater than zero." }
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
        val update = entry.fxUpdate(
            units = fxRate.units,
            rateToGel = fxRate.rateToGel,
            rateSource = FxRateSource.MANUAL_OVERRIDE,
            manualOverride = true,
            clock = clock
        )
        check(incomeRepository.applyFxUpdates(listOf(update)) == 1) {
            "The income entry changed or was deleted. Reopen it before applying a rate."
        }
        return update.applyTo(entry)
    }
}

private fun IncomeEntry.fxUpdate(
    units: Int,
    rateToGel: BigDecimal,
    rateSource: FxRateSource,
    manualOverride: Boolean,
    clock: Clock
): IncomeFxUpdate {
    require(units > 0 && rateToGel.signum() > 0) { "FX rate and units must be positive." }
    val gelAmount = originalAmount.multiply(rateToGel).divide(BigDecimal(units), 2, RoundingMode.HALF_UP)
    return IncomeFxUpdate(this, gelAmount, rateSource, manualOverride, clock.millis())
}

/** A cache rate is only displayable when it reproduces the stored GEL amount. */
suspend fun loadMatchingFxRateDetails(entries: List<IncomeEntry>, repository: FxRateRepository): Map<Long, FxRate> {
    val result = mutableMapOf<Long, FxRate>()
    entries.filter { it.gelEquivalent != null && normalizeCurrencyCode(it.originalCurrency) != "GEL" }
        .groupBy { Triple(it.incomeDate, normalizeCurrencyCode(it.originalCurrency), it.manualFxOverride) }
        .forEach { (key, grouped) ->
            val rate = repository.getRate(key.first, key.second, key.third) ?: return@forEach
            if (rate.units <= 0 || rate.rateToGel.signum() <= 0) return@forEach
            grouped.forEach { entry ->
                val converted = entry.originalAmount.multiply(rate.rateToGel)
                    .divide(BigDecimal(rate.units), 2, RoundingMode.HALF_UP)
                if (converted.compareTo(entry.gelEquivalent) == 0) result[entry.id] = rate
            }
        }
    return result
}
