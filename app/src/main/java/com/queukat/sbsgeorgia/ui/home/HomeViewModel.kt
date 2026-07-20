package com.queukat.sbsgeorgia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
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
class HomeViewModel
@Inject
constructor(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    private val actionPlanner: MonthlyDeclarationActionPlanner,
    private val clock: Clock
) : ViewModel() {
    val uiState =
        observeDashboardSummaryUseCase()
            .map { summary ->
                val duePeriod = summary.currentDuePeriod
                HomeUiState(
                    summary = summary,
                    duePeriodQuickAccess =
                    duePeriod?.let { snapshot ->
                        val actionState = actionPlanner.plan(
                            snapshot = snapshot,
                            registrationId = summary.registrationId
                        )
                        HomeDuePeriodQuickAccess(
                            snapshot = snapshot,
                            copyBundle =
                            buildDeclarationCopyBundle(
                                snapshot = snapshot,
                                registrationId = summary.registrationId,
                                yearMonth = snapshot.period.incomeMonth
                            ),
                            canCopyDeclarationValues = actionState.canCopyDeclarationValues,
                            canCopyPaymentText = actionState.canCopyPaymentText,
                            canQuickSettleMonth = actionState.canQuickSettleMonth,
                            monthAlreadySettled = actionState.monthAlreadySettled,
                            filingOpensOn = actionState.filingOpensOn
                        )
                    }
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
                    zeroDeclarationPrepared =
                    snapshot.zeroDeclarationPrepared || snapshot.zeroDeclarationSuggested,
                    declarationFiledDate = snapshot.record?.declarationFiledDate ?: today,
                    paymentSentDate = snapshot.record?.paymentSentDate ?: today,
                    paymentCreditedDate = snapshot.record?.paymentCreditedDate ?: today,
                    paymentAmountGel =
                    snapshot.record?.paymentAmountGel
                        ?: snapshot.estimatedTaxAmountGel
                        ?: BigDecimal.ZERO.setScale(2),
                    notes = snapshot.record?.notes.orEmpty()
                )
            )
        }
    }
}
