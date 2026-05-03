package com.queukat.sbsgeorgia.ui.months

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MonthsViewModel
@Inject
constructor(
    observeAllSnapshotsUseCase: ObserveAllSnapshotsUseCase,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock
) : ViewModel() {
    val uiState =
        observeAllSnapshotsUseCase()
            .map { snapshots ->
                val today = LocalDate.now(clock)
                MonthsUiState(
                    sections =
                    snapshots
                        .groupBy { it.period.incomeMonth.year }
                        .entries
                        .sortedByDescending { it.key }
                        .map { (year, yearSnapshots) ->
                            MonthsYearSection(
                                year = year,
                                items =
                                yearSnapshots
                                    .sortedByDescending { it.period.incomeMonth }
                                    .map { snapshot ->
                                        val filingWindowOpen =
                                            planner.isFilingWindowOpen(
                                                period = snapshot.period,
                                                referenceDate = today
                                            )
                                        val alreadySettled =
                                            snapshot.workflowStatus in settledStatuses
                                        MonthsMonthItemUiState(
                                            snapshot = snapshot,
                                            canQuickSettleMonth =
                                            !snapshot.period.outOfScope &&
                                                filingWindowOpen &&
                                                !alreadySettled,
                                            monthAlreadySettled = alreadySettled,
                                            filingOpensOn =
                                            if (!snapshot.period.outOfScope &&
                                                !filingWindowOpen
                                            ) {
                                                snapshot.period.filingWindow.start
                                            } else {
                                                null
                                            }
                                        )
                                    }
                            )
                        }
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MonthsUiState()
            )

    fun settleMonth(yearMonth: YearMonth) {
        val monthItem =
            uiState.value.sections
                .flatMap(MonthsYearSection::items)
                .firstOrNull { it.snapshot.period.incomeMonth == yearMonth }
                ?: return
        if (!monthItem.canQuickSettleMonth) return
        val snapshot = monthItem.snapshot
        val today = LocalDate.now(clock)

        viewModelScope.launch {
            upsertMonthlyDeclarationRecordUseCase(
                MonthlyDeclarationRecord(
                    yearMonth = yearMonth,
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared =
                    snapshot.zeroDeclarationPrepared || snapshot.zeroDeclarationSuggested,
                    declarationFiledDate = snapshot.record?.declarationFiledDate ?: today,
                    paymentSentDate = snapshot.record?.paymentSentDate ?: today,
                    paymentCreditedDate = snapshot.record?.paymentCreditedDate ?: today,
                    paymentAmountGel =
                    snapshot.record?.paymentAmountGel
                        ?: snapshot.estimatedTaxAmountGel
                        ?: BigDecimal.ZERO.setScale(2),
                    notes = snapshot.record?.notes.orEmpty()
                )
            )
        }
    }

    private companion object {
        val settledStatuses =
            setOf(
                MonthlyWorkflowStatus.PAYMENT_SENT,
                MonthlyWorkflowStatus.PAYMENT_CREDITED,
                MonthlyWorkflowStatus.SETTLED
            )
    }
}
