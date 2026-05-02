package com.queukat.sbsgeorgia.ui.home

import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.usecase.DeclarationCopyBundle
import java.time.LocalDate

data class HomeUiState(
    val summary: DashboardSummary? = null,
    val duePeriodQuickAccess: HomeDuePeriodQuickAccess? = null,
)

data class HomeDuePeriodQuickAccess(
    val snapshot: MonthlyDeclarationSnapshot,
    val copyBundle: DeclarationCopyBundle?,
    val canCopyDeclarationValues: Boolean,
    val canQuickSettleMonth: Boolean,
    val monthAlreadySettled: Boolean,
    val filingOpensOn: LocalDate?,
)
