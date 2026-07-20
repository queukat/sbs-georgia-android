package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus

object WorkflowStatusPolicy {
    val editableStatuses: List<MonthlyWorkflowStatus> =
        MonthlyWorkflowStatus.entries.filterNot { it == MonthlyWorkflowStatus.OVERDUE }

    fun requiresDeclarationFiledDate(status: MonthlyWorkflowStatus): Boolean = status in declarationDateRequiredStatuses

    fun requiresPaymentSentDate(status: MonthlyWorkflowStatus): Boolean = status in paymentSentDateRequiredStatuses

    fun requiresPaymentCreditedDate(status: MonthlyWorkflowStatus): Boolean =
        status in paymentCreditedDateRequiredStatuses

    fun isPaymentTerminal(status: MonthlyWorkflowStatus): Boolean = status in paymentTerminalStatuses

    fun isFullySettled(status: MonthlyWorkflowStatus): Boolean = status in fullySettledStatuses

    fun isEditable(status: MonthlyWorkflowStatus): Boolean = status != MonthlyWorkflowStatus.OVERDUE

    fun allowedTransitions(status: MonthlyWorkflowStatus): Set<MonthlyWorkflowStatus> = when (status) {
        MonthlyWorkflowStatus.DRAFT -> setOf(MonthlyWorkflowStatus.READY_TO_FILE)
        MonthlyWorkflowStatus.READY_TO_FILE ->
            setOf(
                MonthlyWorkflowStatus.FILED,
                MonthlyWorkflowStatus.DRAFT
            )
        MonthlyWorkflowStatus.FILED ->
            setOf(
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
                MonthlyWorkflowStatus.READY_TO_FILE
            )
        MonthlyWorkflowStatus.TAX_PAYMENT_PENDING ->
            setOf(
                MonthlyWorkflowStatus.PAYMENT_SENT,
                MonthlyWorkflowStatus.FILED
            )
        MonthlyWorkflowStatus.PAYMENT_SENT ->
            setOf(
                MonthlyWorkflowStatus.PAYMENT_CREDITED,
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING
            )
        MonthlyWorkflowStatus.PAYMENT_CREDITED -> setOf(MonthlyWorkflowStatus.SETTLED)
        MonthlyWorkflowStatus.SETTLED -> emptySet()
        MonthlyWorkflowStatus.OVERDUE ->
            setOf(
                MonthlyWorkflowStatus.READY_TO_FILE,
                MonthlyWorkflowStatus.FILED,
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
                MonthlyWorkflowStatus.PAYMENT_SENT,
                MonthlyWorkflowStatus.PAYMENT_CREDITED,
                MonthlyWorkflowStatus.SETTLED
            )
    }

    private val declarationDateRequiredStatuses =
        setOf(
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )

    private val paymentSentDateRequiredStatuses =
        setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )

    private val paymentCreditedDateRequiredStatuses =
        setOf(
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )

    private val paymentTerminalStatuses =
        setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )

    private val fullySettledStatuses =
        setOf(
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )
}
