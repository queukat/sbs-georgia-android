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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner(EnglishReminderNotificationStrings)
    private val reminderConfig =
        ReminderConfig(
            declarationReminderDays = listOf(10, 13, 15),
            paymentReminderDays = listOf(10, 13, 15),
            declarationRemindersEnabled = true,
            paymentRemindersEnabled = true,
            defaultReminderTime = LocalTime.of(9, 0),
            themeMode = ThemeMode.SYSTEM
        )

    @Test
    fun buildsDeclarationReminderForZeroMonth() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 10),
                reminderConfig = reminderConfig,
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.DRAFT,
                    graph20 = "0.00",
                    zeroDeclarationSuggested = true,
                    estimatedTax = "0.00"
                )
            )

        assertEquals(1, notifications.size)
        assertEquals(ReminderType.DECLARATION, notifications.single().type)
        assertTrue(notifications.single().body.contains("zero declaration", ignoreCase = true))
    }

    @Test
    fun buildsPaymentReminderForTaxableFiledMonth() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 13),
                reminderConfig = reminderConfig,
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00"
                )
            )

        assertEquals(1, notifications.count { it.type == ReminderType.PAYMENT })
        assertEquals(0, notifications.count { it.type == ReminderType.DECLARATION })
    }

    @Test
    fun declarationReminderMentionsReviewAndFxBlockers() {
        val notification =
            requireNotNull(
                planner.buildPreviewNotification(
                    type = ReminderType.DECLARATION,
                    snapshot =
                    sampleSnapshot(
                        workflowStatus = MonthlyWorkflowStatus.DRAFT,
                        graph20 = "2500.00",
                        zeroDeclarationSuggested = false,
                        estimatedTax = "25.00",
                        reviewNeeded = true,
                        unresolvedFxCount = 2
                    )
                )
            )

        assertTrue(notification.body.contains("Review March 2026"))
        assertTrue(notification.body.contains("2 FX entries"))
    }

    @Test
    fun paymentReminderMentionsFilingBeforePaymentWhenMonthIsNotFiledYet() {
        val notification =
            requireNotNull(
                planner.buildPreviewNotification(
                    type = ReminderType.PAYMENT,
                    snapshot =
                    sampleSnapshot(
                        workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                        graph20 = "2500.00",
                        zeroDeclarationSuggested = false,
                        estimatedTax = "25.00"
                    )
                )
            )

        assertTrue(notification.body.contains("After filing"))
    }

    @Test
    fun suppressesPaymentReminderAfterPaymentIsSent() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 15),
                reminderConfig = reminderConfig,
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.PAYMENT_SENT,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00"
                )
            )

        assertTrue(notifications.none { it.type == ReminderType.PAYMENT })
    }

    @Test
    fun respectsEachReminderChannelEnablementIndependently() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 10),
                reminderConfig = reminderConfig.copy(declarationRemindersEnabled = false),
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.DRAFT,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00"
                )
            )

        assertEquals(listOf(ReminderType.PAYMENT), notifications.map(ReminderNotification::type))
    }

    @Test
    fun suppressesAllNotificationsWhenBothChannelsAreDisabled() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 10),
                reminderConfig =
                reminderConfig.copy(
                    declarationRemindersEnabled = false,
                    paymentRemindersEnabled = false
                ),
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.DRAFT,
                    graph20 = "2500.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "25.00"
                )
            )

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun usesAdjustedDueDateAndBlockerInputsForLocalizedMessage() {
        val strings = RecordingReminderNotificationStrings()
        val planner = ReminderPlanner(strings)
        val snapshot =
            sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.OVERDUE,
                graph20 = "2500.00",
                zeroDeclarationSuggested = false,
                estimatedTax = "25.00",
                reviewNeeded = true,
                unresolvedFxCount = 2,
                incomeMonth = YearMonth.of(2028, 3),
                dueDate = LocalDate.of(2028, 4, 18)
            )

        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2028, 4, 15),
                reminderConfig = reminderConfig,
                snapshot = snapshot
            )

        assertEquals(2, notifications.size)
        assertEquals(
            listOf(
                ReminderNotificationMessage.DECLARATION_REVIEW_AND_FX,
                ReminderNotificationMessage.PAYMENT_REVIEW_AND_FX
            ),
            strings.bodyRequests.map(RecordingReminderNotificationStrings.BodyRequest::message)
        )
        assertTrue(strings.bodyRequests.all { it.incomeMonth == YearMonth.of(2028, 3) })
        assertTrue(strings.bodyRequests.all { it.unresolvedFxCount == 2 })
        assertTrue(strings.bodyRequests.all { it.dueDate == LocalDate.of(2028, 4, 18) })
    }

    @Test
    fun suppressesPaymentForZeroOrMissingEstimatedTax() {
        val zeroTaxNotifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 10),
                reminderConfig = reminderConfig.copy(declarationRemindersEnabled = false),
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    graph20 = "0.00",
                    zeroDeclarationSuggested = true,
                    estimatedTax = "0.00"
                )
            )

        assertFalse(zeroTaxNotifications.any { it.type == ReminderType.PAYMENT })
    }

    @Test
    fun skipsOutOfScopeMonths() {
        val notifications =
            planner.buildNotifications(
                today = LocalDate.of(2026, 4, 10),
                reminderConfig = reminderConfig,
                snapshot =
                sampleSnapshot(
                    workflowStatus = MonthlyWorkflowStatus.DRAFT,
                    graph20 = "0.00",
                    zeroDeclarationSuggested = false,
                    estimatedTax = "0.00",
                    outOfScope = true
                )
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
        incomeMonth: YearMonth = YearMonth.of(2026, 3),
        dueDate: LocalDate = LocalDate.of(2026, 4, 15)
    ): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
            period =
            MonthlyDeclarationPeriod(
                incomeMonth = incomeMonth,
                filingWindow =
                FilingWindow(
                    start = LocalDate.of(2026, 4, 1),
                    endInclusive = LocalDate.of(2026, 4, 15),
                    dueDate = dueDate
                ),
                inScope = !outOfScope,
                outOfScope = outOfScope
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
            record = null
        )
}

private class RecordingReminderNotificationStrings : ReminderNotificationStrings {
    data class BodyRequest(
        val message: ReminderNotificationMessage,
        val incomeMonth: YearMonth,
        val unresolvedFxCount: Int,
        val dueDate: LocalDate
    )

    val bodyRequests = mutableListOf<BodyRequest>()

    override fun title(type: ReminderType): String = type.name

    override fun body(
        message: ReminderNotificationMessage,
        incomeMonth: YearMonth,
        unresolvedFxCount: Int,
        dueDate: LocalDate
    ): String {
        bodyRequests += BodyRequest(message, incomeMonth, unresolvedFxCount, dueDate)
        return message.name
    }
}

private object EnglishReminderNotificationStrings : ReminderNotificationStrings {
    override fun title(type: ReminderType): String = when (type) {
        ReminderType.DECLARATION -> "Small business declaration action needed"
        ReminderType.PAYMENT -> "Small business tax payment action needed"
    }

    override fun body(
        message: ReminderNotificationMessage,
        incomeMonth: YearMonth,
        unresolvedFxCount: Int,
        dueDate: LocalDate
    ): String {
        val monthReference = incomeMonth.month.name.lowercase().replaceFirstChar(Char::uppercase) +
            " ${incomeMonth.year}"
        val fxEntries =
            if (unresolvedFxCount == 1) {
                "$unresolvedFxCount FX entry"
            } else {
                "$unresolvedFxCount FX entries"
            }
        return when (message) {
            ReminderNotificationMessage.DECLARATION_REVIEW_AND_FX ->
                "Review $monthReference and resolve $fxEntries before filing. Effective due date: $dueDate."
            ReminderNotificationMessage.DECLARATION_FX ->
                "Resolve $fxEntries for $monthReference before filing. Effective due date: $dueDate."
            ReminderNotificationMessage.DECLARATION_REVIEW ->
                "Review $monthReference before treating it as ready to file. Effective due date: $dueDate."
            ReminderNotificationMessage.DECLARATION_ZERO_PREPARED ->
                "Zero declaration for $monthReference is marked prepared but still has to be filed by $dueDate."
            ReminderNotificationMessage.DECLARATION_ZERO_SUGGESTED ->
                "This looks like a zero declaration month for $monthReference. Filing is still required by $dueDate."
            ReminderNotificationMessage.DECLARATION_DEFAULT ->
                "Declaration for $monthReference should be prepared and submitted by $dueDate."
            ReminderNotificationMessage.PAYMENT_REVIEW_AND_FX ->
                "Review $monthReference and resolve $fxEntries before relying on the tax amount. Effective due date: $dueDate."
            ReminderNotificationMessage.PAYMENT_FX ->
                "Resolve $fxEntries for $monthReference before sending the tax payment. Effective due date: $dueDate."
            ReminderNotificationMessage.PAYMENT_REVIEW ->
                "Review $monthReference before sending the tax payment. Effective due date: $dueDate."
            ReminderNotificationMessage.PAYMENT_FILED ->
                "Declaration for $monthReference is filed. Tax payment should be sent by $dueDate."
            ReminderNotificationMessage.PAYMENT_PENDING ->
                "Tax payment for $monthReference still needs to be sent by $dueDate."
            ReminderNotificationMessage.PAYMENT_DEFAULT ->
                "Estimated tax for $monthReference is ready. After filing, send the payment by $dueDate."
        }
    }
}
