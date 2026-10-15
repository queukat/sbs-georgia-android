package com.queukat.sbsgeorgia.ui.months

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.usecase.CompleteMonthlyDeclarationUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MonthsViewModel
@Inject
constructor(
    observeAllSnapshotsUseCase: ObserveAllSnapshotsUseCase,
    private val actionPlanner: MonthlyDeclarationActionPlanner,
    private val completeMonthlyDeclarationUseCase: CompleteMonthlyDeclarationUseCase
) : ViewModel() {
    private val completionFailed = MutableStateFlow(false)
    private var isCompleting = false

    val uiState =
        observeAllSnapshotsUseCase()
            .map { snapshots ->
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
                                        val actionState = actionPlanner.plan(snapshot)
                                        MonthsMonthItemUiState(
                                            snapshot = snapshot,
                                            canQuickSettleMonth = actionState.canQuickSettleMonth,
                                            monthAlreadySettled = actionState.monthAlreadySettled,
                                            filingOpensOn = actionState.filingOpensOn,
                                            paymentRequired = actionState.paymentRequired
                                        )
                                    }
                            )
                        }
                )
            }.combine(completionFailed) { state, failed -> state.copy(completionFailed = failed) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MonthsUiState()
            )

    fun settleMonth(yearMonth: YearMonth) {
        if (isCompleting) return
        val monthItem =
            uiState.value.sections
                .flatMap(MonthsYearSection::items)
                .firstOrNull { it.snapshot.period.incomeMonth == yearMonth }
                ?: return
        if (!monthItem.canQuickSettleMonth) return
        val snapshot = monthItem.snapshot

        isCompleting = true
        completionFailed.value = false
        viewModelScope.launch {
            try {
                completeMonthlyDeclarationUseCase(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completionFailed.value = true
            } finally {
                isCompleting = false
            }
        }
    }
}
