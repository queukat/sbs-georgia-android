package com.queukat.sbsgeorgia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
) : ViewModel() {
    val uiState = observeDashboardSummaryUseCase()
        .map { summary ->
            val duePeriod = summary.currentDuePeriod
            val filingWindowOpen = duePeriod?.let { planner.isFilingWindowOpen(it.period) } ?: false
            val alreadySettled = duePeriod?.workflowStatus?.let { it in settledStatuses } == true
            HomeUiState(
                summary = summary,
                duePeriodQuickAccess = duePeriod?.let { snapshot ->
                    HomeDuePeriodQuickAccess(
                        snapshot = snapshot,
                        copyBundle = buildDeclarationCopyBundle(
                            snapshot = snapshot,
                            registrationId = summary.registrationId,
                            yearMonth = snapshot.period.incomeMonth,
                        ),
                        canCopyDeclarationValues = filingWindowOpen &&
                            snapshot.unresolvedFxCount == 0 &&
                            !snapshot.reviewNeeded,
                        canQuickSettleMonth = !snapshot.period.outOfScope &&
                            filingWindowOpen &&
                            !alreadySettled,
                        monthAlreadySettled = alreadySettled,
                        filingOpensOn = if (!snapshot.period.outOfScope && !filingWindowOpen) {
                            snapshot.period.filingWindow.start
                        } else {
                            null
                        },
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun settleCurrentDuePeriod() {
        val quickAccess = uiState.value.duePeriodQuickAccess ?: return
        if (!quickAccess.canQuickSettleMonth) return

        val snapshot = quickAccess.snapshot
        val today = LocalDate.now(clock)
        viewModelScope.launch {
            upsertMonthlyDeclarationRecordUseCase(
                MonthlyDeclarationRecord(
                    yearMonth = snapshot.period.incomeMonth,
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = snapshot.zeroDeclarationPrepared || snapshot.zeroDeclarationSuggested,
                    declarationFiledDate = snapshot.record?.declarationFiledDate ?: today,
                    paymentSentDate = snapshot.record?.paymentSentDate ?: today,
                    paymentCreditedDate = snapshot.record?.paymentCreditedDate ?: today,
                    paymentAmountGel = snapshot.record?.paymentAmountGel
                        ?: snapshot.estimatedTaxAmountGel
                        ?: BigDecimal.ZERO.setScale(2),
                    notes = snapshot.record?.notes.orEmpty(),
                ),
            )
        }
    }

    private companion object {
        val settledStatuses = setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }
}
