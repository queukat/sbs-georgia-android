package com.queukat.sbsgeorgia.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {
    private val selectedYearMonth = MutableStateFlow<YearMonth?>(null)

    val uiState = selectedYearMonth
        .filterNotNull()
        .flatMapLatest { yearMonth ->
            observePaymentHelperUseCase(yearMonth)
                .map { data ->
                    val snapshot = data.snapshot
                    val readinessState = when {
                        snapshot == null -> PaymentHelperReadinessState.MONTH_UNAVAILABLE
                        snapshot.period.outOfScope -> PaymentHelperReadinessState.OUT_OF_SCOPE
                        snapshot.reviewNeeded -> PaymentHelperReadinessState.REVIEW_REQUIRED
                        snapshot.unresolvedFxCount > 0 -> PaymentHelperReadinessState.UNRESOLVED_FX
                        snapshot.zeroDeclarationSuggested -> PaymentHelperReadinessState.ZERO_DECLARATION
                        else -> PaymentHelperReadinessState.READY
                    }
                    PaymentHelperUiState(
                        data = data,
                        readinessState = readinessState,
                        isReady = snapshot != null &&
                            !snapshot.period.outOfScope &&
                            !snapshot.reviewNeeded &&
                            snapshot.unresolvedFxCount == 0,
                    )
                }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PaymentHelperUiState(),
        )

    fun initialize(yearMonth: YearMonth) {
        if (selectedYearMonth.value == yearMonth) return
        selectedYearMonth.value = yearMonth
    }
}
