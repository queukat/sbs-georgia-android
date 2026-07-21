package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowStatusPolicyTest {
    @Test
    fun `status dates are required from the workflow stage that creates them`() {
        assertFalse(WorkflowStatusPolicy.requiresDeclarationFiledDate(MonthlyWorkflowStatus.READY_TO_FILE))
        assertTrue(WorkflowStatusPolicy.requiresDeclarationFiledDate(MonthlyWorkflowStatus.FILED))
        assertFalse(WorkflowStatusPolicy.requiresPaymentSentDate(MonthlyWorkflowStatus.TAX_PAYMENT_PENDING))
        assertTrue(WorkflowStatusPolicy.requiresPaymentSentDate(MonthlyWorkflowStatus.PAYMENT_SENT))
        assertFalse(WorkflowStatusPolicy.requiresPaymentCreditedDate(MonthlyWorkflowStatus.PAYMENT_SENT))
        assertTrue(WorkflowStatusPolicy.requiresPaymentCreditedDate(MonthlyWorkflowStatus.PAYMENT_CREDITED))
    }

    @Test
    fun `payment sent is terminal for repeated payment actions but not fully settled`() {
        assertTrue(WorkflowStatusPolicy.isPaymentTerminal(MonthlyWorkflowStatus.PAYMENT_SENT))
        assertFalse(WorkflowStatusPolicy.isFullySettled(MonthlyWorkflowStatus.PAYMENT_SENT))
        assertTrue(WorkflowStatusPolicy.isFullySettled(MonthlyWorkflowStatus.PAYMENT_CREDITED))
        assertTrue(WorkflowStatusPolicy.isFullySettled(MonthlyWorkflowStatus.SETTLED))
    }

    @Test
    fun `overdue is derived and remains non editable while allowing recovery transitions`() {
        assertFalse(WorkflowStatusPolicy.isEditable(MonthlyWorkflowStatus.OVERDUE))
        assertFalse(MonthlyWorkflowStatus.OVERDUE in WorkflowStatusPolicy.editableStatuses)
        assertEquals(
            setOf(
                MonthlyWorkflowStatus.READY_TO_FILE,
                MonthlyWorkflowStatus.FILED,
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
                MonthlyWorkflowStatus.PAYMENT_SENT,
                MonthlyWorkflowStatus.PAYMENT_CREDITED,
                MonthlyWorkflowStatus.SETTLED
            ),
            WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.OVERDUE)
        )
    }

    @Test
    fun `normal transitions retain required rollback paths and settled has none`() {
        assertEquals(
            setOf(MonthlyWorkflowStatus.FILED, MonthlyWorkflowStatus.DRAFT),
            WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.READY_TO_FILE)
        )
        assertEquals(
            setOf(MonthlyWorkflowStatus.PAYMENT_CREDITED, MonthlyWorkflowStatus.TAX_PAYMENT_PENDING),
            WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.PAYMENT_SENT)
        )
        assertTrue(WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.SETTLED).isEmpty())
    }

    @Test
    fun `payment transitions cannot skip confirmation stages`() {
        assertFalse(
            MonthlyWorkflowStatus.PAYMENT_SENT in
                WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.FILED)
        )
        assertFalse(
            MonthlyWorkflowStatus.PAYMENT_CREDITED in
                WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.TAX_PAYMENT_PENDING)
        )
        assertFalse(
            MonthlyWorkflowStatus.SETTLED in
                WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.PAYMENT_SENT)
        )
        assertEquals(
            setOf(MonthlyWorkflowStatus.SETTLED),
            WorkflowStatusPolicy.allowedTransitions(MonthlyWorkflowStatus.PAYMENT_CREDITED)
        )
    }
}
