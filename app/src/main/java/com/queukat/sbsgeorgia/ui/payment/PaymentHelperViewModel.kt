package com.queukat.sbsgeorgia.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.service.MonthlyActionBlocker
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObservePaymentHelperUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentHelperViewModel @Inject constructor(
    private val observePaymentHelperUseCase: ObservePaymentHelperUseCase,
    private val actionPlanner: MonthlyDeclarationActionPlanner
) : ViewModel() {
    private val selectedYearMonth = MutableStateFlow<YearMonth?>(null)

    val uiState =
        selectedYearMonth
            .filterNotNull()
            .flatMapLatest { yearMonth ->
                observePaymentHelperUseCase(yearMonth)
                    .map { data ->
                        val snapshot = data.snapshot
                        val actionState = actionPlanner.plan(
                            snapshot = snapshot,
                            registrationId = data.registrationId
                        )
                        val readinessState =
                            when {
                                snapshot == null -> PaymentHelperReadinessState.MONTH_UNAVAILABLE
                                MonthlyActionBlocker.OUT_OF_SCOPE in actionState.blockers ->
                                    PaymentHelperReadinessState.OUT_OF_SCOPE
                                MonthlyActionBlocker.UNRESOLVED_FX in actionState.blockers ->
                                    PaymentHelperReadinessState.UNRESOLVED_FX
                                MonthlyActionBlocker.REVIEW_REQUIRED in actionState.blockers ->
                                    PaymentHelperReadinessState.REVIEW_REQUIRED
                                snapshot.zeroDeclarationSuggested -> PaymentHelperReadinessState.ZERO_DECLARATION
                                else -> PaymentHelperReadinessState.READY
                            }
                        PaymentHelperUiState(
                            data = data,
                            actionState = actionState,
                            readinessState = readinessState,
                            isReady = actionState.canPreparePayment
                        )
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PaymentHelperUiState()
            )

    fun initialize(yearMonth: YearMonth) {
        if (selectedYearMonth.value == yearMonth) return
        selectedYearMonth.value = yearMonth
    }
}
