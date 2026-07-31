package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyDeclarationPlannerTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC)
    private val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
    private val profile =
        TaxpayerProfile(
            registrationId = "123456789",
            displayName = "Test taxpayer"
        )

    @Test
    fun `buildYearSnapshots keeps cumulative across zero months`() {
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-01-01"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            planner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-01-12", "100.00"),
                    manualEntry("2026-03-03", "50.00")
                ),
                records = emptyList()
            )

        assertEquals(BigDecimal("100.00"), snapshots[0].graph20TotalGel)
        assertEquals(BigDecimal("100.00"), snapshots[0].graph15CumulativeGel)
        assertTrue(snapshots[1].zeroDeclarationSuggested)
        assertEquals(BigDecimal("100.00"), snapshots[1].graph15CumulativeGel)
        assertEquals(BigDecimal("50.00"), snapshots[2].graph20TotalGel)
        assertEquals(BigDecimal("150.00"), snapshots[2].graph15CumulativeGel)
        assertEquals(BigDecimal("150.00"), snapshots[3].graph15CumulativeGel)
    }

    @Test
    fun `declarationPeriodFor uses next month filing window and keeps declaration start on first day`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = java.time.YearMonth.parse("2026-03"),
                config = null
            )

        assertEquals(LocalDate.parse("2026-04-01"), period.filingWindow.start)
        assertEquals(LocalDate.parse("2026-04-15"), period.filingWindow.endInclusive)
        assertEquals(LocalDate.parse("2026-04-15"), period.filingWindow.dueDate)
    }

    @Test
    fun `declarationPeriodFor moves weekend due date to next business day`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = java.time.YearMonth.parse("2026-01"),
                config = null
            )

        assertEquals(LocalDate.parse("2026-02-01"), period.filingWindow.start)
        assertEquals(LocalDate.parse("2026-02-15"), period.filingWindow.endInclusive)
        assertEquals(LocalDate.parse("2026-02-16"), period.filingWindow.dueDate)
    }

    @Test
    fun `months before effective date are excluded and same-month pre-effective income is flagged`() {
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-03-15"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            planner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-02-10", "80.00"),
                    manualEntry("2026-03-10", "10.00"),
                    manualEntry("2026-03-20", "20.00")
                ),
                records = emptyList()
            )

        assertTrue(snapshots[1].period.outOfScope)
        assertEquals(BigDecimal("0.00"), snapshots[1].graph20TotalGel)
        assertEquals(BigDecimal("20.00"), snapshots[2].graph20TotalGel)
        assertEquals(BigDecimal("20.00"), snapshots[2].graph15CumulativeGel)
        assertTrue(snapshots[2].reviewNeeded)
    }

    @Test
    fun `non gel entries stay unresolved until conversion exists`() {
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-01-01"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            planner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-01-05", "100.00", currency = "USD"),
                    manualEntry("2026-01-06", "50.00")
                ),
                records = emptyList()
            )

        assertEquals(1, snapshots[0].unresolvedFxCount)
        assertEquals(BigDecimal("50.00"), snapshots[0].graph20TotalGel)
        assertEquals(BigDecimal("0.50"), snapshots[0].estimatedTaxAmountGel)
    }

    @Test
    fun `draft period becomes overdue after filing due date`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = java.time.YearMonth.parse("2026-03"),
                config = null
            )

        val status =
            planner.deriveWorkflowStatus(
                baseStatus = MonthlyWorkflowStatus.DRAFT,
                period = period,
                referenceDate = LocalDate.parse("2026-04-20")
            )

        assertEquals(MonthlyWorkflowStatus.OVERDUE, status)
    }

    @Test
    fun `due date itself is not overdue and filing window opens on its first day`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = YearMonth.parse("2026-03"),
                config = null
            )

        assertTrue(planner.isFilingWindowOpen(period, LocalDate.parse("2026-04-01")))
        assertFalse(planner.isFilingWindowOpen(period, LocalDate.parse("2026-03-31")))
        assertEquals(
            MonthlyWorkflowStatus.DRAFT,
            planner.deriveWorkflowStatus(
                baseStatus = MonthlyWorkflowStatus.DRAFT,
                period = period,
                referenceDate = LocalDate.parse("2026-04-15")
            )
        )
    }

    @Test
    fun `only incomplete filing and payment states become overdue`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = YearMonth.parse("2026-03"),
                config = null
            )
        val afterDueDate = LocalDate.parse("2026-04-16")

        assertEquals(
            MonthlyWorkflowStatus.OVERDUE,
            planner.deriveWorkflowStatus(MonthlyWorkflowStatus.FILED, period, afterDueDate)
        )
        assertEquals(
            MonthlyWorkflowStatus.OVERDUE,
            planner.deriveWorkflowStatus(MonthlyWorkflowStatus.TAX_PAYMENT_PENDING, period, afterDueDate)
        )
        assertEquals(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            planner.deriveWorkflowStatus(MonthlyWorkflowStatus.PAYMENT_SENT, period, afterDueDate)
        )
        assertEquals(
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            planner.deriveWorkflowStatus(MonthlyWorkflowStatus.PAYMENT_CREDITED, period, afterDueDate)
        )
        assertEquals(
            MonthlyWorkflowStatus.SETTLED,
            planner.deriveWorkflowStatus(MonthlyWorkflowStatus.SETTLED, period, afterDueDate)
        )
    }

    @Test
    fun `zero declaration is suggested only when in-scope month has no included or review rows`() {
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-01-01"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            planner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-01-05", "20.00").copy(
                        declarationInclusion = DeclarationInclusion.EXCLUDED
                    ),
                    manualEntry("2026-02-05", "20.00").copy(
                        declarationInclusion = DeclarationInclusion.REVIEW_REQUIRED
                    )
                ),
                records = emptyList()
            )

        assertTrue(snapshots[0].zeroDeclarationSuggested)
        assertFalse(snapshots[1].zeroDeclarationSuggested)
        assertTrue(snapshots[1].reviewNeeded)
    }

    @Test
    fun `unfiled zero income month remains overdue after its due date`() {
        val latePlanner = plannerAfterMayDueDate()
        val config = statusEffectiveFromMay()
        val snapshots =
            latePlanner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries = emptyList(),
                records = emptyList()
            )

        val maySnapshot = snapshots.single { it.period.incomeMonth == YearMonth.of(2026, 5) }
        val summary =
            latePlanner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders = null,
                snapshots = snapshots,
                records = emptyList()
            )

        assertEquals(BigDecimal("0.00"), maySnapshot.estimatedTaxAmountGel)
        assertTrue(maySnapshot.zeroDeclarationSuggested)
        assertEquals(MonthlyWorkflowStatus.OVERDUE, maySnapshot.workflowStatus)
        assertEquals(1, summary.unsettledMonthsCount)
    }

    @Test
    fun `filed zero income month is not overdue or unsettled after its due date`() {
        val latePlanner = plannerAfterMayDueDate()
        val config = statusEffectiveFromMay()
        val filedMayRecord =
            MonthlyDeclarationRecord(
                yearMonth = YearMonth.of(2026, 5),
                workflowStatus = MonthlyWorkflowStatus.FILED,
                zeroDeclarationPrepared = true,
                declarationFiledDate = LocalDate.of(2026, 6, 10)
            )
        val snapshots =
            latePlanner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries = emptyList(),
                records = listOf(filedMayRecord)
            )

        val maySnapshot = snapshots.single { it.period.incomeMonth == YearMonth.of(2026, 5) }
        val summary =
            latePlanner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders = null,
                snapshots = snapshots,
                records = listOf(filedMayRecord)
            )

        assertEquals(BigDecimal("0.00"), maySnapshot.estimatedTaxAmountGel)
        assertTrue(maySnapshot.zeroDeclarationPrepared)
        assertEquals(MonthlyWorkflowStatus.FILED, maySnapshot.workflowStatus)
        assertEquals(0, summary.unsettledMonthsCount)
    }

    @Test
    fun `payment sent does not become overdue after due date`() {
        val period =
            planner.declarationPeriodFor(
                incomeMonth = java.time.YearMonth.parse("2026-03"),
                config = null
            )

        val status =
            planner.deriveWorkflowStatus(
                baseStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                period = period,
                referenceDate = LocalDate.parse("2026-04-20")
            )

        assertEquals(MonthlyWorkflowStatus.PAYMENT_SENT, status)
        assertFalse(status == MonthlyWorkflowStatus.OVERDUE)
    }

    @Test
    fun `snapshot flags tax payment mismatch when paid amount differs from estimate`() {
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-01-01"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            planner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-01-05", "5000.00")
                ),
                records =
                listOf(
                    MonthlyDeclarationRecord(
                        yearMonth = YearMonth.parse("2026-01"),
                        workflowStatus = MonthlyWorkflowStatus.SETTLED,
                        zeroDeclarationPrepared = false,
                        declarationFiledDate = LocalDate.parse("2026-02-10"),
                        paymentSentDate = LocalDate.parse("2026-02-10"),
                        paymentCreditedDate = LocalDate.parse("2026-02-10"),
                        paymentAmountGel = BigDecimal("10.00")
                    )
                )
            )
        val januarySnapshot = snapshots.first()
        val summary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders = null,
                snapshots = snapshots,
                records =
                listOf(
                    MonthlyDeclarationRecord(
                        yearMonth = YearMonth.parse("2026-01"),
                        workflowStatus = MonthlyWorkflowStatus.SETTLED,
                        zeroDeclarationPrepared = false,
                        declarationFiledDate = LocalDate.parse("2026-02-10"),
                        paymentSentDate = LocalDate.parse("2026-02-10"),
                        paymentCreditedDate = LocalDate.parse("2026-02-10"),
                        paymentAmountGel = BigDecimal("10.00")
                    )
                )
            )

        assertTrue(januarySnapshot.taxPaymentMismatch)
        assertTrue(januarySnapshot.taxPaymentUnderpaid)
        assertEquals(BigDecimal("-40.00"), januarySnapshot.taxPaymentDifferenceGel)
        assertEquals(1, summary.paymentMismatchMonthsCount)
    }

    @Test
    fun `dashboard summary does not count current income month before filing window opens`() {
        val preWindowPlanner =
            MonthlyDeclarationPlanner(
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC),
                GeorgiaTaxBusinessCalendar()
            )
        val config =
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.parse("2026-03-01"),
                defaultTaxRatePercent = BigDecimal("1.0")
            )
        val snapshots =
            preWindowPlanner.buildYearSnapshots(
                year = 2026,
                profile = profile,
                config = config,
                entries =
                listOf(
                    manualEntry("2026-03-10", "250.00")
                ),
                records = emptyList()
            )

        val summary =
            preWindowPlanner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders = null,
                snapshots = snapshots,
                records = emptyList()
            )

        assertEquals(0, summary.unsettledMonthsCount)
    }

    @Test
    fun `dashboard summary hides next reminder when all reminders are disabled`() {
        val summary =
            planner.buildDashboardSummary(
                profile = profile,
                config =
                SmallBusinessStatusConfig(
                    effectiveDate = LocalDate.parse("2026-01-01"),
                    defaultTaxRatePercent = BigDecimal("1.0")
                ),
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = false,
                    paymentRemindersEnabled = false
                ),
                snapshots = emptyList(),
                records = emptyList()
            )

        assertNull(summary.nextReminderDay)
    }

    @Test
    fun `dashboard summary uses next reminder day when declaration reminders are enabled`() {
        val config = activeStatusConfig()
        val upcomingSummary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = true,
                    declarationReminderDays = listOf(5, 25, 10)
                ),
                snapshots = activeMarchSnapshots(config),
                records = emptyList()
            )
        val wrappedSummary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = true,
                    declarationReminderDays = listOf(5, 10, 15)
                ),
                snapshots = activeMarchSnapshots(config),
                records = emptyList()
            )

        assertEquals(25, upcomingSummary.nextReminderDay)
        assertEquals(5, wrappedSummary.nextReminderDay)
    }

    @Test
    fun `dashboard summary uses payment reminder day when only payment reminders are enabled`() {
        val config = activeStatusConfig()
        val summary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = false,
                    paymentRemindersEnabled = true,
                    paymentReminderDays = listOf(5)
                ),
                snapshots = activeMarchSnapshots(config),
                records = emptyList()
            )

        assertEquals(5, summary.nextReminderDay)
    }

    @Test
    fun `dashboard summary chooses earliest applicable declaration or payment reminder day`() {
        val config = activeStatusConfig()
        val summary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = true,
                    declarationReminderDays = listOf(10),
                    paymentRemindersEnabled = true,
                    paymentReminderDays = listOf(5)
                ),
                snapshots = activeMarchSnapshots(config),
                records = emptyList()
            )

        assertEquals(5, summary.nextReminderDay)
    }

    @Test
    fun `dashboard summary hides next reminder when due period is terminal`() {
        val config = activeStatusConfig()
        val settledRecord =
            MonthlyDeclarationRecord(
                yearMonth = YearMonth.of(2026, 3),
                workflowStatus = MonthlyWorkflowStatus.SETTLED,
                zeroDeclarationPrepared = false
            )
        val summary =
            planner.buildDashboardSummary(
                profile = profile,
                config = config,
                reminders =
                reminderConfig(
                    declarationRemindersEnabled = true,
                    declarationReminderDays = listOf(10),
                    paymentRemindersEnabled = true,
                    paymentReminderDays = listOf(5)
                ),
                snapshots = activeMarchSnapshots(config, records = listOf(settledRecord)),
                records = listOf(settledRecord)
            )

        assertNull(summary.nextReminderDay)
    }

    private fun manualEntry(date: String, amount: String, currency: String = "GEL"): IncomeEntry = IncomeEntry(
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = LocalDate.parse(date),
        originalAmount = BigDecimal(amount),
        originalCurrency = currency,
        sourceCategory = "Software services",
        note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = if (currency == "GEL") BigDecimal(amount) else null,
        rateSource = FxRateSource.NONE,
        manualFxOverride = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    private fun reminderConfig(
        declarationRemindersEnabled: Boolean,
        declarationReminderDays: List<Int> = listOf(10, 13, 15),
        paymentRemindersEnabled: Boolean = true,
        paymentReminderDays: List<Int> = listOf(10, 13, 15)
    ): ReminderConfig = ReminderConfig(
        declarationReminderDays = declarationReminderDays,
        paymentReminderDays = paymentReminderDays,
        declarationRemindersEnabled = declarationRemindersEnabled,
        paymentRemindersEnabled = paymentRemindersEnabled,
        defaultReminderTime = LocalTime.of(9, 0),
        themeMode = ThemeMode.SYSTEM
    )

    private fun activeStatusConfig(): SmallBusinessStatusConfig = SmallBusinessStatusConfig(
        effectiveDate = LocalDate.parse("2026-01-01"),
        defaultTaxRatePercent = BigDecimal("1.0")
    )

    private fun statusEffectiveFromMay(): SmallBusinessStatusConfig = SmallBusinessStatusConfig(
        effectiveDate = LocalDate.parse("2026-05-01"),
        defaultTaxRatePercent = BigDecimal("1.0")
    )

    private fun plannerAfterMayDueDate(): MonthlyDeclarationPlanner = MonthlyDeclarationPlanner(
        Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC),
        GeorgiaTaxBusinessCalendar()
    )

    private fun activeMarchSnapshots(
        config: SmallBusinessStatusConfig,
        records: List<MonthlyDeclarationRecord> = emptyList()
    ) = planner.buildYearSnapshots(
        year = 2026,
        profile = profile,
        config = config,
        entries = listOf(manualEntry("2026-03-10", "100.00")),
        records = records
    )
}
