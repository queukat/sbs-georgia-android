package com.queukat.sbsgeorgia.ui.fxoverride

import java.time.LocalDate

data class FxOverrideUiState(
    val entryId: Long? = null,
    val incomeDate: LocalDate? = null,
    val originalAmount: String = "",
    val originalCurrency: String = "",
    val units: String = "1",
    val rateToGel: String = "",
    val previewGelEquivalent: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed interface FxOverrideEffect {
    data object Saved : FxOverrideEffect
}
