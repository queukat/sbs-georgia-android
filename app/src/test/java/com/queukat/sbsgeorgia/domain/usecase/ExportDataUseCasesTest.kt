package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDataUseCasesTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun exportIncomeEntriesCsvEscapesFieldsAndKeepsImportedMetadataColumns() = runTest {
        val csv = ExportIncomeEntriesCsvUseCase(
            incomeRepository = FakeExportIncomeRepository(
                listOf(
                    IncomeEntry(
                        id = 7L,
                        sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                        incomeDate = LocalDate.of(2026, 3, 15),
                        originalAmount = BigDecimal("125.50"),
                        originalCurrency = "USD",
                        sourceCategory = "Software, services",
                        note = "Invoice \"A-1\"",
                        declarationInclusion = DeclarationInclusion.INCLUDED,
                        gelEquivalent = BigDecimal("338.85"),
                        rateSource = FxRateSource.OFFICIAL_NBG_JSON,
                        manualFxOverride = false,
                        sourceStatementId = 3L,
                        sourceTransactionFingerprint = "tx-fingerprint",
                        createdAtEpochMillis = 100L,
                        updatedAtEpochMillis = 200L,
                    ),
                ),
            ),
        ).invoke()

        assertTrue(csv.contains("\"sourceStatementId\""))
        assertTrue(csv.contains("\"Software, services\""))
        assertTrue(csv.contains("\"Invoice \"\"A-1\"\"\""))
        assertTrue(csv.contains("\"tx-fingerprint\""))
    }

    @Test
    fun exportMonthlySummariesCsvIncludesWorkflowAndUnresolvedFxColumns() = runTest {
        val settingsRepository = FakeExportSettingsRepository(
            profile = TaxpayerProfile(
                registrationId = "306449082",
                displayName = "Jane Doe",
            ),
            config = SmallBusinessStatusConfig(
                effectiveDate = LocalDate.of(2026, 1, 1),
                defaultTaxRatePercent = BigDecimal("1.0"),
            ),
            reminders = ReminderConfig(
                declarationReminderDays = listOf(10, 13, 15),
                paymentReminderDays = listOf(10, 13, 15),
                declarationRemindersEnabled = true,
                paymentRemindersEnabled = true,
                defaultReminderTime = LocalTime.of(9, 0),
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        val incomeRepository = FakeExportIncomeRepository(
            listOf(
                sampleIncomeEntry(
                    id = 1L,
                    date = LocalDate.of(2026, 1, 10),
                    amount = "100.00",
                    currency = "GEL",
                    gelEquivalent = BigDecimal("100.00"),
                    rateSource = FxRateSource.NONE,
                ),
                sampleIncomeEntry(
                    id = 2L,
                    date = LocalDate.of(2026, 2, 5),
                    amount = "50.00",
                    currency = "USD",
                    gelEquivalent = null,
                    rateSource = FxRateSource.NONE,
                ),
            ),
        )
        val recordRepository = FakeExportMonthlyDeclarationRepository(
            listOf(
                MonthlyDeclarationRecord(
                    yearMonth = YearMonth.of(2026, 2),
                    workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                    zeroDeclarationPrepared = false,
                ),
            ),
        )

        val csv = ExportMonthlySummariesCsvUseCase(
            settingsRepository = settingsRepository,
            incomeRepository = incomeRepository,
            monthlyDeclarationRepository = recordRepository,
            planner = MonthlyDeclarationPlanner(fixedClock, com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar()),
            clock = fixedClock,
        ).invoke()

        assertTrue(csv.contains("\"workflowStatus\""))
        assertTrue(csv.contains("\"2026-02\""))
        assertTrue(csv.contains("\"OVERDUE\""))
        assertTrue(csv.contains("\"1\""))
    }

    @Test
    fun chartMappingSortsSnapshotsChronologically() {
        val points = chartCumulativePoints(
            listOf(
                sampleSnapshot(YearMonth.of(2026, 3), "150.00"),
                sampleSnapshot(YearMonth.of(2026, 1), "100.00"),
                sampleSnapshot(YearMonth.of(2026, 2), "120.00"),
            ),
        )

        val labelFormatter = DateTimeFormatter.ofPattern("LLL", Locale.getDefault())
        assertEquals(
            listOf(
                YearMonth.of(2026, 1).atDay(1).format(labelFormatter).uppercase(Locale.getDefault()),
                YearMonth.of(2026, 2).atDay(1).format(labelFormatter).uppercase(Locale.getDefault()),
                YearMonth.of(2026, 3).atDay(1).format(labelFormatter).uppercase(Locale.getDefault()),
            ),
            points.map { it.label },
        )
        assertEquals(listOf("100.00", "120.00", "150.00"), points.map { it.value.toPlainString() })
    }

    private fun sampleIncomeEntry(
        id: Long,
        date: LocalDate,
        amount: String,
        currency: String,
        gelEquivalent: BigDecimal?,
        rateSource: FxRateSource,
    ): IncomeEntry = IncomeEntry(
        id = id,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = date,
        originalAmount = BigDecimal(amount),
        originalCurrency = currency,
        sourceCategory = "Software services",
        note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = gelEquivalent,
        rateSource = rateSource,
        manualFxOverride = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun sampleSnapshot(yearMonth: YearMonth, cumulative: String): MonthlyDeclarationSnapshot =
        MonthlyDeclarationSnapshot(
            period = MonthlyDeclarationPeriod(
                incomeMonth = yearMonth,
                filingWindow = FilingWindow(
                    start = yearMonth.plusMonths(1).atDay(1),
                    endInclusive = yearMonth.plusMonths(1).atDay(15),
                    dueDate = yearMonth.plusMonths(1).atDay(15),
                ),
                inScope = true,
                outOfScope = false,
            ),
            workflowStatus = MonthlyWorkflowStatus.DRAFT,
            graph20TotalGel = BigDecimal("50.00"),
            graph15CumulativeGel = BigDecimal(cumulative),
            originalCurrencyTotals = emptyList(),
            estimatedTaxAmountGel = BigDecimal("0.50"),
            unresolvedFxCount = 0,
            zeroDeclarationSuggested = false,
            zeroDeclarationPrepared = false,
            reviewNeeded = false,
            setupRequired = false,
            record = null,
        )
}

private class FakeExportSettingsRepository(
    profile: TaxpayerProfile?,
    config: SmallBusinessStatusConfig?,
    reminders: ReminderConfig?,
) : SettingsRepository {
    private val profileState = MutableStateFlow(profile)
    private val configState = MutableStateFlow(config)
    private val reminderState = MutableStateFlow(reminders)

    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> = profileState

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = configState

    override fun observeReminderConfig(): Flow<ReminderConfig?> = reminderState

    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) {
        profileState.value = profile
    }

    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) {
        configState.value = config
    }

    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        reminderState.value = config
    }
}

private class FakeExportIncomeRepository(
    entries: List<IncomeEntry>,
) : IncomeRepository {
    private val state = MutableStateFlow(entries)

    override fun observeAll(): Flow<List<IncomeEntry>> = state

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = state.map { items ->
        items.filter { YearMonth.from(it.incomeDate) == yearMonth }
    }

    override suspend fun getById(id: Long): IncomeEntry? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(entry: IncomeEntry): Long = entry.id

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class FakeExportMonthlyDeclarationRepository(
    records: List<MonthlyDeclarationRecord>,
) : MonthlyDeclarationRepository {
    private val state = MutableStateFlow(records)

    override fun observeAll(): Flow<List<MonthlyDeclarationRecord>> = state

    override fun observeByMonth(yearMonth: YearMonth): Flow<MonthlyDeclarationRecord?> =
        flowOf(state.value.firstOrNull { it.yearMonth == yearMonth })

    override suspend fun upsert(record: MonthlyDeclarationRecord) {
        state.value = state.value.filterNot { it.yearMonth == record.yearMonth } + record
    }
}
