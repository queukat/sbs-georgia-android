package com.queukat.sbsgeorgia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.repository.DeclarationFormConfigRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.usecase.CompleteMonthlyDeclarationUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveRollingIncomeUseCase
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    observeRollingIncomeUseCase: ObserveRollingIncomeUseCase,
    private val clock: Clock,
    declarationFormConfigRepository: DeclarationFormConfigRepository,
    private val actionPlanner: MonthlyDeclarationActionPlanner,
    private val completeMonthlyDeclarationUseCase: CompleteMonthlyDeclarationUseCase
) : ViewModel() {
    private val completionFailed = MutableStateFlow(false)
    private var isCompleting = false

    private val asOf = MutableStateFlow(LocalDate.now(clock))

    fun refreshDate() {
        asOf.value = LocalDate.now(clock)
    }

    val uiState =
        combine(
            observeDashboardSummaryUseCase(),
            declarationFormConfigRepository.observeConfig(),
            observeRollingIncomeUseCase(asOf)
        ) { summary, savedFormConfig, rollingIncome ->
            val formConfig = savedFormConfig ?: DeclarationFormConfig()
            val duePeriod = summary.currentDuePeriod
            HomeUiState(
                summary = summary,
                rollingIncome = rollingIncome,
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
        }.combine(completionFailed) { state, failed -> state.copy(completionFailed = failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun settleCurrentDuePeriod() {
        if (isCompleting) return
        val quickAccess = uiState.value.duePeriodQuickAccess ?: return
        if (!quickAccess.canQuickSettleMonth) return

        val snapshot = quickAccess.snapshot
        isCompleting = true
        completionFailed.value = false
        viewModelScope.launch {
            try {
                completeMonthlyDeclarationUseCase(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completionFailed.value = true
            } finally {
                isCompleting = false
            }
        }
    }
}
