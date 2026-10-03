package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderType {
    DECLARATION,
    PAYMENT
}

data class ReminderNotification(
    val type: ReminderType,
    val title: String,
    val body: String,
    val notificationId: Int? = null
)

enum class ReminderNotificationMessage {
    DECLARATION_REVIEW_AND_FX,
    DECLARATION_FX,
    DECLARATION_REVIEW,
    DECLARATION_ZERO_PREPARED,
    DECLARATION_ZERO_SUGGESTED,
    DECLARATION_DEFAULT,
    PAYMENT_REVIEW_AND_FX,
    PAYMENT_FX,
    PAYMENT_REVIEW,
    PAYMENT_FILED,
    PAYMENT_PENDING,
    PAYMENT_DEFAULT,
    PAYMENT_SENT_CHECK_CREDIT
}

interface ReminderNotificationStrings {
    fun title(type: ReminderType): String

    fun body(
        message: ReminderNotificationMessage,
        incomeMonth: YearMonth,
        unresolvedFxCount: Int,
        dueDate: LocalDate
    ): String
}

internal object ReminderEligibilityPolicy {
    fun shouldRemindDeclaration(snapshot: MonthlyDeclarationSnapshot): Boolean {
        val baseStatus = snapshot.record?.workflowStatus ?: snapshot.workflowStatus
        return !snapshot.period.outOfScope &&
            snapshot.record?.declarationFiledDate == null &&
            baseStatus in declarationStatuses
    }

    fun shouldRemindPayment(snapshot: MonthlyDeclarationSnapshot): Boolean = !snapshot.period.outOfScope &&
        snapshot.estimatedTaxAmountGel?.signum() == 1 &&
        !WorkflowStatusPolicy.isPaymentTerminal(snapshot.record?.workflowStatus ?: snapshot.workflowStatus)

    private val declarationStatuses = setOf(
        MonthlyWorkflowStatus.DRAFT,
        MonthlyWorkflowStatus.READY_TO_FILE,
        MonthlyWorkflowStatus.OVERDUE
    )
}

@Singleton
class ReminderPlanner
@Inject
constructor(private val strings: ReminderNotificationStrings) {
    fun buildNotifications(
        today: LocalDate,
        reminderConfig: ReminderConfig?,
        snapshot: MonthlyDeclarationSnapshot?
    ): List<ReminderNotification> {
        if (reminderConfig == null ||
            snapshot == null ||
            snapshot.period.outOfScope ||
            today.isBefore(snapshot.period.filingWindow.start)
        ) {
            return emptyList()
        }

        val notifications = mutableListOf<ReminderNotification>()

        val shouldRemindDeclaration =
            reminderConfig.declarationRemindersEnabled &&
                today.dayOfMonth in reminderConfig.declarationReminderDays &&
                ReminderEligibilityPolicy.shouldRemindDeclaration(snapshot)

        if (shouldRemindDeclaration) {
            notifications += buildDeclarationNotification(snapshot)
        }

        val shouldRemindPayment =
            reminderConfig.paymentRemindersEnabled &&
                today.dayOfMonth in reminderConfig.paymentReminderDays &&
                ReminderEligibilityPolicy.shouldRemindPayment(snapshot)

        // One actionable notification per run. Filing comes before payment.
        if (shouldRemindPayment && notifications.isEmpty()) {
            notifications += buildPaymentNotification(snapshot)
        }

        return notifications
    }

    fun buildNotificationsForSnapshots(
        today: LocalDate,
        reminderConfig: ReminderConfig?,
        snapshots: List<MonthlyDeclarationSnapshot>
    ): List<ReminderNotification> {
        val dueMonth = YearMonth.from(today).minusMonths(1)
        return snapshots.asSequence()
            .filter { snapshot ->
                // A missing history is not evidence of years of unfiled zero declarations.
                snapshot.period.incomeMonth == dueMonth ||
                    snapshot.record != null ||
                    snapshot.originalCurrencyTotals.isNotEmpty() ||
                    snapshot.graph20TotalGel.signum() != 0 ||
                    (snapshot.reviewNeeded && !snapshot.setupRequired && !snapshot.priorPeriodDataIncomplete)
            }
            .sortedWith(
                compareBy<MonthlyDeclarationSnapshot> {
                    if (it.period.incomeMonth == dueMonth) 0 else 1
                }.thenBy { it.period.incomeMonth }
            )
            .map { buildNotifications(today, reminderConfig, it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    fun buildPreviewNotification(type: ReminderType, snapshot: MonthlyDeclarationSnapshot?): ReminderNotification? {
        if (snapshot == null || snapshot.period.outOfScope) {
            return null
        }
        return when (type) {
            ReminderType.DECLARATION -> buildDeclarationNotification(snapshot)
            ReminderType.PAYMENT -> buildPaymentNotification(snapshot)
        }
    }

    private fun buildDeclarationNotification(snapshot: MonthlyDeclarationSnapshot): ReminderNotification {
        val dueDate = snapshot.period.filingWindow.dueDate
        val message =
            when {
                snapshot.reviewNeeded && snapshot.unresolvedFxCount > 0 ->
                    ReminderNotificationMessage.DECLARATION_REVIEW_AND_FX
                snapshot.unresolvedFxCount > 0 ->
                    ReminderNotificationMessage.DECLARATION_FX
                snapshot.reviewNeeded ->
                    ReminderNotificationMessage.DECLARATION_REVIEW
                snapshot.zeroDeclarationPrepared ->
                    ReminderNotificationMessage.DECLARATION_ZERO_PREPARED
                snapshot.zeroDeclarationSuggested ->
                    ReminderNotificationMessage.DECLARATION_ZERO_SUGGESTED
                else -> ReminderNotificationMessage.DECLARATION_DEFAULT
            }
        return ReminderNotification(
            type = ReminderType.DECLARATION,
            title = strings.title(ReminderType.DECLARATION),
            body =
            strings.body(
                message = message,
                incomeMonth = snapshot.period.incomeMonth,
                unresolvedFxCount = snapshot.unresolvedFxCount,
                dueDate = dueDate
            )
        )
    }

    private fun buildPaymentNotification(snapshot: MonthlyDeclarationSnapshot): ReminderNotification {
        val paymentWasSent = snapshot.record?.paymentSentDate != null ||
            (snapshot.record?.workflowStatus ?: snapshot.workflowStatus) == MonthlyWorkflowStatus.PAYMENT_SENT
        val dueDate = snapshot.period.filingWindow.dueDate
        val message =
            when {
                paymentWasSent -> ReminderNotificationMessage.PAYMENT_SENT_CHECK_CREDIT
                snapshot.reviewNeeded && snapshot.unresolvedFxCount > 0 ->
                    ReminderNotificationMessage.PAYMENT_REVIEW_AND_FX
                snapshot.unresolvedFxCount > 0 ->
                    ReminderNotificationMessage.PAYMENT_FX
                snapshot.reviewNeeded ->
                    ReminderNotificationMessage.PAYMENT_REVIEW
                snapshot.workflowStatus == MonthlyWorkflowStatus.FILED ->
                    ReminderNotificationMessage.PAYMENT_FILED
                snapshot.workflowStatus in paymentPendingStatuses ->
                    ReminderNotificationMessage.PAYMENT_PENDING
                else -> ReminderNotificationMessage.PAYMENT_DEFAULT
            }
        return ReminderNotification(
            type = ReminderType.PAYMENT,
            title = strings.title(ReminderType.PAYMENT),
            body =
            strings.body(
                message = message,
                incomeMonth = snapshot.period.incomeMonth,
                unresolvedFxCount = snapshot.unresolvedFxCount,
                dueDate = dueDate
            )
        )
    }

    private companion object {
        val paymentPendingStatuses =
            setOf(
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
                MonthlyWorkflowStatus.OVERDUE
            )
    }
}
