package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderType {
    DECLARATION,
    PAYMENT,
}

data class ReminderNotification(
    val type: ReminderType,
    val title: String,
    val body: String,
)

@Singleton
class ReminderPlanner @Inject constructor() {
    fun buildNotifications(
        today: LocalDate,
        reminderConfig: ReminderConfig?,
        snapshot: MonthlyDeclarationSnapshot?,
    ): List<ReminderNotification> {
        if (reminderConfig == null || snapshot == null || snapshot.period.outOfScope) {
            return emptyList()
        }

        val notifications = mutableListOf<ReminderNotification>()
        val incomeMonthLabel = snapshot.period.incomeMonth.atDay(1).month.name.lowercase().replaceFirstChar(Char::uppercase)
        val monthReference = "$incomeMonthLabel ${snapshot.period.incomeMonth.year}"

        val shouldRemindDeclaration = reminderConfig.declarationRemindersEnabled &&
            today.dayOfMonth in reminderConfig.declarationReminderDays &&
            snapshot.workflowStatus in declarationStatuses

        if (shouldRemindDeclaration) {
            val body = if (snapshot.zeroDeclarationSuggested || snapshot.zeroDeclarationPrepared) {
                "Zero declaration for $monthReference still has to be filed by ${snapshot.period.filingWindow.dueDate}."
            } else {
                "Declaration for $monthReference should be prepared and submitted by ${snapshot.period.filingWindow.dueDate}."
            }
            notifications += ReminderNotification(
                type = ReminderType.DECLARATION,
                title = "Small business declaration due",
                body = body,
            )
        }

        val shouldRemindPayment = reminderConfig.paymentRemindersEnabled &&
            today.dayOfMonth in reminderConfig.paymentReminderDays &&
            snapshot.estimatedTaxAmountGel != null &&
            snapshot.estimatedTaxAmountGel.signum() > 0 &&
            snapshot.workflowStatus !in paymentTerminalStatuses

        if (shouldRemindPayment) {
            notifications += ReminderNotification(
                type = ReminderType.PAYMENT,
                title = "Small business tax payment due",
                body = "Tax payment for $monthReference should be sent by ${snapshot.period.filingWindow.dueDate}.",
            )
        }

        return notifications
    }

    private companion object {
        val declarationStatuses = setOf(
            MonthlyWorkflowStatus.DRAFT,
            MonthlyWorkflowStatus.READY_TO_FILE,
            MonthlyWorkflowStatus.OVERDUE,
        )
        val paymentTerminalStatuses = setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }
}
