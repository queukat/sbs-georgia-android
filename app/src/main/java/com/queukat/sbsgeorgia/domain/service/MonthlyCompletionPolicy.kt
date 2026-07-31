package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.math.BigDecimal
import java.time.LocalDate

object MonthlyCompletionPolicy {
    fun paymentRequired(estimatedTaxAmountGel: BigDecimal?): Boolean = estimatedTaxAmountGel?.signum() == 1

    fun isFiledWithoutPaymentComplete(
        baseStatus: MonthlyWorkflowStatus,
        declarationFiledDate: LocalDate?,
        estimatedTaxAmountGel: BigDecimal?
    ): Boolean = estimatedTaxAmountGel != null &&
        !paymentRequired(estimatedTaxAmountGel) &&
        declarationFiledDate != null &&
        baseStatus in filedStatuses

    fun isComplete(snapshot: MonthlyDeclarationSnapshot): Boolean {
        val baseStatus = snapshot.record?.workflowStatus ?: snapshot.workflowStatus
        return WorkflowStatusPolicy.isPaymentTerminal(baseStatus) ||
            isFiledWithoutPaymentComplete(
                baseStatus = baseStatus,
                declarationFiledDate = snapshot.record?.declarationFiledDate,
                estimatedTaxAmountGel = snapshot.estimatedTaxAmountGel
            )
    }

    private val filedStatuses =
        setOf(
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED
        )
}
