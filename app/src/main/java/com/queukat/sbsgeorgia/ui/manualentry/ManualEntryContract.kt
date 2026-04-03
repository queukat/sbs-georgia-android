package com.queukat.sbsgeorgia.ui.manualentry

import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import java.time.LocalDate

data class ManualEntryUiState(
    val entryId: Long? = null,
    val incomeDate: LocalDate,
    val amount: String = "",
    val currency: String = "GEL",
    val sourceCategory: String = SourceCategoryPresets.SOFTWARE_SERVICES,
    val note: String = "",
    val declarationIncluded: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ManualEntryEffect {
    data object Saved : ManualEntryEffect
}
