package com.queukat.sbsgeorgia.ui.monthdetails

import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.usecase.DeclarationCopyBundle
import java.time.YearMonth

data class MonthDetailUiState(
    val yearMonth: YearMonth? = null,
    val snapshot: MonthlyDeclarationSnapshot? = null,
    val entries: List<IncomeEntry> = emptyList(),
    val copyBundle: DeclarationCopyBundle? = null,
    val isFilingWindowOpen: Boolean = false,
    val isResolvingFx: Boolean = false,
)

sealed interface MonthDetailEffect {
    data class Message(val text: String) : MonthDetailEffect
}
