package com.queukat.sbsgeorgia.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentParseException
import com.queukat.sbsgeorgia.domain.model.OnboardingParseError
import com.queukat.sbsgeorgia.domain.service.ReminderNotification
import com.queukat.sbsgeorgia.domain.service.ReminderPlanner
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportIncomeEntriesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportMonthlySummariesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ImportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadOnboardingDocumentPreviewUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertSettingsUseCase
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import com.queukat.sbsgeorgia.worker.ReminderTestScheduler
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
    private val loadOnboardingDocumentPreviewUseCase: LoadOnboardingDocumentPreviewUseCase,
    private val exportIncomeEntriesCsvUseCase: ExportIncomeEntriesCsvUseCase,
    private val exportMonthlySummariesCsvUseCase: ExportMonthlySummariesCsvUseCase,
    private val exportBackupJsonUseCase: ExportBackupJsonUseCase,
    private val importBackupJsonUseCase: ImportBackupJsonUseCase,
    private val textDocumentStore: TextDocumentStore,
    private val observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val reminderPlanner: ReminderPlanner,
    private val reminderScheduler: ReminderScheduler,
    private val reminderTestScheduler: ReminderTestScheduler,
    @param:ApplicationContext private val appContext: Context,
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

    fun updateLegalForm(value: String) {
        _uiState.value = _uiState.value.copy(legalForm = value, errorMessage = null)
    }

    fun updateRegistrationDate(value: String) {
        _uiState.value = _uiState.value.copy(registrationDate = value, errorMessage = null)
    }

    fun updateLegalAddress(value: String) {
        _uiState.value = _uiState.value.copy(legalAddress = value, errorMessage = null)
    }

    fun updateActivityType(value: String) {
        _uiState.value = _uiState.value.copy(activityType = value, errorMessage = null)
    }

    fun updateCertificateNumber(value: String) {
        _uiState.value = _uiState.value.copy(certificateNumber = value, errorMessage = null)
    }

    fun updateCertificateIssuedDate(value: String) {
        _uiState.value = _uiState.value.copy(certificateIssuedDate = value, errorMessage = null)
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

    fun loadDocument(uri: Uri, action: SettingsDocumentImportAction) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDocumentLoading = true,
                documentInfoMessage = null,
                documentErrorMessage = null,
            )
            runCatching {
                loadOnboardingDocumentPreviewUseCase(
                    uriString = uri.toString(),
                    expectedDocumentType = action.expectedDocumentType(),
                )
            }.onSuccess { preview ->
                _uiState.value = _uiState.value.copy(
                    isDocumentLoading = false,
                    preview = preview,
                    documentInfoMessage = when (preview.documentType) {
                        com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType.REGISTRY_EXTRACT ->
                            appContext.getString(R.string.onboarding_registry_recognized)
                        com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE ->
                            appContext.getString(R.string.onboarding_certificate_recognized)
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isDocumentLoading = false,
                    preview = null,
                    documentErrorMessage = when ((error as? OnboardingDocumentParseException)?.reason) {
                        OnboardingParseError.UNSUPPORTED_DOCUMENT ->
                            appContext.getString(R.string.onboarding_error_unsupported_document)
                        OnboardingParseError.EXPECTED_REGISTRY_EXTRACT ->
                            appContext.getString(R.string.onboarding_error_expected_registry_extract)
                        OnboardingParseError.EXPECTED_SMALL_BUSINESS_CERTIFICATE ->
                            appContext.getString(R.string.onboarding_error_expected_sbs_certificate)
                        null -> appContext.getString(R.string.onboarding_error_parse_failed)
                    },
                )
            }
        }
    }

    fun applyPreview() {
        val preview = _uiState.value.preview ?: return
        _uiState.value = _uiState.value.copy(
            displayName = preview.displayName.value ?: _uiState.value.displayName,
            legalForm = preview.legalForm.value ?: _uiState.value.legalForm,
            registrationId = preview.registrationId.value ?: _uiState.value.registrationId,
            registrationDate = preview.registrationDate.value?.toString() ?: _uiState.value.registrationDate,
            legalAddress = preview.legalAddress.value ?: _uiState.value.legalAddress,
            activityType = preview.activityType.value ?: _uiState.value.activityType,
            certificateNumber = preview.certificateNumber.value ?: _uiState.value.certificateNumber,
            certificateIssuedDate = preview.certificateIssuedDate.value?.toString() ?: _uiState.value.certificateIssuedDate,
            effectiveDate = preview.effectiveDate.value ?: _uiState.value.effectiveDate,
            documentInfoMessage = appContext.getString(R.string.onboarding_preview_applied),
            documentErrorMessage = null,
            errorMessage = null,
        )
    }

    fun save() {
        val current = _uiState.value
        val taxRate = runCatching { BigDecimal(current.taxRatePercent) }.getOrNull()
        val defaultReminderTime = runCatching { LocalTime.parse(current.defaultReminderTime) }.getOrNull()
        val registrationDate = current.registrationDate.parseOptionalDate()
        val certificateIssuedDate = current.certificateIssuedDate.parseOptionalDate()
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
            current.registrationDate.isNotBlank() && registrationDate == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_registration_date_invalid))
            }
            current.certificateIssuedDate.isNotBlank() && certificateIssuedDate == null -> {
                _uiState.value = current.copy(errorMessage = appContext.getString(R.string.onboarding_error_certificate_issued_date_invalid))
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
                            legalForm = current.legalForm.trim().ifBlank { null },
                            registrationDate = registrationDate,
                            legalAddress = current.legalAddress.trim().ifBlank { null },
                            activityType = current.activityType.trim().ifBlank { null },
                        ),
                        config = (persistedStatusConfig ?: SmallBusinessStatusConfig(
                            effectiveDate = current.effectiveDate,
                            defaultTaxRatePercent = taxRate,
                        )).copy(
                            effectiveDate = current.effectiveDate,
                            defaultTaxRatePercent = taxRate,
                            certificateNumber = current.certificateNumber.trim().ifBlank { null },
                            certificateIssuedDate = certificateIssuedDate,
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

    fun scheduleTestReminder(
        type: ReminderType,
        delaySeconds: Long,
    ) {
        viewModelScope.launch {
            val dueSnapshot = observeDashboardSummaryUseCase().first().currentDuePeriod
            val notification = reminderPlanner.buildPreviewNotification(type, dueSnapshot)
                ?: fallbackTestNotification(type)
            reminderTestScheduler.schedule(
                reminderNotification = notification.copy(
                    notificationId = notification.notificationId ?: defaultNotificationId(type),
                ),
                delaySeconds = delaySeconds,
            )
            _effects.emit(
                SettingsEffect.Message(
                    appContext.getString(
                        R.string.settings_message_test_notification_scheduled,
                        type.label(appContext),
                        delaySeconds,
                    ),
                ),
            )
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
            preview = null,
            registrationId = profile?.registrationId.orEmpty(),
            displayName = profile?.displayName.orEmpty(),
            legalForm = profile?.legalForm.orEmpty(),
            registrationDate = profile?.registrationDate?.toString().orEmpty(),
            legalAddress = profile?.legalAddress.orEmpty(),
            activityType = profile?.activityType.orEmpty(),
            certificateNumber = config?.certificateNumber.orEmpty(),
            certificateIssuedDate = config?.certificateIssuedDate?.toString().orEmpty(),
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
            isDocumentLoading = false,
            documentInfoMessage = null,
            documentErrorMessage = null,
            errorMessage = null,
        )
    }

    private fun fallbackTestNotification(type: ReminderType): ReminderNotification = when (type) {
        ReminderType.DECLARATION -> ReminderNotification(
            type = type,
            title = appContext.getString(R.string.settings_test_notification_generic_declaration_title),
            body = appContext.getString(R.string.settings_test_notification_generic_declaration_body),
        )
        ReminderType.PAYMENT -> ReminderNotification(
            type = type,
            title = appContext.getString(R.string.settings_test_notification_generic_payment_title),
            body = appContext.getString(R.string.settings_test_notification_generic_payment_body),
        )
    }

    private fun defaultNotificationId(type: ReminderType): Int =
        ((clock.millis() + type.ordinal) and 0x7fffffff).toInt()
}

private fun BackupRestoreResult.toSummaryMessage(context: Context): String = context.getString(
    R.string.settings_message_backup_imported_summary,
    incomeEntryCount,
    monthlyRecordCount,
    importedStatementCount,
    importedTransactionCount,
)

private fun ReminderType.label(context: Context): String = context.getString(
    when (this) {
        ReminderType.DECLARATION -> R.string.settings_test_notification_type_declaration
        ReminderType.PAYMENT -> R.string.settings_test_notification_type_payment
    },
)

private fun String.parseOptionalDate(): LocalDate? =
    trim().takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
    }
