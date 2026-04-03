package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveCurrentYearSnapshotsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val incomeRepository: IncomeRepository,
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
) {
    operator fun invoke(year: Int = YearMonth.now(clock).year): Flow<List<MonthlyDeclarationSnapshot>> =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            incomeRepository.observeAll(),
            monthlyDeclarationRepository.observeAll(),
        ) { profile: TaxpayerProfile?, config: SmallBusinessStatusConfig?, entries: List<IncomeEntry>, records: List<MonthlyDeclarationRecord> ->
            planner.buildYearSnapshots(year, profile, config, entries, records)
        }
}

class ObserveAllSnapshotsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val incomeRepository: IncomeRepository,
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<MonthlyDeclarationSnapshot>> =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            incomeRepository.observeAll(),
            monthlyDeclarationRepository.observeAll(),
        ) { profile: TaxpayerProfile?, config: SmallBusinessStatusConfig?, entries: List<IncomeEntry>, records: List<MonthlyDeclarationRecord> ->
            collectRelevantSnapshotYears(
                clock = clock,
                config = config,
                entries = entries,
                records = records,
            ).flatMap { year ->
                planner.buildYearSnapshots(year, profile, config, entries, records)
            }
        }
}

class ObserveMonthDetailUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val incomeRepository: IncomeRepository,
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
    private val planner: MonthlyDeclarationPlanner,
) {
    operator fun invoke(yearMonth: YearMonth): Flow<Pair<MonthlyDeclarationSnapshot?, List<IncomeEntry>>> =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            incomeRepository.observeAll(),
            incomeRepository.observeByMonth(yearMonth),
            monthlyDeclarationRepository.observeAll(),
        ) { profile: TaxpayerProfile?, config: SmallBusinessStatusConfig?, allEntries: List<IncomeEntry>, monthEntries: List<IncomeEntry>, records: List<MonthlyDeclarationRecord> ->
            val snapshot = planner.buildYearSnapshots(
                year = yearMonth.year,
                profile = profile,
                config = config,
                entries = allEntries,
                records = records,
            ).firstOrNull { it.period.incomeMonth == yearMonth }
            snapshot to monthEntries.sortedWith(compareByDescending<IncomeEntry> { it.incomeDate }.thenByDescending { it.id })
        }
}

class ObserveDashboardSummaryUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val observeCurrentYearSnapshotsUseCase: ObserveCurrentYearSnapshotsUseCase,
    private val planner: MonthlyDeclarationPlanner,
) {
    operator fun invoke(): Flow<DashboardSummary> =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            settingsRepository.observeReminderConfig(),
            observeCurrentYearSnapshotsUseCase(),
        ) { profile: TaxpayerProfile?, config: SmallBusinessStatusConfig?, reminders: ReminderConfig?, snapshots: List<MonthlyDeclarationSnapshot> ->
            planner.buildDashboardSummary(profile, config, reminders, snapshots)
        }
}

class UpsertManualIncomeEntryUseCase @Inject constructor(
    private val incomeRepository: IncomeRepository,
) {
    suspend operator fun invoke(entry: IncomeEntry): Long = incomeRepository.upsert(entry)
}

class UpsertSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        profile: TaxpayerProfile,
        config: SmallBusinessStatusConfig,
        reminders: ReminderConfig,
    ) {
        settingsRepository.upsertTaxpayerProfile(profile)
        settingsRepository.upsertStatusConfig(config)
        settingsRepository.upsertReminderConfig(reminders)
    }
}

class UpsertMonthlyDeclarationRecordUseCase @Inject constructor(
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
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
): List<Int> = buildSet {
    add(YearMonth.now(clock).year)
    config?.effectiveDate?.year?.let(::add)
    entries.mapTo(this) { it.incomeDate.year }
    records.mapTo(this) { it.yearMonth.year }
}.sortedDescending()
