package com.queukat.sbsgeorgia.ui.fxoverride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.usecase.ApplyManualFxOverrideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FxOverrideViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val applyManualFxOverrideUseCase: ApplyManualFxOverrideUseCase,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {
    private var initializedEntryId: Long = Long.MIN_VALUE

    private val _uiState = MutableStateFlow(FxOverrideUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<FxOverrideEffect>()
    val effects = _effects.asSharedFlow()

    fun initialize(entryId: Long) {
        if (initializedEntryId == entryId) return
        initializedEntryId = entryId
        viewModelScope.launch {
            val entry = incomeRepository.getById(entryId)
            if (entry == null) {
                _uiState.value = FxOverrideUiState(errorMessage = appContext.getString(R.string.fx_override_error_not_found))
                return@launch
            }
            val initialRate = if (entry.manualFxOverride && entry.gelEquivalent != null && entry.originalAmount > BigDecimal.ZERO) {
                entry.gelEquivalent
                    .multiply(BigDecimal.ONE)
                    .divide(entry.originalAmount, 6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
            } else {
                ""
            }
            _uiState.value = FxOverrideUiState(
                entryId = entry.id,
                incomeDate = entry.incomeDate,
                originalAmount = entry.originalAmount.toPlainString(),
                originalCurrency = entry.originalCurrency,
                rateToGel = initialRate,
                previewGelEquivalent = entry.gelEquivalent?.toPlainString(),
            )
        }
    }

    fun updateUnits(value: String) {
        _uiState.value = _uiState.value.copy(units = value, errorMessage = null)
        updatePreview()
    }

    fun updateRateToGel(value: String) {
        _uiState.value = _uiState.value.copy(rateToGel = value, errorMessage = null)
        updatePreview()
    }

    fun save() {
        val current = _uiState.value
        val entryId = current.entryId
        val units = current.units.toIntOrNull()
        val rateToGel = runCatching { BigDecimal(current.rateToGel) }.getOrNull()

        when {
            entryId == null -> _uiState.value = current.copy(errorMessage = appContext.getString(R.string.fx_override_error_not_found))
            units == null || units <= 0 -> _uiState.value = current.copy(errorMessage = appContext.getString(R.string.fx_override_error_units))
            rateToGel == null || rateToGel <= BigDecimal.ZERO -> _uiState.value = current.copy(errorMessage = appContext.getString(R.string.fx_override_error_rate))
            else -> {
                _uiState.value = current.copy(isSaving = true, errorMessage = null)
                viewModelScope.launch {
                    applyManualFxOverrideUseCase(entryId, units, rateToGel)
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _effects.emit(FxOverrideEffect.Saved)
                }
            }
        }
    }

    private fun updatePreview() {
        val current = _uiState.value
        val amount = runCatching { BigDecimal(current.originalAmount) }.getOrNull()
        val units = current.units.toIntOrNull()
        val rateToGel = runCatching { BigDecimal(current.rateToGel) }.getOrNull()
        val preview = if (amount == null || units == null || units <= 0 || rateToGel == null) {
            null
        } else {
            amount.multiply(rateToGel).divide(BigDecimal(units), 2, RoundingMode.HALF_UP).toPlainString()
        }
        _uiState.value = current.copy(previewGelEquivalent = preview)
    }
}
