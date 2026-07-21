package com.queukat.sbsgeorgia.ui

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveCurrentYearSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import com.queukat.sbsgeorgia.testing.MainDispatcherRule
import com.queukat.sbsgeorgia.ui.home.HomeViewModel
import com.queukat.sbsgeorgia.ui.months.MonthsUiState
import com.queukat.sbsgeorgia.ui.months.MonthsViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickSettleGatingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun unresolvedFxBlocksQuickSettleInHomeAndMonths() = runTest {
        val fixture =
            QuickSettleFixture(
                entries =
                listOf(
                    incomeEntry(
                        currency = "USD",
                        gelEquivalent = null
                    )
                )
            )
        val homeViewModel = fixture.homeViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val homeQuickAccess = homeViewModel.uiState.value.duePeriodQuickAccess
        assertFalse(homeQuickAccess?.canQuickSettleMonth ?: true)
        assertFalse(homeQuickAccess?.canCopyDeclarationValues ?: true)
        homeViewModel.settleCurrentDuePeriod()
        advanceUntilIdle()
        assertTrue(fixture.monthlyDeclarationRepository.savedRecords.isEmpty())

        val monthsViewModel = fixture.monthsViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monthsViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(monthsViewModel.uiState.value.monthItem(MARCH_2026).canQuickSettleMonth)
        monthsViewModel.settleMonth(MARCH_2026)
        advanceUntilIdle()
        assertTrue(fixture.monthlyDeclarationRepository.savedRecords.isEmpty())
    }

    @Test
    fun reviewNeededEntriesBlockQuickSettleInHomeAndMonths() = runTest {
        val fixture =
            QuickSettleFixture(
                entries =
                listOf(
                    incomeEntry(
                        inclusion = DeclarationInclusion.REVIEW_REQUIRED,
                        gelEquivalent = BigDecimal("100.00")
                    )
                )
            )
        val homeViewModel = fixture.homeViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val homeQuickAccess = homeViewModel.uiState.value.duePeriodQuickAccess
        assertFalse(homeQuickAccess?.canQuickSettleMonth ?: true)
        assertFalse(homeQuickAccess?.canCopyDeclarationValues ?: true)
        homeViewModel.settleCurrentDuePeriod()
        advanceUntilIdle()
        assertTrue(fixture.monthlyDeclarationRepository.savedRecords.isEmpty())

        val monthsViewModel = fixture.monthsViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monthsViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(monthsViewModel.uiState.value.monthItem(MARCH_2026).canQuickSettleMonth)
        monthsViewModel.settleMonth(MARCH_2026)
        advanceUntilIdle()
        assertTrue(fixture.monthlyDeclarationRepository.savedRecords.isEmpty())
    }

    @Test
    fun safeMonthAllowsQuickSettleInHomeAndMonths() = runTest {
        val fixture =
            QuickSettleFixture(
                entries =
                listOf(
                    incomeEntry(
                        currency = "GEL",
                        gelEquivalent = BigDecimal("100.00")
                    )
                )
            )
        val homeViewModel = fixture.homeViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val homeQuickAccess = homeViewModel.uiState.value.duePeriodQuickAccess
        assertTrue(homeQuickAccess?.canQuickSettleMonth ?: false)
        assertTrue(homeQuickAccess?.canCopyDeclarationValues ?: false)

        val monthsViewModel = fixture.monthsViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monthsViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(monthsViewModel.uiState.value.monthItem(MARCH_2026).canQuickSettleMonth)
    }

    @Test
    fun sameSafeSnapshotHasMatchingQuickSettleReadinessAcrossHomeAndMonths() = runTest {
        val fixture =
            QuickSettleFixture(
                entries = listOf(incomeEntry(currency = "GEL", gelEquivalent = BigDecimal("100.00")))
            )
        val homeViewModel = fixture.homeViewModel()
        val monthsViewModel = fixture.monthsViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { homeViewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monthsViewModel.uiState.collect {} }
        advanceUntilIdle()

        val home = requireNotNull(homeViewModel.uiState.value.duePeriodQuickAccess)
        val months = monthsViewModel.uiState.value.monthItem(MARCH_2026)
        assertTrue(home.canCopyDeclarationValues)
        assertEquals(home.canQuickSettleMonth, months.canQuickSettleMonth)
    }
}

private class QuickSettleFixture(entries: List<IncomeEntry>) {
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-04-10T00:00:00Z"), ZoneOffset.UTC)
    private val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
    private val actionPlanner = MonthlyDeclarationActionPlanner(clock)
    private val settingsRepository = FakeSettingsRepository()
    private val incomeRepository = FakeIncomeRepository(entries)
    val monthlyDeclarationRepository = FakeMonthlyDeclarationRepository()

    fun homeViewModel(): HomeViewModel {
        val observeCurrentYearSnapshotsUseCase =
            ObserveCurrentYearSnapshotsUseCase(
                settingsRepository = settingsRepository,
                incomeRepository = incomeRepository,
                monthlyDeclarationRepository = monthlyDeclarationRepository,
                planner = planner,
                clock = clock
            )
        return HomeViewModel(
            observeDashboardSummaryUseCase =
            ObserveDashboardSummaryUseCase(
                settingsRepository = settingsRepository,
                monthlyDeclarationRepository = monthlyDeclarationRepository,
                observeCurrentYearSnapshotsUseCase = observeCurrentYearSnapshotsUseCase,
                planner = planner
            ),
            upsertMonthlyDeclarationRecordUseCase =
            UpsertMonthlyDeclarationRecordUseCase(monthlyDeclarationRepository),
            actionPlanner = actionPlanner,
            clock = clock
        )
    }

    fun monthsViewModel(): MonthsViewModel = MonthsViewModel(
        observeAllSnapshotsUseCase =
        ObserveAllSnapshotsUseCase(
            settingsRepository = settingsRepository,
            incomeRepository = incomeRepository,
            monthlyDeclarationRepository = monthlyDeclarationRepository,
            planner = planner,
            clock = clock
        ),
        upsertMonthlyDeclarationRecordUseCase =
        UpsertMonthlyDeclarationRecordUseCase(monthlyDeclarationRepository),
        actionPlanner = actionPlanner,
        clock = clock
    )

}

private class FakeSettingsRepository : SettingsRepository {
    private val taxpayerProfile =
        MutableStateFlow(
            TaxpayerProfile(
                registrationId = "123456789",
                displayName = "Test taxpayer"
            )
        )
    private val statusConfig =
        MutableStateFlow(
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.of(2026, 1, 1),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        )
    private val reminderConfig =
        MutableStateFlow(
            ReminderConfig(
                declarationReminderDays = listOf(10, 13, 15),
                paymentReminderDays = listOf(10, 13, 15),
                declarationRemindersEnabled = true,
                paymentRemindersEnabled = true,
                defaultReminderTime = LocalTime.of(9, 0),
                themeMode = ThemeMode.SYSTEM
            )
        )

    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> = taxpayerProfile

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = statusConfig

    override fun observeReminderConfig(): Flow<ReminderConfig?> = reminderConfig

    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) {
        taxpayerProfile.value = profile
    }

    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) {
        statusConfig.value = config
    }

    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        reminderConfig.value = config
    }
}

private class FakeIncomeRepository(initialEntries: List<IncomeEntry>) : IncomeRepository {
    private val entries = MutableStateFlow(initialEntries)

    override fun observeAll(): Flow<List<IncomeEntry>> = entries

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = entries.map { allEntries ->
        allEntries.filter { YearMonth.from(it.incomeDate) == yearMonth }
    }

    override suspend fun getById(id: Long): IncomeEntry? = entries.value.firstOrNull { it.id == id }

    override suspend fun upsert(entry: IncomeEntry): Long {
        val id = entry.id.takeIf { it != 0L } ?: 1L
        entries.value =
            entries.value.filterNot { it.id == id } +
            entry.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }
}

private class FakeMonthlyDeclarationRepository : MonthlyDeclarationRepository {
    private val records = MutableStateFlow<List<MonthlyDeclarationRecord>>(emptyList())
    val savedRecords = mutableListOf<MonthlyDeclarationRecord>()

    override fun observeAll(): Flow<List<MonthlyDeclarationRecord>> = records

    override fun observeByMonth(yearMonth: YearMonth): Flow<MonthlyDeclarationRecord?> = records.map { allRecords ->
        allRecords.firstOrNull { it.yearMonth == yearMonth }
    }

    override suspend fun upsert(record: MonthlyDeclarationRecord) {
        savedRecords += record
        records.value =
            records.value.filterNot { it.yearMonth == record.yearMonth } +
            record
    }
}

private fun MonthsUiState.monthItem(yearMonth: YearMonth) = sections
    .flatMap { it.items }
    .single { it.snapshot.period.incomeMonth == yearMonth }

private fun incomeEntry(
    inclusion: DeclarationInclusion = DeclarationInclusion.INCLUDED,
    currency: String = "GEL",
    gelEquivalent: BigDecimal? = BigDecimal("100.00")
): IncomeEntry = IncomeEntry(
    id = 1L,
    sourceType = IncomeSourceType.MANUAL,
    incomeDate = LocalDate.of(2026, 3, 10),
    originalAmount = BigDecimal("100.00"),
    originalCurrency = currency,
    sourceCategory = "Software services",
    note = "",
    declarationInclusion = inclusion,
    gelEquivalent = gelEquivalent,
    rateSource = FxRateSource.NONE,
    manualFxOverride = false,
    createdAtEpochMillis = 1L,
    updatedAtEpochMillis = 1L
)

private val MARCH_2026: YearMonth = YearMonth.of(2026, 3)
