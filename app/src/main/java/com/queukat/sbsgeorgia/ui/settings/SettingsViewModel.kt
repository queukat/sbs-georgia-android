package com.queukat.sbsgeorgia.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
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
import com.queukat.sbsgeorgia.domain.usecase.LoadOnboardingDocumentPreviewUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertSettingsUseCase
import com.queukat.sbsgeorgia.ui.common.backup.BackupRestoreController
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportAction
import com.queukat.sbsgeorgia.ui.common.document.documentImportStrings
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import com.queukat.sbsgeorgia.worker.ReminderTestScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val upsertSettingsUseCase: UpsertSettingsUseCase,
    private val loadOnboardingDocumentPreviewUseCase: LoadOnboardingDocumentPreviewUseCase,
    private val exportIncomeEntriesCsvUseCase: ExportIncomeEntriesCsvUseCase,
    private val exportMonthlySummariesCsvUseCase: ExportMonthlySummariesCsvUseCase,
    private val exportBackupJsonUseCase: ExportBackupJsonUseCase,
    private val textDocumentStore: TextDocumentStore,
    private val observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val reminderPlanner: ReminderPlanner,
    private val reminderScheduler: ReminderScheduler,
    private val backupRestoreController: BackupRestoreController,
    private val reminderTestScheduler: ReminderTestScheduler,
    @param:ApplicationContext private val appContext: Context,
    private val clock: Clock,
) : ViewModel() {
    private var persistedProfile: TaxpayerProfile? = null
    private var persistedStatusConfig: SmallBusinessStatusConfig? = null
    private val validator = SettingsValidator(
        strings = SettingsValidationStrings(
            registrationIdRequired = appContext.getString(R.string.settings_error_registration_id_required),
            displayNameRequired = appContext.getString(R.string.settings_error_display_name_required),
            taxRateInvalid = appContext.getString(R.string.settings_error_tax_rate_invalid),
            registrationDateInvalid = appContext.getString(R.string.onboarding_error_registration_date_invalid),
            certificateIssuedDateInvalid = appContext.getString(R.string.onboarding_error_certificate_issued_date_invalid),
            reminderTimeInvalid = appContext.getString(R.string.settings_error_reminder_time_invalid),
            declarationDaysInvalid = appContext.getString(R.string.settings_error_declaration_days_invalid),
            paymentDaysInvalid = appContext.getString(R.string.settings_error_payment_days_invalid),
        ),
    )
    private val documentImportHandler = SettingsDocumentImportHandler(
        strings = appContext.documentImportStrings(),
        loadPreview = loadOnboardingDocumentPreviewUseCase::invoke,
    )
    private val backupActions = SettingsBackupActions(
        textDocumentStore = textDocumentStore,
        exportIncomeEntriesCsvUseCase = exportIncomeEntriesCsvUseCase,
        exportMonthlySummariesCsvUseCase = exportMonthlySummariesCsvUseCase,
        exportBackupJsonUseCase = exportBackupJsonUseCase,
        context = appContext,
    )

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
        _uiState.value = SettingsFormReducer.updateRegistrationId(_uiState.value, value)
    }

    fun updateDisplayName(value: String) {
        _uiState.value = SettingsFormReducer.updateDisplayName(_uiState.value, value)
    }

    fun updateLegalForm(value: String) {
        _uiState.value = SettingsFormReducer.updateLegalForm(_uiState.value, value)
    }

    fun updateRegistrationDate(value: String) {
        _uiState.value = SettingsFormReducer.updateRegistrationDate(_uiState.value, value)
    }

    fun updateLegalAddress(value: String) {
        _uiState.value = SettingsFormReducer.updateLegalAddress(_uiState.value, value)
    }

    fun updateActivityType(value: String) {
        _uiState.value = SettingsFormReducer.updateActivityType(_uiState.value, value)
    }

    fun updateCertificateNumber(value: String) {
        _uiState.value = SettingsFormReducer.updateCertificateNumber(_uiState.value, value)
    }

    fun updateCertificateIssuedDate(value: String) {
        _uiState.value = SettingsFormReducer.updateCertificateIssuedDate(_uiState.value, value)
    }

    fun updateEffectiveDate(value: LocalDate) {
        _uiState.value = SettingsFormReducer.updateEffectiveDate(_uiState.value, value)
    }

    fun updateTaxRatePercent(value: String) {
        _uiState.value = SettingsFormReducer.updateTaxRatePercent(_uiState.value, value)
    }

    fun updateDefaultReminderTime(value: String) {
        _uiState.value = SettingsFormReducer.updateDefaultReminderTime(_uiState.value, value)
    }

    fun updateDeclarationReminderDays(value: String) {
        _uiState.value = SettingsFormReducer.updateDeclarationReminderDays(_uiState.value, value)
    }

    fun updatePaymentReminderDays(value: String) {
        _uiState.value = SettingsFormReducer.updatePaymentReminderDays(_uiState.value, value)
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
                    declarationReminderDays = settingsDefaultReminderDays,
                    paymentReminderDays = settingsDefaultReminderDays,
                    declarationRemindersEnabled = true,
                    paymentRemindersEnabled = true,
                    defaultReminderTime = LocalTime.of(9, 0),
                    themeMode = mode,
                ),
            )
        }
    }

    fun loadDocument(uri: Uri, action: DocumentImportAction) {
        viewModelScope.launch {
            _uiState.value = SettingsFormReducer.startDocumentLoading(_uiState.value)
            val result = documentImportHandler.load(uri, action)
            _uiState.value = SettingsFormReducer.applyDocumentLoadResult(_uiState.value, result)
        }
    }

    fun applyPreview() {
        val preview = _uiState.value.preview ?: return
        _uiState.value = SettingsFormReducer.applyPreview(
            state = _uiState.value,
            preview = preview,
            previewAppliedMessage = documentImportHandler.previewAppliedMessage,
        )
    }

    fun save() {
        val current = _uiState.value
        when (val validation = validator.validate(current)) {
            is SettingsValidationResult.Invalid -> {
                _uiState.value = current.copy(errorMessage = validation.errorMessage)
            }
            is SettingsValidationResult.Valid -> {
                _uiState.value = current.copy(isSaving = true, errorMessage = null)
                viewModelScope.launch {
                    val input = validation.value
                    val reminders = ReminderConfig(
                        declarationReminderDays = input.declarationReminderDays,
                        paymentReminderDays = input.paymentReminderDays,
                        declarationRemindersEnabled = current.declarationRemindersEnabled,
                        paymentRemindersEnabled = current.paymentRemindersEnabled,
                        defaultReminderTime = input.defaultReminderTime,
                        themeMode = current.themeMode,
                    )
                    upsertSettingsUseCase(
                        profile = (persistedProfile ?: TaxpayerProfile(
                            registrationId = input.registrationId,
                            displayName = input.displayName,
                        )).copy(
                            registrationId = input.registrationId,
                            displayName = input.displayName,
                            legalForm = input.legalForm,
                            registrationDate = input.registrationDate,
                            legalAddress = input.legalAddress,
                            activityType = input.activityType,
                        ),
                        config = (persistedStatusConfig ?: SmallBusinessStatusConfig(
                            effectiveDate = input.effectiveDate,
                            defaultTaxRatePercent = input.taxRatePercent,
                        )).copy(
                            effectiveDate = input.effectiveDate,
                            defaultTaxRatePercent = input.taxRatePercent,
                            certificateNumber = input.certificateNumber,
                            certificateIssuedDate = input.certificateIssuedDate,
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
            _effects.emit(SettingsEffect.Message(backupActions.exportIncomeEntriesCsv(uriString).message))
        }
    }

    fun exportMonthlySummariesCsv(uriString: String) {
        runDataOperation {
            _effects.emit(SettingsEffect.Message(backupActions.exportMonthlySummariesCsv(uriString).message))
        }
    }

    fun exportBackupJson(uriString: String) {
        runDataOperation {
            _effects.emit(SettingsEffect.Message(backupActions.exportBackupJson(uriString).message))
        }
    }

    fun importBackupJson(uriString: String) {
        runDataOperation {
            val result = backupRestoreController.restore(uriString)
            loadCurrentSettings()
            _effects.emit(SettingsEffect.Message(result.message))
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

    private suspend fun loadCurrentSettings() {
        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        val reminderConfig = settingsRepository.observeReminderConfig().first()
        persistedProfile = profile
        persistedStatusConfig = config
        _uiState.value = SettingsFormReducer.loadedState(
            currentState = _uiState.value,
            profile = profile,
            config = config,
            reminderConfig = reminderConfig,
            today = LocalDate.now(clock),
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

private fun ReminderType.label(context: Context): String = context.getString(
    when (this) {
        ReminderType.DECLARATION -> R.string.settings_test_notification_type_declaration
        ReminderType.PAYMENT -> R.string.settings_test_notification_type_payment
    },
)
