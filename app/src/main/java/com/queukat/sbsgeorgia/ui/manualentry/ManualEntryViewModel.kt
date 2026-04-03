package com.queukat.sbsgeorgia.ui.manualentry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.usecase.UpsertManualIncomeEntryUseCase
import com.queukat.sbsgeorgia.ui.common.canonicalSourceCategory
import com.queukat.sbsgeorgia.ui.common.displaySourceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val upsertManualIncomeEntryUseCase: UpsertManualIncomeEntryUseCase,
    @ApplicationContext private val appContext: Context,
    private val clock: Clock,
) : ViewModel() {
    private var initializedEntryId: Long? = Long.MIN_VALUE

    private val _uiState = MutableStateFlow(
        ManualEntryUiState(
            incomeDate = LocalDate.now(clock),
            sourceCategory = displaySourceCategory(appContext, SourceCategoryPresets.SOFTWARE_SERVICES),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ManualEntryEffect>()
    val effects = _effects.asSharedFlow()

    fun initialize(entryId: Long?) {
        if (initializedEntryId == entryId) return
        initializedEntryId = entryId
        if (entryId == null) {
            _uiState.value = ManualEntryUiState(
                entryId = null,
                incomeDate = LocalDate.now(clock),
                sourceCategory = displaySourceCategory(appContext, SourceCategoryPresets.SOFTWARE_SERVICES),
            )
            return
        }
        viewModelScope.launch {
            incomeRepository.getById(entryId)?.let { entry ->
                _uiState.value = ManualEntryUiState(
                    entryId = entry.id,
                    incomeDate = entry.incomeDate,
                    amount = entry.originalAmount.toPlainString(),
                    currency = entry.originalCurrency,
                    sourceCategory = displaySourceCategory(appContext, entry.sourceCategory),
                    note = entry.note,
                    declarationIncluded = entry.declarationInclusion == DeclarationInclusion.INCLUDED,
                )
            }
        }
    }

    fun updateDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(incomeDate = date, errorMessage = null)
    }

    fun updateAmount(amount: String) {
        _uiState.value = _uiState.value.copy(amount = amount, errorMessage = null)
    }

    fun updateCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(currency = currency, errorMessage = null)
    }

    fun updateCategory(category: String) {
        _uiState.value = _uiState.value.copy(sourceCategory = category, errorMessage = null)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note, errorMessage = null)
    }

    fun updateIncluded(included: Boolean) {
        _uiState.value = _uiState.value.copy(declarationIncluded = included)
    }

    fun save() {
        val current = _uiState.value
        val amount = runCatching { BigDecimal(current.amount) }.getOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.value = current.copy(errorMessage = appContext.getString(R.string.manual_entry_error_amount_required))
            return
        }
        if (current.sourceCategory.isBlank()) {
            _uiState.value = current.copy(errorMessage = appContext.getString(R.string.manual_entry_error_category_required))
            return
        }

        _uiState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val existing = current.entryId?.let { incomeRepository.getById(it) }
            upsertManualIncomeEntryUseCase(
                IncomeEntry(
                    id = current.entryId ?: 0L,
                    sourceType = IncomeSourceType.MANUAL,
                    incomeDate = current.incomeDate,
                    originalAmount = amount,
                    originalCurrency = current.currency,
                    sourceCategory = canonicalSourceCategory(appContext, current.sourceCategory),
                    note = current.note.trim(),
                    declarationInclusion = if (current.declarationIncluded) {
                        DeclarationInclusion.INCLUDED
                    } else {
                        DeclarationInclusion.EXCLUDED
                    },
                    gelEquivalent = if (current.currency.equals("GEL", ignoreCase = true)) {
                        amount
                    } else {
                        existing?.gelEquivalent
                    },
                    rateSource = existing?.rateSource ?: FxRateSource.NONE,
                    manualFxOverride = existing?.manualFxOverride ?: false,
                    sourceStatementId = existing?.sourceStatementId,
                    sourceTransactionFingerprint = existing?.sourceTransactionFingerprint,
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: clock.millis(),
                    updatedAtEpochMillis = clock.millis(),
                ),
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            _effects.emit(ManualEntryEffect.Saved)
        }
    }
}
