package com.queukat.sbsgeorgia.ui.months

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import java.time.LocalDate

data class MonthsMonthItemUiState(
    val snapshot: MonthlyDeclarationSnapshot,
    val canQuickMarkDeclarationFiled: Boolean,
    val declarationAlreadyFiled: Boolean,
    val filingOpensOn: LocalDate? = null,
)

data class MonthsYearSection(
    val year: Int,
    val items: List<MonthsMonthItemUiState>,
)

data class MonthsUiState(
    val sections: List<MonthsYearSection> = emptyList(),
)
