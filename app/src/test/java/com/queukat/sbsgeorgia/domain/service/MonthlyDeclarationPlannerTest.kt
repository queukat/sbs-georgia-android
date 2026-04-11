package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyDeclarationPlannerTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC)
    private val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
    private val profile = TaxpayerProfile(
        registrationId = "306449082",
        displayName = "Test taxpayer",
    )

    @Test
    fun `buildYearSnapshots keeps cumulative across zero months`() {
        val config = SmallBusinessStatusConfig(
            effectiveDate = LocalDate.parse("2026-01-01"),
            defaultTaxRatePercent = BigDecimal("1.0"),
        )
        val snapshots = planner.buildYearSnapshots(
            year = 2026,
            profile = profile,
            config = config,
            entries = listOf(
                manualEntry("2026-01-12", "100.00"),
                manualEntry("2026-03-03", "50.00"),
            ),
            records = emptyList(),
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
        val period = planner.declarationPeriodFor(
            incomeMonth = java.time.YearMonth.parse("2026-03"),
            config = null,
        )

        assertEquals(LocalDate.parse("2026-04-01"), period.filingWindow.start)
        assertEquals(LocalDate.parse("2026-04-15"), period.filingWindow.endInclusive)
        assertEquals(LocalDate.parse("2026-04-15"), period.filingWindow.dueDate)
    }

    @Test
    fun `declarationPeriodFor moves weekend due date to next business day`() {
        val period = planner.declarationPeriodFor(
            incomeMonth = java.time.YearMonth.parse("2026-01"),
            config = null,
        )

        assertEquals(LocalDate.parse("2026-02-01"), period.filingWindow.start)
        assertEquals(LocalDate.parse("2026-02-15"), period.filingWindow.endInclusive)
        assertEquals(LocalDate.parse("2026-02-16"), period.filingWindow.dueDate)
    }

    @Test
    fun `months before effective date are excluded and same-month pre-effective income is flagged`() {
        val config = SmallBusinessStatusConfig(
            effectiveDate = LocalDate.parse("2026-03-15"),
            defaultTaxRatePercent = BigDecimal("1.0"),
        )
        val snapshots = planner.buildYearSnapshots(
            year = 2026,
            profile = profile,
            config = config,
            entries = listOf(
                manualEntry("2026-02-10", "80.00"),
                manualEntry("2026-03-10", "10.00"),
                manualEntry("2026-03-20", "20.00"),
            ),
            records = emptyList(),
        )

        assertTrue(snapshots[1].period.outOfScope)
        assertEquals(BigDecimal("0.00"), snapshots[1].graph20TotalGel)
        assertEquals(BigDecimal("20.00"), snapshots[2].graph20TotalGel)
        assertEquals(BigDecimal("20.00"), snapshots[2].graph15CumulativeGel)
        assertTrue(snapshots[2].reviewNeeded)
    }

    @Test
    fun `non gel entries stay unresolved until conversion exists`() {
        val config = SmallBusinessStatusConfig(
            effectiveDate = LocalDate.parse("2026-01-01"),
            defaultTaxRatePercent = BigDecimal("1.0"),
        )
        val snapshots = planner.buildYearSnapshots(
            year = 2026,
            profile = profile,
            config = config,
            entries = listOf(
                manualEntry("2026-01-05", "100.00", currency = "USD"),
                manualEntry("2026-01-06", "50.00"),
            ),
            records = emptyList(),
        )

        assertEquals(1, snapshots[0].unresolvedFxCount)
        assertEquals(BigDecimal("50.00"), snapshots[0].graph20TotalGel)
        assertEquals(BigDecimal("0.50"), snapshots[0].estimatedTaxAmountGel)
    }

    @Test
    fun `draft period becomes overdue after filing due date`() {
        val period = planner.declarationPeriodFor(
            incomeMonth = java.time.YearMonth.parse("2026-03"),
            config = null,
        )

        val status = planner.deriveWorkflowStatus(
            baseStatus = MonthlyWorkflowStatus.DRAFT,
            period = period,
            referenceDate = LocalDate.parse("2026-04-20"),
        )

        assertEquals(MonthlyWorkflowStatus.OVERDUE, status)
    }

    @Test
    fun `payment sent does not become overdue after due date`() {
        val period = planner.declarationPeriodFor(
            incomeMonth = java.time.YearMonth.parse("2026-03"),
            config = null,
        )

        val status = planner.deriveWorkflowStatus(
            baseStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
            period = period,
            referenceDate = LocalDate.parse("2026-04-20"),
        )

        assertEquals(MonthlyWorkflowStatus.PAYMENT_SENT, status)
        assertFalse(status == MonthlyWorkflowStatus.OVERDUE)
    }

    @Test
    fun `snapshot flags tax payment mismatch when paid amount differs from estimate`() {
        val config = SmallBusinessStatusConfig(
            effectiveDate = LocalDate.parse("2026-01-01"),
            defaultTaxRatePercent = BigDecimal("1.0"),
        )
        val snapshots = planner.buildYearSnapshots(
            year = 2026,
            profile = profile,
            config = config,
            entries = listOf(
                manualEntry("2026-01-05", "5000.00"),
            ),
            records = listOf(
                MonthlyDeclarationRecord(
                    yearMonth = YearMonth.parse("2026-01"),
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = false,
                    declarationFiledDate = LocalDate.parse("2026-02-10"),
                    paymentSentDate = LocalDate.parse("2026-02-10"),
                    paymentCreditedDate = LocalDate.parse("2026-02-10"),
                    paymentAmountGel = BigDecimal("10.00"),
                ),
            ),
        )
        val januarySnapshot = snapshots.first()
        val summary = planner.buildDashboardSummary(
            profile = profile,
            config = config,
            reminders = null,
            snapshots = snapshots,
            records = listOf(
                MonthlyDeclarationRecord(
                    yearMonth = YearMonth.parse("2026-01"),
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = false,
                    declarationFiledDate = LocalDate.parse("2026-02-10"),
                    paymentSentDate = LocalDate.parse("2026-02-10"),
                    paymentCreditedDate = LocalDate.parse("2026-02-10"),
                    paymentAmountGel = BigDecimal("10.00"),
                ),
            ),
        )

        assertTrue(januarySnapshot.taxPaymentMismatch)
        assertTrue(januarySnapshot.taxPaymentUnderpaid)
        assertEquals(BigDecimal("-40.00"), januarySnapshot.taxPaymentDifferenceGel)
        assertEquals(1, summary.paymentMismatchMonthsCount)
    }

    private fun manualEntry(
        date: String,
        amount: String,
        currency: String = "GEL",
    ): IncomeEntry = IncomeEntry(
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
        updatedAtEpochMillis = 0L,
    )
}
