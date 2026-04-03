package com.queukat.sbsgeorgia.ui.payment

import com.queukat.sbsgeorgia.domain.usecase.PaymentHelperData

data class PaymentHelperUiState(
    val data: PaymentHelperData? = null,
    val readinessState: PaymentHelperReadinessState = PaymentHelperReadinessState.LOADING,
    val isReady: Boolean = false,
)

enum class PaymentHelperReadinessState {
    LOADING,
    MONTH_UNAVAILABLE,
    OUT_OF_SCOPE,
    REVIEW_REQUIRED,
    UNRESOLVED_FX,
    ZERO_DECLARATION,
    READY,
}
