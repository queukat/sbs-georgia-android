package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import java.time.LocalDate
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

@Singleton
class ReminderPlanner
@Inject
constructor() {
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
                snapshot.workflowStatus !in paymentTerminalStatuses

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
        val monthReference = snapshot.monthReference()
        val dueDate = snapshot.period.filingWindow.dueDate
        val body =
            when {
                snapshot.reviewNeeded && snapshot.unresolvedFxCount > 0 ->
                    "Review $monthReference and resolve ${snapshot.unresolvedFxCount} FX entries before filing. Effective due date: $dueDate."
                snapshot.unresolvedFxCount > 0 ->
                    "Resolve ${snapshot.unresolvedFxCount} FX entries for $monthReference before filing. Effective due date: $dueDate."
                snapshot.reviewNeeded ->
                    "Review $monthReference before treating it as ready to file. Effective due date: $dueDate."
                snapshot.zeroDeclarationPrepared ->
                    "Zero declaration for $monthReference is marked prepared but still has to be filed by $dueDate."
                snapshot.zeroDeclarationSuggested ->
                    "This looks like a zero declaration month for $monthReference. Filing is still required by $dueDate."
                else ->
                    "Declaration for $monthReference should be prepared and submitted by $dueDate."
            }
        return ReminderNotification(
            type = ReminderType.DECLARATION,
            title = "Small business declaration action needed",
            body = body
        )
    }

    private fun buildPaymentNotification(snapshot: MonthlyDeclarationSnapshot): ReminderNotification {
        val monthReference = snapshot.monthReference()
        val dueDate = snapshot.period.filingWindow.dueDate
        val body =
            when {
                snapshot.reviewNeeded && snapshot.unresolvedFxCount > 0 ->
                    "Review $monthReference and resolve ${snapshot.unresolvedFxCount} FX entries before relying on the tax amount. Effective due date: $dueDate."
                snapshot.unresolvedFxCount > 0 ->
                    "Resolve ${snapshot.unresolvedFxCount} FX entries for $monthReference before sending the tax payment. Effective due date: $dueDate."
                snapshot.reviewNeeded ->
                    "Review $monthReference before sending the tax payment. Effective due date: $dueDate."
                snapshot.workflowStatus == MonthlyWorkflowStatus.FILED ->
                    "Declaration for $monthReference is filed. Tax payment should be sent by $dueDate."
                snapshot.workflowStatus in paymentPendingStatuses ->
                    "Tax payment for $monthReference still needs to be sent by $dueDate."
                else ->
                    "Estimated tax for $monthReference is ready. After filing, send the payment by $dueDate."
            }
        return ReminderNotification(
            type = ReminderType.PAYMENT,
            title = "Small business tax payment action needed",
            body = body
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
        val paymentTerminalStatuses =
            setOf(
                MonthlyWorkflowStatus.PAYMENT_SENT,
                MonthlyWorkflowStatus.PAYMENT_CREDITED,
                MonthlyWorkflowStatus.SETTLED
            )
    }
}

private fun MonthlyDeclarationSnapshot.monthReference(): String {
    val incomeMonthLabel =
        period.incomeMonth
            .atDay(1)
            .month.name
            .lowercase()
            .replaceFirstChar(Char::uppercase)
    return "$incomeMonthLabel ${period.incomeMonth.year}"
}
