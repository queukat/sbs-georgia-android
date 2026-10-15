package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import com.queukat.sbsgeorgia.domain.model.normalizeCurrencyCode
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentYearSnapshotsUseCase
@Inject
constructor(private val observeState: ObserveDeclarationStateUseCase) {
    operator fun invoke(year: Int? = null): Flow<List<MonthlyDeclarationSnapshot>> = observeState.project { state ->
        state.snapshots.filter { it.period.incomeMonth.year == (year ?: state.date.year) }
    }
}

class ObserveAllSnapshotsUseCase
@Inject
constructor(private val observeState: ObserveDeclarationStateUseCase) {
    operator fun invoke(): Flow<List<MonthlyDeclarationSnapshot>> = observeState.project { it.snapshots }

    suspend fun current(): List<MonthlyDeclarationSnapshot> = observeState.current().snapshots
}

class ObserveMonthDetailUseCase
@Inject
constructor(private val observeState: ObserveDeclarationStateUseCase) {
    operator fun invoke(yearMonth: YearMonth): Flow<Pair<MonthlyDeclarationSnapshot?, List<IncomeEntry>>> =
        observeState.project { state ->
            val snapshot = state.snapshots.firstOrNull { it.period.incomeMonth == yearMonth }
            // Total and visible rows must always describe the same database revision.
            val entries = state.data.entries.filter { YearMonth.from(it.incomeDate) == yearMonth }
                .sortedWith(compareByDescending<IncomeEntry> { it.incomeDate }.thenByDescending { it.id })
            snapshot to entries
        }
}

class ObserveDashboardSummaryUseCase
@Inject
constructor(
    private val observeState: ObserveDeclarationStateUseCase,
    private val planner: MonthlyDeclarationPlanner
) {
    operator fun invoke(): Flow<DashboardSummary> = observeState.project(::buildSummary)

    suspend fun current(): DashboardSummary = buildSummary(observeState.current())

    private fun buildSummary(state: DeclarationState): DashboardSummary = planner.buildDashboardSummary(
        state.data.profile,
        state.data.config,
        state.data.reminders,
        state.snapshots,
        state.data.records,
        state.date
    )
}

class UpsertManualIncomeEntryUseCase
@Inject
constructor(private val incomeRepository: IncomeRepository) {
    suspend operator fun invoke(entry: IncomeEntry): Long {
        require(entry.originalAmount.signum() > 0) { "Income amount must be positive." }
        val normalizedCurrency = normalizeCurrencyCode(entry.originalCurrency)
        require(isIsoLikeCurrencyCode(normalizedCurrency)) {
            "Manual income entries must use a valid 3-letter currency code."
        }
        return incomeRepository.upsert(
            entry.copy(originalCurrency = normalizedCurrency)
        )
    }
}

class UpsertSettingsUseCase
@Inject
constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(
        profile: TaxpayerProfile,
        config: SmallBusinessStatusConfig,
        reminders: ReminderConfig
    ) {
        settingsRepository.upsertSettings(profile, config, reminders)
    }
}

class UpsertMonthlyDeclarationRecordUseCase
@Inject
constructor(
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository
) {
    suspend operator fun invoke(record: MonthlyDeclarationRecord) {
        monthlyDeclarationRepository.upsert(record)
    }
}

internal fun collectRelevantSnapshotYears(
    clock: Clock,
    config: SmallBusinessStatusConfig?,
    entries: List<IncomeEntry>,
    records: List<MonthlyDeclarationRecord>,
    referenceDate: LocalDate = LocalDate.now(clock)
): List<Int> = buildSet {
    val currentYear = referenceDate.year
    val earliestYear = listOfNotNull(
        config?.effectiveDate?.year,
        entries.minOfOrNull { it.incomeDate.year },
        records.minOfOrNull { it.yearMonth.year },
        currentYear
    ).min()
    addAll(earliestYear..currentYear)
    // The previous due year must exist even when no December income has been imported.
    add(YearMonth.from(referenceDate).minusMonths(1).year)
    entries.mapTo(this) { it.incomeDate.year }
    records.mapTo(this) { it.yearMonth.year }
}.sortedDescending()
