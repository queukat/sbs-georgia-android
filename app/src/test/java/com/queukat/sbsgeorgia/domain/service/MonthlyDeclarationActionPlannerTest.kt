package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
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

class MonthlyDeclarationActionPlannerTest {
    private val planner =
        MonthlyDeclarationActionPlanner(
            Clock.fixed(Instant.parse("2026-04-10T00:00:00Z"), ZoneOffset.UTC)
        )

    @Test
    fun blocksDeclarationActionsBeforeFilingWindowOpens() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(),
                registrationId = "123456789",
                referenceDate = LocalDate.of(2026, 3, 31)
            )

        assertEquals(MonthUserJourneyState.WAIT_FOR_FILING_WINDOW, state.journeyState)
        assertTrue(MonthlyActionBlocker.FILING_WINDOW_CLOSED in state.blockers)
        assertEquals(LocalDate.of(2026, 4, 1), state.filingOpensOn)
        assertFalse(state.canCopyDeclarationValues)
        assertFalse(state.canCopyPaymentText)
        assertFalse(state.canPreparePayment)
    }

    @Test
    fun keepsDeclarationCopyAvailableButBlocksPaymentTextWithoutRegistrationId() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.FILED
                ),
                registrationId = ""
            )

        assertEquals(MonthUserJourneyState.PREPARE_PAYMENT, state.journeyState)
        assertTrue(MonthlyActionBlocker.MISSING_PAYMENT_COMMENT in state.blockers)
        assertTrue(state.canCopyDeclarationValues)
        assertFalse(state.canCopyPaymentText)
        assertFalse(state.canPreparePayment)
    }

    @Test
    fun allowsPaymentPreparationForFiledTaxableMonthWithRegistrationId() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.FILED
                ),
                registrationId = "123456789"
            )

        assertEquals(MonthUserJourneyState.PREPARE_PAYMENT, state.journeyState)
        assertTrue(state.blockers.isEmpty())
        assertTrue(state.canCopyDeclarationValues)
        assertTrue(state.canCopyPaymentText)
        assertTrue(state.canPreparePayment)
        assertTrue(state.canQuickSettleMonth)
    }

    @Test
    fun unresolvedFxTakesPriorityOverGenericReview() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    reviewNeeded = true,
                    unresolvedFxCount = 1
                ),
                registrationId = "123456789"
            )

        assertEquals(MonthUserJourneyState.RESOLVE_FX, state.journeyState)
        assertTrue(MonthlyActionBlocker.UNRESOLVED_FX in state.blockers)
        assertFalse(MonthlyActionBlocker.REVIEW_REQUIRED in state.blockers)
        assertFalse(state.canCopyDeclarationValues)
        assertFalse(state.canPreparePayment)
    }

    @Test
    fun paymentSentMonthCannotBeQuickSettledAgain() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                    record = MonthlyDeclarationRecord(
                        yearMonth = INCOME_MONTH,
                        workflowStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                        zeroDeclarationPrepared = false,
                        declarationFiledDate = LocalDate.of(2026, 4, 10),
                        paymentSentDate = LocalDate.of(2026, 4, 10)
                    )
                ),
                registrationId = "123456789"
            )

        assertEquals(MonthUserJourneyState.CONFIRM_PAYMENT_CREDITED, state.journeyState)
        assertTrue(state.monthAlreadySettled)
        assertFalse(state.canQuickSettleMonth)
        assertFalse(state.canPreparePayment)
    }

    @Test
    fun terminalStoredStatusBlocksQuickSettleEvenWhenSnapshotStatusIsDerivedDifferently() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.OVERDUE,
                    record = MonthlyDeclarationRecord(
                        yearMonth = INCOME_MONTH,
                        workflowStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                        zeroDeclarationPrepared = false,
                        declarationFiledDate = LocalDate.of(2026, 4, 10),
                        paymentSentDate = LocalDate.of(2026, 4, 10)
                    )
                ),
                registrationId = "123456789"
            )

        assertTrue(state.monthAlreadySettled)
        assertFalse(state.canQuickSettleMonth)
        assertFalse(state.canPreparePayment)
    }

    @Test
    fun zeroTaxFiledMonthNeedsNoPaymentCommentAndCanBeSettled() {
        val state =
            planner.plan(
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    graph20 = "0.00",
                    estimatedTax = "0.00"
                ).copy(zeroDeclarationPrepared = true),
                registrationId = null
            )

        assertEquals(MonthUserJourneyState.SETTLED, state.journeyState)
        assertTrue(MonthlyActionBlocker.NO_PAYMENT_DUE in state.blockers)
        assertFalse(MonthlyActionBlocker.MISSING_PAYMENT_COMMENT in state.blockers)
        assertTrue(state.canCopyDeclarationValues)
        assertFalse(state.canCopyPaymentText)
        assertFalse(state.canPreparePayment)
        assertTrue(state.canQuickSettleMonth)
    }

    @Test
    fun setupAndOutOfScopeBlockEveryDeclarationAndPaymentAction() {
        val setupBlocked =
            planner.plan(
                snapshot = sampleSnapshot(reviewNeeded = true).copy(setupRequired = true),
                registrationId = "123456789"
            )
        val outOfScope =
            planner.plan(
                snapshot = sampleSnapshot().copy(
                    period = sampleSnapshot().period.copy(inScope = false, outOfScope = true)
                ),
                registrationId = "123456789"
            )

        assertEquals(MonthUserJourneyState.SETUP_REQUIRED, setupBlocked.journeyState)
        assertTrue(MonthlyActionBlocker.SETUP_REQUIRED in setupBlocked.blockers)
        assertFalse(setupBlocked.canCopyDeclarationValues)
        assertFalse(setupBlocked.canPreparePayment)
        assertEquals(MonthUserJourneyState.OUT_OF_SCOPE, outOfScope.journeyState)
        assertTrue(MonthlyActionBlocker.OUT_OF_SCOPE in outOfScope.blockers)
        assertFalse(outOfScope.canCopyDeclarationValues)
        assertFalse(outOfScope.canCopyPaymentText)
        assertFalse(outOfScope.canQuickSettleMonth)
    }

    private fun sampleSnapshot(
        workflowStatus: MonthlyWorkflowStatus = MonthlyWorkflowStatus.DRAFT,
        graph20: String = "2500.00",
        estimatedTax: String = "25.00",
        reviewNeeded: Boolean = false,
        unresolvedFxCount: Int = 0,
        record: MonthlyDeclarationRecord? = null
    ): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
        period = MonthlyDeclarationPeriod(
            incomeMonth = INCOME_MONTH,
            filingWindow = FilingWindow(
                start = LocalDate.of(2026, 4, 1),
                endInclusive = LocalDate.of(2026, 4, 15),
                dueDate = LocalDate.of(2026, 4, 15)
            ),
            inScope = true,
            outOfScope = false
        ),
        workflowStatus = workflowStatus,
        graph20TotalGel = BigDecimal(graph20),
        graph15CumulativeGel = BigDecimal(graph20),
        originalCurrencyTotals = emptyList(),
        estimatedTaxAmountGel = BigDecimal(estimatedTax),
        unresolvedFxCount = unresolvedFxCount,
        zeroDeclarationSuggested = false,
        zeroDeclarationPrepared = false,
        reviewNeeded = reviewNeeded,
        setupRequired = false,
        record = record
    )

    private companion object {
        val INCOME_MONTH: YearMonth = YearMonth.of(2026, 3)
    }
}
