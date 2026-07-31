package com.queukat.sbsgeorgia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.repository.DeclarationFormConfigRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.usecase.CompleteMonthlyDeclarationUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    declarationFormConfigRepository: DeclarationFormConfigRepository,
    private val actionPlanner: MonthlyDeclarationActionPlanner,
    private val completeMonthlyDeclarationUseCase: CompleteMonthlyDeclarationUseCase
) : ViewModel() {
    val uiState =
        combine(
            observeDashboardSummaryUseCase(),
            declarationFormConfigRepository.observeConfig()
        ) { summary, savedFormConfig ->
            val formConfig = savedFormConfig ?: DeclarationFormConfig()
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
                            yearMonth = snapshot.period.incomeMonth,
                            formConfig = formConfig
                        ),
                        canCopyDeclarationValues = actionState.canCopyDeclarationValues,
                        canCopyPaymentText = actionState.canCopyPaymentText,
                        canQuickSettleMonth = actionState.canQuickSettleMonth,
                        monthAlreadySettled = actionState.monthAlreadySettled,
                        filingOpensOn = actionState.filingOpensOn,
                        paymentRequired = actionState.paymentRequired
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun settleCurrentDuePeriod() {
        val quickAccess = uiState.value.duePeriodQuickAccess ?: return
        if (!quickAccess.canQuickSettleMonth) return

        val snapshot = quickAccess.snapshot
        viewModelScope.launch {
            completeMonthlyDeclarationUseCase(snapshot)
        }
    }
}
