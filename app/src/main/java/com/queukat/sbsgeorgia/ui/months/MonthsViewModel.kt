package com.queukat.sbsgeorgia.ui.months

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MonthsViewModel @Inject constructor(
    observeAllSnapshotsUseCase: ObserveAllSnapshotsUseCase,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
) : ViewModel() {
    val uiState = observeAllSnapshotsUseCase()
        .map { snapshots ->
            val today = LocalDate.now(clock)
            MonthsUiState(
                sections = snapshots
                    .groupBy { it.period.incomeMonth.year }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (year, yearSnapshots) ->
                        MonthsYearSection(
                            year = year,
                            items = yearSnapshots
                                .sortedByDescending { it.period.incomeMonth }
                                .map { snapshot ->
                                    val filingWindowOpen = planner.isFilingWindowOpen(
                                        period = snapshot.period,
                                        referenceDate = today,
                                    )
                                    val alreadyFiled = snapshot.workflowStatus in declarationFiledStatuses
                                    MonthsMonthItemUiState(
                                        snapshot = snapshot,
                                        canQuickMarkDeclarationFiled = !snapshot.period.outOfScope &&
                                            filingWindowOpen &&
                                            !alreadyFiled,
                                        declarationAlreadyFiled = alreadyFiled,
                                        filingOpensOn = if (!snapshot.period.outOfScope && !filingWindowOpen) {
                                            snapshot.period.filingWindow.start
                                        } else {
                                            null
                                        },
                                    )
                                },
                        )
                    },
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthsUiState(),
        )

    fun markDeclarationFiled(yearMonth: YearMonth) {
        val monthItem = uiState.value.sections
            .flatMap(MonthsYearSection::items)
            .firstOrNull { it.snapshot.period.incomeMonth == yearMonth }
            ?: return
        if (!monthItem.canQuickMarkDeclarationFiled) return
        val snapshot = monthItem.snapshot

        viewModelScope.launch {
            upsertMonthlyDeclarationRecordUseCase(
                MonthlyDeclarationRecord(
                    yearMonth = yearMonth,
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    zeroDeclarationPrepared = snapshot.zeroDeclarationPrepared || snapshot.zeroDeclarationSuggested,
                    declarationFiledDate = snapshot.record?.declarationFiledDate ?: LocalDate.now(clock),
                    paymentSentDate = snapshot.record?.paymentSentDate,
                    paymentCreditedDate = snapshot.record?.paymentCreditedDate,
                    notes = snapshot.record?.notes.orEmpty(),
                ),
            )
        }
    }

    private companion object {
        val declarationFiledStatuses = setOf(
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }
}
