package com.queukat.sbsgeorgia.ui.workflow

import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.time.LocalDate
import java.time.YearMonth

data class WorkflowStatusUiState(
    val yearMonth: YearMonth? = null,
    val dueDate: LocalDate? = null,
    val derivedStatus: MonthlyWorkflowStatus? = null,
    val baseStatus: MonthlyWorkflowStatus = MonthlyWorkflowStatus.DRAFT,
    val editableStatuses: List<MonthlyWorkflowStatus> = emptyList(),
    val zeroDeclarationPrepared: Boolean = false,
    val declarationFiledDate: LocalDate? = null,
    val paymentSentDate: LocalDate? = null,
    val paymentCreditedDate: LocalDate? = null,
    val paymentAmount: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed interface WorkflowStatusEffect {
    data object Saved : WorkflowStatusEffect
}
