package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val reminderConfig = ReminderConfig(
        declarationReminderDays = listOf(10, 13, 15),
        paymentReminderDays = listOf(10, 13, 15),
        declarationRemindersEnabled = true,
        paymentRemindersEnabled = true,
        defaultReminderTime = LocalTime.of(9, 0),
        themeMode = ThemeMode.SYSTEM,
    )

    @Test
    fun buildsDeclarationReminderForZeroMonth() {
        val notifications = planner.buildNotifications(
            today = LocalDate.of(2026, 4, 10),
            reminderConfig = reminderConfig,
            snapshot = sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.DRAFT,
                graph20 = "0.00",
                zeroDeclarationSuggested = true,
                estimatedTax = "0.00",
            ),
        )

        assertEquals(1, notifications.size)
        assertEquals(ReminderType.DECLARATION, notifications.single().type)
        assertTrue(notifications.single().body.contains("zero declaration", ignoreCase = true))
    }

    @Test
    fun buildsPaymentReminderForTaxableFiledMonth() {
        val notifications = planner.buildNotifications(
            today = LocalDate.of(2026, 4, 13),
            reminderConfig = reminderConfig,
            snapshot = sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.FILED,
                graph20 = "2500.00",
                zeroDeclarationSuggested = false,
                estimatedTax = "25.00",
            ),
        )

        assertEquals(1, notifications.count { it.type == ReminderType.PAYMENT })
        assertEquals(0, notifications.count { it.type == ReminderType.DECLARATION })
    }

    @Test
    fun declarationReminderMentionsReviewAndFxBlockers() {
        val notification = requireNotNull(
            planner.buildPreviewNotification(
                type = ReminderType.DECLARATION,
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.DRAFT,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00",
                    reviewNeeded = true,
                    unresolvedFxCount = 2,
                ),
            ),
        )

        assertTrue(notification.body.contains("Review March 2026"))
        assertTrue(notification.body.contains("2 FX entries"))
    }

    @Test
    fun paymentReminderMentionsFilingBeforePaymentWhenMonthIsNotFiledYet() {
        val notification = requireNotNull(
            planner.buildPreviewNotification(
                type = ReminderType.PAYMENT,
                snapshot = sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00",
                ),
            ),
        )

        assertTrue(notification.body.contains("After filing"))
    }

    @Test
    fun suppressesPaymentReminderAfterPaymentIsSent() {
        val notifications = planner.buildNotifications(
            today = LocalDate.of(2026, 4, 15),
            reminderConfig = reminderConfig,
            snapshot = sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                graph20 = "2500.00",
                zeroDeclarationSuggested = false,
                estimatedTax = "25.00",
            ),
        )

        assertTrue(notifications.none { it.type == ReminderType.PAYMENT })
    }

    @Test
    fun skipsOutOfScopeMonths() {
        val notifications = planner.buildNotifications(
            today = LocalDate.of(2026, 4, 10),
            reminderConfig = reminderConfig,
            snapshot = sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.DRAFT,
                graph20 = "0.00",
                zeroDeclarationSuggested = false,
                estimatedTax = "0.00",
                outOfScope = true,
            ),
        )

        assertTrue(notifications.isEmpty())
    }

    private fun sampleSnapshot(
        workflowStatus: MonthlyWorkflowStatus,
        graph20: String,
        zeroDeclarationSuggested: Boolean,
        estimatedTax: String,
        outOfScope: Boolean = false,
        reviewNeeded: Boolean = false,
        unresolvedFxCount: Int = 0,
    ): MonthlyDeclarationSnapshot {
        val incomeMonth = YearMonth.of(2026, 3)
        return MonthlyDeclarationSnapshot(
            period = MonthlyDeclarationPeriod(
                incomeMonth = incomeMonth,
                filingWindow = FilingWindow(
                    start = LocalDate.of(2026, 4, 1),
                    endInclusive = LocalDate.of(2026, 4, 15),
                    dueDate = LocalDate.of(2026, 4, 15),
                ),
                inScope = !outOfScope,
                outOfScope = outOfScope,
            ),
            workflowStatus = workflowStatus,
            graph20TotalGel = BigDecimal(graph20),
            graph15CumulativeGel = BigDecimal(graph20),
            originalCurrencyTotals = emptyList(),
            estimatedTaxAmountGel = BigDecimal(estimatedTax),
            unresolvedFxCount = unresolvedFxCount,
            zeroDeclarationSuggested = zeroDeclarationSuggested,
            zeroDeclarationPrepared = false,
            reviewNeeded = reviewNeeded,
            setupRequired = false,
            record = null,
        )
    }
}
