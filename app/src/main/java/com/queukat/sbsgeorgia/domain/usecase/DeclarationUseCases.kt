package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
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
import kotlinx.coroutines.flow.map

class ObserveAllSnapshotsUseCase
@Inject
constructor(private val observeState: ObserveDeclarationStateUseCase) {
    operator fun invoke(): Flow<List<MonthlyDeclarationSnapshot>> = observeState.project { it.snapshots }

    suspend fun current(): List<MonthlyDeclarationSnapshot> = observeState.current().snapshots
}

data class MonthDetailReadModel(
    val snapshot: MonthlyDeclarationSnapshot?,
    val entries: List<IncomeEntry>,
    val registrationId: String?,
    val taxpayerName: String?,
    val formConfig: DeclarationFormConfig,
    val referenceDate: LocalDate
)

class ObserveMonthDetailUseCase
@Inject
constructor(private val observeState: ObserveDeclarationStateUseCase) {
    fun full(yearMonth: YearMonth): Flow<MonthDetailReadModel> = observeState.project { state ->
        val snapshot = state.snapshots.firstOrNull { it.period.incomeMonth == yearMonth }
        // Totals, entries, profile and copy-field configuration come from one Room snapshot.
        val entries = state.data.entries.filter { YearMonth.from(it.incomeDate) == yearMonth }
            .sortedWith(compareByDescending<IncomeEntry> { it.incomeDate }.thenByDescending { it.id })
        MonthDetailReadModel(
            snapshot = snapshot,
            entries = entries,
            registrationId = state.data.profile?.registrationId,
            taxpayerName = state.data.profile?.displayName,
            formConfig = state.data.formConfig,
            referenceDate = state.date
        )
    }

    operator fun invoke(yearMonth: YearMonth): Flow<Pair<MonthlyDeclarationSnapshot?, List<IncomeEntry>>> =
        full(yearMonth).map { it.snapshot to it.entries }
}

class ObserveDashboardSummaryUseCase
@Inject
constructor(
    private val observeState: ObserveDeclarationStateUseCase,
    private val planner: MonthlyDeclarationPlanner
) {
    operator fun invoke(): Flow<DashboardSummary> = observeState.project(::buildSummary)

    fun withDeclarationState(): Flow<Pair<DashboardSummary, DeclarationState>> = observeState.project { state ->
        buildSummary(state) to state
    }

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
        reminders: ReminderConfig,
        formConfig: DeclarationFormConfig? = null
    ) {
        settingsRepository.upsertSettings(profile, config, reminders, formConfig)
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
