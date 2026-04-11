package com.queukat.sbsgeorgia.ui.workflow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.usecase.ObserveMonthDetailUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class WorkflowStatusViewModel @Inject constructor(
    private val observeMonthDetailUseCase: ObserveMonthDetailUseCase,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {
    private var initializedYearMonth: YearMonth? = null

    private val _uiState = MutableStateFlow(WorkflowStatusUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkflowStatusEffect>()
    val effects = _effects.asSharedFlow()

    fun initialize(yearMonth: YearMonth) {
        if (initializedYearMonth == yearMonth) return
        initializedYearMonth = yearMonth
        viewModelScope.launch {
            val (snapshot, _) = observeMonthDetailUseCase(yearMonth).first()
            val baseStatus = snapshot?.record?.workflowStatus ?: MonthlyWorkflowStatus.DRAFT
            _uiState.value = WorkflowStatusUiState(
                yearMonth = yearMonth,
                dueDate = snapshot?.period?.filingWindow?.dueDate,
                derivedStatus = snapshot?.workflowStatus,
                baseStatus = baseStatus,
                editableStatuses = editableStatuses,
                zeroDeclarationPrepared = snapshot?.zeroDeclarationPrepared ?: false,
                declarationFiledDate = snapshot?.record?.declarationFiledDate,
                paymentSentDate = snapshot?.record?.paymentSentDate,
                paymentCreditedDate = snapshot?.record?.paymentCreditedDate,
                paymentAmount = snapshot?.record?.paymentAmountGel?.toPlainString().orEmpty(),
                notes = snapshot?.record?.notes.orEmpty(),
            )
        }
    }

    fun updateStatus(status: MonthlyWorkflowStatus) {
        val current = _uiState.value
        _uiState.value = current.copy(
            baseStatus = status,
            declarationFiledDate = if (status.ordinal >= MonthlyWorkflowStatus.FILED.ordinal) {
                current.declarationFiledDate
            } else {
                null
            },
            paymentSentDate = if (status.ordinal >= MonthlyWorkflowStatus.PAYMENT_SENT.ordinal) {
                current.paymentSentDate
            } else {
                null
            },
            paymentCreditedDate = if (status.ordinal >= MonthlyWorkflowStatus.PAYMENT_CREDITED.ordinal) {
                current.paymentCreditedDate
            } else {
                null
            },
            paymentAmount = if (status.ordinal >= MonthlyWorkflowStatus.PAYMENT_SENT.ordinal) {
                current.paymentAmount
            } else {
                ""
            },
            errorMessage = null,
        )
    }

    fun updateDeclarationFiledDate(value: java.time.LocalDate) {
        _uiState.value = _uiState.value.copy(declarationFiledDate = value, errorMessage = null)
    }

    fun clearDeclarationFiledDate() {
        _uiState.value = _uiState.value.copy(declarationFiledDate = null, errorMessage = null)
    }

    fun updatePaymentSentDate(value: java.time.LocalDate) {
        _uiState.value = _uiState.value.copy(paymentSentDate = value, errorMessage = null)
    }

    fun clearPaymentSentDate() {
        _uiState.value = _uiState.value.copy(paymentSentDate = null, errorMessage = null)
    }

    fun updatePaymentCreditedDate(value: java.time.LocalDate) {
        _uiState.value = _uiState.value.copy(paymentCreditedDate = value, errorMessage = null)
    }

    fun clearPaymentCreditedDate() {
        _uiState.value = _uiState.value.copy(paymentCreditedDate = null, errorMessage = null)
    }

    fun updatePaymentAmount(value: String) {
        _uiState.value = _uiState.value.copy(paymentAmount = value, errorMessage = null)
    }

    fun updateZeroDeclarationPrepared(value: Boolean) {
        _uiState.value = _uiState.value.copy(zeroDeclarationPrepared = value, errorMessage = null)
    }

    fun updateNotes(value: String) {
        _uiState.value = _uiState.value.copy(notes = value, errorMessage = null)
    }

    fun save() {
        val current = _uiState.value
        val yearMonth = current.yearMonth ?: return
        val paymentAmount = current.paymentAmount.trim().takeIf { it.isNotBlank() }?.let {
            runCatching { BigDecimal(it) }.getOrNull()
        }
        when {
            current.baseStatus in declarationDateRequiredStatuses && current.declarationFiledDate == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.workflow_error_declaration_date_required))
            }
            current.baseStatus in paymentSentDateRequiredStatuses && current.paymentSentDate == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.workflow_error_payment_sent_date_required))
            }
            current.baseStatus in paymentCreditedDateRequiredStatuses && current.paymentCreditedDate == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.workflow_error_payment_credited_date_required))
            }
            current.paymentAmount.isNotBlank() && (paymentAmount == null || paymentAmount.signum() != 1) -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.workflow_error_payment_amount_invalid))
            }
            else -> {
                _uiState.value = current.copy(isSaving = true, errorMessage = null)
                viewModelScope.launch {
                    upsertMonthlyDeclarationRecordUseCase(
                        MonthlyDeclarationRecord(
                            yearMonth = yearMonth,
                            workflowStatus = current.baseStatus,
                            zeroDeclarationPrepared = current.zeroDeclarationPrepared,
                            declarationFiledDate = current.declarationFiledDate,
                            paymentSentDate = current.paymentSentDate,
                            paymentCreditedDate = current.paymentCreditedDate,
                            paymentAmountGel = paymentAmount,
                            notes = current.notes.trim(),
                        ),
                    )
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _effects.emit(WorkflowStatusEffect.Saved)
                }
            }
        }
    }

    private companion object {
        val editableStatuses = MonthlyWorkflowStatus.entries.filter { it != MonthlyWorkflowStatus.OVERDUE }
        val declarationDateRequiredStatuses = setOf(
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
        val paymentSentDateRequiredStatuses = setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
        val paymentCreditedDateRequiredStatuses = setOf(
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }
}
