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
    PAYMENT_DEFAULT
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

@Singleton
class ReminderPlanner
@Inject
constructor(private val strings: ReminderNotificationStrings) {
    fun buildNotifications(
        today: LocalDate,
        reminderConfig: ReminderConfig?,
        snapshot: MonthlyDeclarationSnapshot?
    ): List<ReminderNotification> {
        if (reminderConfig == null || snapshot == null || snapshot.period.outOfScope) {
            return emptyList()
        }

        val notifications = mutableListOf<ReminderNotification>()

        val shouldRemindDeclaration =
            reminderConfig.declarationRemindersEnabled &&
                today.dayOfMonth in reminderConfig.declarationReminderDays &&
                snapshot.workflowStatus in declarationStatuses

        if (shouldRemindDeclaration) {
            notifications += buildDeclarationNotification(snapshot)
        }

        val shouldRemindPayment =
            reminderConfig.paymentRemindersEnabled &&
                today.dayOfMonth in reminderConfig.paymentReminderDays &&
                snapshot.estimatedTaxAmountGel != null &&
                snapshot.estimatedTaxAmountGel.signum() > 0 &&
                !WorkflowStatusPolicy.isPaymentTerminal(snapshot.workflowStatus)

        if (shouldRemindPayment) {
            notifications += buildPaymentNotification(snapshot)
        }

        return notifications
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
        val dueDate = snapshot.period.filingWindow.dueDate
        val message =
            when {
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
        val declarationStatuses =
            setOf(
                MonthlyWorkflowStatus.DRAFT,
                MonthlyWorkflowStatus.READY_TO_FILE,
                MonthlyWorkflowStatus.OVERDUE
            )
        val paymentPendingStatuses =
            setOf(
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
                MonthlyWorkflowStatus.OVERDUE
            )
    }
}
