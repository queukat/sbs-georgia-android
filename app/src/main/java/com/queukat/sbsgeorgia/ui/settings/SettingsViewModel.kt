package com.queukat.sbsgeorgia.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.ExportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportIncomeEntriesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportMonthlySummariesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ImportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertSettingsUseCase
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val defaultReminderDays = listOf(10, 13, 15)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val upsertSettingsUseCase: UpsertSettingsUseCase,
    private val exportIncomeEntriesCsvUseCase: ExportIncomeEntriesCsvUseCase,
    private val exportMonthlySummariesCsvUseCase: ExportMonthlySummariesCsvUseCase,
    private val exportBackupJsonUseCase: ExportBackupJsonUseCase,
    private val importBackupJsonUseCase: ImportBackupJsonUseCase,
    private val textDocumentStore: TextDocumentStore,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val appContext: Context,
    private val clock: Clock,
) : ViewModel() {
    private var persistedProfile: TaxpayerProfile? = null
    private var persistedStatusConfig: SmallBusinessStatusConfig? = null

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            effectiveDate = LocalDate.now(clock),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            loadCurrentSettings()
        }
    }

    fun updateRegistrationId(value: String) {
        _uiState.value = _uiState.value.copy(registrationId = value, errorMessage = null)
    }

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value, errorMessage = null)
    }

    fun updateEffectiveDate(value: LocalDate) {
        _uiState.value = _uiState.value.copy(effectiveDate = value, errorMessage = null)
    }

    fun updateTaxRatePercent(value: String) {
        _uiState.value = _uiState.value.copy(taxRatePercent = value, errorMessage = null)
    }

    fun updateDefaultReminderTime(value: String) {
        _uiState.value = _uiState.value.copy(defaultReminderTime = value, errorMessage = null)
    }

    fun updateDeclarationReminderDays(value: String) {
        _uiState.value = _uiState.value.copy(declarationReminderDays = value, errorMessage = null)
    }

    fun updatePaymentReminderDays(value: String) {
        _uiState.value = _uiState.value.copy(paymentReminderDays = value, errorMessage = null)
    }

    fun updateDeclarationEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(declarationRemindersEnabled = enabled)
    }

    fun updatePaymentEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(paymentRemindersEnabled = enabled)
    }

    fun updateThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch {
            val persistedConfig = settingsRepository.observeReminderConfig().first()
            settingsRepository.upsertReminderConfig(
                persistedConfig?.copy(themeMode = mode) ?: ReminderConfig(
                    declarationReminderDays = defaultReminderDays,
                    paymentReminderDays = defaultReminderDays,
                    declarationRemindersEnabled = true,
                    paymentRemindersEnabled = true,
                    defaultReminderTime = LocalTime.of(9, 0),
                    themeMode = mode,
                ),
            )
        }
    }

    fun save() {
        val current = _uiState.value
        val taxRate = runCatching { BigDecimal(current.taxRatePercent) }.getOrNull()
        val defaultReminderTime = runCatching { LocalTime.parse(current.defaultReminderTime) }.getOrNull()
        val declarationReminderDays = parseReminderDays(current.declarationReminderDays)
        val paymentReminderDays = parseReminderDays(current.paymentReminderDays)
        when {
            current.registrationId.isBlank() -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.settings_error_registration_id_required))
            }
            current.displayName.isBlank() -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.settings_error_display_name_required))
            }
            taxRate == null || taxRate < BigDecimal.ZERO -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.settings_error_tax_rate_invalid))
            }
            defaultReminderTime == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.settings_error_reminder_time_invalid))
            }
            declarationReminderDays == null -> {
                _uiState.value = current.copy(
                    errorMessage = appContext.getString(R.string.settings_error_declaration_days_invalid),
                )
            }
            paymentReminderDays == null -> {
                _uiState.value = current.copy(
                    errorMessage = appContext.getString(R.string.settings_error_payment_days_invalid),
                )
            }
            else -> {
                _uiState.value = current.copy(isSaving = true, errorMessage = null)
                viewModelScope.launch {
                    val reminders = ReminderConfig(
                        declarationReminderDays = declarationReminderDays,
                        paymentReminderDays = paymentReminderDays,
                        declarationRemindersEnabled = current.declarationRemindersEnabled,
                        paymentRemindersEnabled = current.paymentRemindersEnabled,
                        defaultReminderTime = defaultReminderTime,
                        themeMode = current.themeMode,
                    )
                    upsertSettingsUseCase(
                        profile = (persistedProfile ?: TaxpayerProfile(
                            registrationId = current.registrationId.trim(),
                            displayName = current.displayName.trim(),
                        )).copy(
                            registrationId = current.registrationId.trim(),
                            displayName = current.displayName.trim(),
                        ),
                        config = (persistedStatusConfig ?: SmallBusinessStatusConfig(
                            effectiveDate = current.effectiveDate,
                            defaultTaxRatePercent = taxRate,
                        )).copy(
                            effectiveDate = current.effectiveDate,
                            defaultTaxRatePercent = taxRate,
                        ),
                        reminders = reminders,
                    )
                    reminderScheduler.reschedule(reminders)
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _effects.emit(SettingsEffect.Saved)
                }
            }
        }
    }

    fun exportIncomeEntriesCsv(uriString: String) {
        runDataOperation {
            textDocumentStore.writeText(uriString, exportIncomeEntriesCsvUseCase())
            _effects.emit(SettingsEffect.Message(appContext.getString(R.string.settings_message_income_csv_exported)))
        }
    }

    fun exportMonthlySummariesCsv(uriString: String) {
        runDataOperation {
            textDocumentStore.writeText(uriString, exportMonthlySummariesCsvUseCase())
            _effects.emit(SettingsEffect.Message(appContext.getString(R.string.settings_message_monthly_csv_exported)))
        }
    }

    fun exportBackupJson(uriString: String) {
        runDataOperation {
            textDocumentStore.writeText(uriString, exportBackupJsonUseCase())
            _effects.emit(SettingsEffect.Message(appContext.getString(R.string.settings_message_backup_exported)))
        }
    }

    fun importBackupJson(uriString: String) {
        runDataOperation {
            val content = textDocumentStore.readText(uriString)
            val result = importBackupJsonUseCase(content)
            val importedReminderConfig = settingsRepository.observeReminderConfig().first()
            if (importedReminderConfig != null) {
                reminderScheduler.reschedule(importedReminderConfig)
            } else {
                reminderScheduler.cancelAll()
            }
            loadCurrentSettings()
            _effects.emit(SettingsEffect.Message(result.toSummaryMessage(appContext)))
        }
    }

    private fun runDataOperation(block: suspend () -> Unit) {
        if (_uiState.value.isDataOperationInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDataOperationInProgress = true)
            runCatching {
                block()
            }.onFailure {
                _effects.emit(
                    SettingsEffect.Message(
                        appContext.getString(R.string.settings_message_data_operation_failed),
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(isDataOperationInProgress = false)
        }
    }

    private fun parseReminderDays(value: String): List<Int>? {
        val parts = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val days = parts.map { it.toIntOrNull() ?: return null }
        if (days.any { it !in 1..15 }) return null
        return days.distinct().sorted()
    }

    private suspend fun loadCurrentSettings() {
        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        val reminderConfig = settingsRepository.observeReminderConfig().first()
        persistedProfile = profile
        persistedStatusConfig = config
        _uiState.value = _uiState.value.copy(
            registrationId = profile?.registrationId.orEmpty(),
            displayName = profile?.displayName.orEmpty(),
            effectiveDate = config?.effectiveDate ?: LocalDate.now(clock),
            taxRatePercent = config?.defaultTaxRatePercent?.toPlainString() ?: "1.0",
            defaultReminderTime = reminderConfig?.defaultReminderTime?.toString() ?: "09:00",
            declarationReminderDays = reminderConfig?.declarationReminderDays?.joinToString(",")
                ?: defaultReminderDays.joinToString(","),
            paymentReminderDays = reminderConfig?.paymentReminderDays?.joinToString(",")
                ?: defaultReminderDays.joinToString(","),
            declarationRemindersEnabled = reminderConfig?.declarationRemindersEnabled ?: true,
            paymentRemindersEnabled = reminderConfig?.paymentRemindersEnabled ?: true,
            themeMode = reminderConfig?.themeMode ?: ThemeMode.SYSTEM,
            errorMessage = null,
        )
    }
}

private fun BackupRestoreResult.toSummaryMessage(context: Context): String = context.getString(
    R.string.settings_message_backup_imported_summary,
    incomeEntryCount,
    monthlyRecordCount,
    importedStatementCount,
    importedTransactionCount,
)
