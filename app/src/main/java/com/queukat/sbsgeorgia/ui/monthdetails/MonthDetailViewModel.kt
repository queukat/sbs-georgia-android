package com.queukat.sbsgeorgia.ui.monthdetails

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.requiresFxResolution
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveMonthDetailUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveMonthFxUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertMonthlyDeclarationRecordUseCase
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MonthDetailViewModel
@Inject
constructor(
    observeMonthDetailUseCase: ObserveMonthDetailUseCase,
    settingsRepository: SettingsRepository,
    private val upsertMonthlyDeclarationRecordUseCase: UpsertMonthlyDeclarationRecordUseCase,
    private val incomeRepository: IncomeRepository,
    private val resolveMonthFxUseCase: ResolveMonthFxUseCase,
    private val planner: MonthlyDeclarationPlanner,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    private val selectedYearMonth = MutableStateFlow<YearMonth?>(null)
    private val isResolvingFx = MutableStateFlow(false)
    private val autoResolvedMonths = mutableSetOf<YearMonth>()
    private val _effects = MutableSharedFlow<MonthDetailEffect>()
    val effects = _effects.asSharedFlow()

    val uiState =
        combine(
            selectedYearMonth
                .filterNotNull()
                .flatMapLatest { yearMonth ->
                    combine(
                        observeMonthDetailUseCase(yearMonth),
                        settingsRepository.observeTaxpayerProfile()
                    ) { (snapshot, entries), profile ->
                        val registrationId = profile?.registrationId
                        val filingWindowOpen =
                            snapshot?.let { planner.isFilingWindowOpen(it.period) } ?: false
                        val copyBundle =
                            buildDeclarationCopyBundle(
                                snapshot = snapshot,
                                registrationId = registrationId,
                                yearMonth = yearMonth
                            )
                        MonthDetailUiState(
                            yearMonth = yearMonth,
                            snapshot = snapshot,
                            entries = entries,
                            copyBundle = copyBundle,
                            isFilingWindowOpen = filingWindowOpen
                        )
                    }
                },
            isResolvingFx
        ) { detailState, resolving ->
            detailState.copy(isResolvingFx = resolving)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthDetailUiState()
        )

    fun initialize(yearMonth: YearMonth) {
        if (selectedYearMonth.value == yearMonth) return
        selectedYearMonth.value = yearMonth
        attemptAutomaticFxResolution(yearMonth)
    }

    fun resolveOfficialRates() {
        if (isResolvingFx.value) return
        viewModelScope.launch {
            resolveOfficialRatesInternal(
                entries = uiState.value.entries,
                emitFeedback = true
            )
        }
    }

    fun toggleZeroPrepared() {
        val snapshot = uiState.value.snapshot ?: return
        viewModelScope.launch {
            upsertMonthlyDeclarationRecordUseCase(
                MonthlyDeclarationRecord(
                    yearMonth = snapshot.period.incomeMonth,
                    workflowStatus =
                    snapshot.record?.workflowStatus ?: MonthlyWorkflowStatus.DRAFT,
                    zeroDeclarationPrepared = !snapshot.zeroDeclarationPrepared,
                    declarationFiledDate = snapshot.record?.declarationFiledDate,
                    paymentSentDate = snapshot.record?.paymentSentDate,
                    paymentCreditedDate = snapshot.record?.paymentCreditedDate,
                    paymentAmountGel = snapshot.record?.paymentAmountGel,
                    notes = snapshot.record?.notes.orEmpty()
                )
            )
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            incomeRepository.deleteById(entryId)
        }
    }

    private fun attemptAutomaticFxResolution(yearMonth: YearMonth) {
        if (!autoResolvedMonths.add(yearMonth)) return
        viewModelScope.launch {
            val monthEntries = incomeRepository.observeByMonth(yearMonth).first()
            if (monthEntries.none(IncomeEntry::requiresFxResolution)) return@launch
            resolveOfficialRatesInternal(
                entries = monthEntries,
                emitFeedback = false
            )
        }
    }

    private suspend fun resolveOfficialRatesInternal(
        entries: List<com.queukat.sbsgeorgia.domain.model.IncomeEntry>,
        emitFeedback: Boolean
    ) {
        if (isResolvingFx.value) return
        isResolvingFx.value = true
        try {
            val result = resolveMonthFxUseCase(entries)
            if (emitFeedback) {
                val message =
                    when {
                        result.resolvedEntryCount > 0 && result.unresolvedEntryCount == 0 ->
                            appContext.getString(
                                R.string.fx_resolve_resolved_all,
                                result.resolvedEntryCount
                            )
                        result.resolvedEntryCount > 0 ->
                            appContext.getString(
                                R.string.fx_resolve_partial,
                                result.resolvedEntryCount,
                                result.unresolvedEntryCount
                            )
                        result.unresolvedEntryCount > 0 ->
                            appContext.getString(
                                R.string.fx_resolve_manual_review_needed,
                                result.unresolvedEntryCount
                            )
                        else -> appContext.getString(R.string.fx_resolve_none)
                    }
                _effects.emit(MonthDetailEffect.Message(message))
            }
        } finally {
            isResolvingFx.value = false
        }
    }
}
