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
    fun `overdue is derived while every persisted status remains editable`() {
        assertFalse(WorkflowStatusPolicy.isEditable(MonthlyWorkflowStatus.OVERDUE))
        assertFalse(MonthlyWorkflowStatus.OVERDUE in WorkflowStatusPolicy.editableStatuses)
        assertEquals(
            MonthlyWorkflowStatus.entries.filterNot { it == MonthlyWorkflowStatus.OVERDUE },
            WorkflowStatusPolicy.editableStatuses
        )
        assertTrue(WorkflowStatusPolicy.isEditable(MonthlyWorkflowStatus.DRAFT))
        assertTrue(WorkflowStatusPolicy.isEditable(MonthlyWorkflowStatus.SETTLED))
    }
}
