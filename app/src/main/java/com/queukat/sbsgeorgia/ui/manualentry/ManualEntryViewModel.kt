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
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import com.queukat.sbsgeorgia.domain.model.normalizeCurrencyCode
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ManualEntryViewModel
@Inject
constructor(
    private val incomeRepository: IncomeRepository,
    private val upsertManualIncomeEntryUseCase: UpsertManualIncomeEntryUseCase,
    @param:ApplicationContext private val appContext: Context,
    private val clock: Clock
) : ViewModel() {
    private var initializedEntryKey: Pair<Long?, LocalDate?>? = null

    private val _uiState =
        MutableStateFlow(
            ManualEntryUiState(
                incomeDate = LocalDate.now(clock),
                sourceCategory = displaySourceCategory(
                    appContext,
                    SourceCategoryPresets.SOFTWARE_SERVICES
                )
            )
        )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ManualEntryEffect>()
    val effects = _effects.asSharedFlow()

    fun initialize(entryId: Long?, initialDate: LocalDate?) {
        val key = entryId to initialDate
        if (initializedEntryKey == key) return
        initializedEntryKey = key
        if (entryId == null) {
            _uiState.value =
                ManualEntryUiState(
                    entryId = null,
                    incomeDate = initialDate ?: LocalDate.now(clock),
                    sourceCategory = displaySourceCategory(
                        appContext,
                        SourceCategoryPresets.SOFTWARE_SERVICES
                    )
                )
            return
        }
        _uiState.value = _uiState.value.copy(entryId = entryId, isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val entry = incomeRepository.getById(entryId)
                if (initializedEntryKey != key) return@launch
                if (entry == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.manual_entry_not_found)
                    )
                    return@launch
                }

                _uiState.value =
                    ManualEntryUiState(
                        entryId = entry.id,
                        incomeDate = entry.incomeDate,
                        amount = entry.originalAmount.toPlainString(),
                        currency = entry.originalCurrency,
                        sourceCategory = displaySourceCategory(
                            appContext,
                            entry.sourceCategory
                        ),
                        note = entry.note,
                        declarationIncluded =
                        entry.declarationInclusion == DeclarationInclusion.INCLUDED
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (initializedEntryKey == key) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.operation_failed)
                    )
                }
            }
        }
    }

    fun updateDate(date: LocalDate) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(incomeDate = date, errorMessage = null)
    }

    fun updateAmount(amount: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(amount = amount, errorMessage = null)
    }

    fun updateCurrency(currency: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value =
            _uiState.value.copy(currency = normalizeCurrencyCode(currency), errorMessage = null)
    }

    fun updateCategory(category: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(sourceCategory = category, errorMessage = null)
    }

    fun updateNote(note: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(note = note, errorMessage = null)
    }

    fun updateIncluded(included: Boolean) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(declarationIncluded = included)
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return
        val amount = runCatching { BigDecimal(current.amount.trim().replace(',', '.')) }.getOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.value =
                current.copy(
                    errorMessage = appContext.getString(
                        R.string.manual_entry_error_amount_required
                    )
                )
            return
        }
        if (!isIsoLikeCurrencyCode(current.currency)) {
            _uiState.value =
                current.copy(
                    errorMessage = appContext.getString(
                        R.string.manual_entry_error_currency_invalid
                    )
                )
            return
        }
        if (current.sourceCategory.isBlank()) {
            _uiState.value =
                current.copy(
                    errorMessage = appContext.getString(
                        R.string.manual_entry_error_category_required
                    )
                )
            return
        }

        _uiState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val existing = current.entryId?.let { incomeRepository.getById(it) }
                if (current.entryId != null && existing == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = appContext.getString(R.string.manual_entry_not_found)
                    )
                    return@launch
                }
                val fxPersistence =
                    resolveManualEntryFxPersistence(
                        currency = current.currency,
                        amount = amount,
                        incomeDate = current.incomeDate,
                        existing = existing
                    )
                upsertManualIncomeEntryUseCase(
                    IncomeEntry(
                        id = current.entryId ?: 0L,
                        sourceType = resolvedManualEntrySourceType(existing),
                        incomeDate = current.incomeDate,
                        originalAmount = amount,
                        originalCurrency = fxPersistence.normalizedCurrency,
                        sourceCategory = canonicalSourceCategory(
                            appContext,
                            current.sourceCategory
                        ),
                        note = current.note.trim(),
                        declarationInclusion =
                        if (current.declarationIncluded) {
                            DeclarationInclusion.INCLUDED
                        } else {
                            DeclarationInclusion.EXCLUDED
                        },
                        gelEquivalent = fxPersistence.gelEquivalent,
                        rateSource = fxPersistence.rateSource,
                        manualFxOverride = fxPersistence.manualFxOverride,
                        sourceStatementId = existing?.sourceStatementId,
                        sourceTransactionFingerprint = existing?.sourceTransactionFingerprint,
                        createdAtEpochMillis = existing?.createdAtEpochMillis ?: clock.millis(),
                        updatedAtEpochMillis = clock.millis()
                    )
                )
                _effects.emit(ManualEntryEffect.Saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = appContext.getString(R.string.operation_failed))
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }
}

internal fun resolvedManualEntrySourceType(existing: IncomeEntry?): IncomeSourceType =
    if (!existing?.sourceTransactionFingerprint.isNullOrBlank()) {
        IncomeSourceType.IMPORTED_STATEMENT
    } else {
        existing?.sourceType ?: IncomeSourceType.MANUAL
    }

internal data class ManualEntryFxPersistence(
    val normalizedCurrency: String,
    val gelEquivalent: BigDecimal?,
    val rateSource: FxRateSource,
    val manualFxOverride: Boolean
)

internal fun resolveManualEntryFxPersistence(
    currency: String,
    amount: BigDecimal,
    incomeDate: LocalDate,
    existing: IncomeEntry?
): ManualEntryFxPersistence {
    val normalizedCurrency = normalizeCurrencyCode(currency)
    val isGel = normalizedCurrency == "GEL"
    val existingCurrency = existing?.originalCurrency?.let(::normalizeCurrencyCode)
    val fxFieldsChanged =
        existing == null ||
            existingCurrency != normalizedCurrency ||
            existing.originalAmount.compareTo(amount) != 0 ||
            existing.incomeDate != incomeDate

    return ManualEntryFxPersistence(
        normalizedCurrency = normalizedCurrency,
        gelEquivalent =
        when {
            isGel -> amount
            fxFieldsChanged -> null
            else -> existing.gelEquivalent
        },
        rateSource =
        when {
            isGel || fxFieldsChanged -> FxRateSource.NONE
            else -> existing.rateSource
        },
        manualFxOverride =
        when {
            isGel || fxFieldsChanged -> false
            else -> existing.manualFxOverride
        }
    )
}
