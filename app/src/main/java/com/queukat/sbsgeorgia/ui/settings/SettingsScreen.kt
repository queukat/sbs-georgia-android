@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.help.HelpFaqDialog
import com.queukat.sbsgeorgia.ui.help.QuickStartGuideDialog
import com.queukat.sbsgeorgia.ui.help.openFeedbackPage
import com.queukat.sbsgeorgia.ui.help.openPlayStoreListing
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    innerPadding: PaddingValues,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)
    val openStoreFailedMessage = stringResource(R.string.help_open_store_failed)
    val openFeedbackFailedMessage = stringResource(R.string.help_open_feedback_failed)
    var notificationPermissionGranted by remember {
        mutableStateOf(isNotificationPermissionGranted(context))
    }
    var pendingDocumentImportAction by remember { mutableStateOf<SettingsDocumentImportAction?>(null) }
    LifecycleResumeEffect(context) {
        notificationPermissionGranted = isNotificationPermissionGranted(context)
        onPauseOrDispose { }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
    }
    val exportIncomeCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        uri?.let { viewModel.exportIncomeEntriesCsv(it.toString()) }
    }
    val exportMonthlySummariesCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        uri?.let { viewModel.exportMonthlySummariesCsv(it.toString()) }
    }
    val exportBackupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackupJson(it.toString()) }
    }
    val importBackupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackupJson(it.toString()) }
    }
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val action = pendingDocumentImportAction
        if (uri != null && action != null) {
            viewModel.loadDocument(uri, action)
        }
        pendingDocumentImportAction = null
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Saved -> snackbarHostState.showSnackbar(savedMessage)
                is SettingsEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    SettingsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        notificationPermissionGranted = notificationPermissionGranted,
        onRegistrationIdChanged = viewModel::updateRegistrationId,
        onDisplayNameChanged = viewModel::updateDisplayName,
        onLegalFormChanged = viewModel::updateLegalForm,
        onRegistrationDateChanged = viewModel::updateRegistrationDate,
        onLegalAddressChanged = viewModel::updateLegalAddress,
        onActivityTypeChanged = viewModel::updateActivityType,
        onCertificateNumberChanged = viewModel::updateCertificateNumber,
        onCertificateIssuedDateChanged = viewModel::updateCertificateIssuedDate,
        onEffectiveDateChanged = viewModel::updateEffectiveDate,
        onTaxRateChanged = viewModel::updateTaxRatePercent,
        onDefaultReminderTimeChanged = viewModel::updateDefaultReminderTime,
        onDeclarationReminderDaysChanged = viewModel::updateDeclarationReminderDays,
        onPaymentReminderDaysChanged = viewModel::updatePaymentReminderDays,
        onDeclarationEnabledChanged = viewModel::updateDeclarationEnabled,
        onPaymentEnabledChanged = viewModel::updatePaymentEnabled,
        onThemeModeChanged = viewModel::updateThemeMode,
        onScheduleTestReminder = viewModel::scheduleTestReminder,
        onImportRegistryExtract = {
            pendingDocumentImportAction = SettingsDocumentImportAction.IMPORT_REGISTRY_EXTRACT
            importDocumentLauncher.launch(arrayOf("application/pdf"))
        },
        onImportCertificate = {
            pendingDocumentImportAction = SettingsDocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE
            importDocumentLauncher.launch(arrayOf("application/pdf"))
        },
        onApplyPreview = viewModel::applyPreview,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onExportIncomeEntriesCsv = {
            exportIncomeCsvLauncher.launch("sbs-georgia-income-entries-${LocalDate.now()}.csv")
        },
        onExportMonthlySummariesCsv = {
            exportMonthlySummariesCsvLauncher.launch("sbs-georgia-monthly-summaries-${LocalDate.now()}.csv")
        },
        onExportBackupJson = {
            exportBackupJsonLauncher.launch("sbs-georgia-backup-${LocalDate.now()}.json")
        },
        onImportBackupJson = {
            importBackupJsonLauncher.launch(arrayOf("application/json", "text/plain"))
        },
        onRateApp = {
            if (!openPlayStoreListing(context)) {
                scope.launch {
                    snackbarHostState.showSnackbar(openStoreFailedMessage)
                }
            }
        },
        onSendFeedback = {
            if (!openFeedbackPage(context)) {
                scope.launch {
                    snackbarHostState.showSnackbar(openFeedbackFailedMessage)
                }
            }
        },
        onSave = viewModel::save,
    )
}

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    notificationPermissionGranted: Boolean,
    onRegistrationIdChanged: (String) -> Unit = {},
    onDisplayNameChanged: (String) -> Unit = {},
    onLegalFormChanged: (String) -> Unit = {},
    onRegistrationDateChanged: (String) -> Unit = {},
    onLegalAddressChanged: (String) -> Unit = {},
    onActivityTypeChanged: (String) -> Unit = {},
    onCertificateNumberChanged: (String) -> Unit = {},
    onCertificateIssuedDateChanged: (String) -> Unit = {},
    onEffectiveDateChanged: (java.time.LocalDate) -> Unit = {},
    onTaxRateChanged: (String) -> Unit = {},
    onDefaultReminderTimeChanged: (String) -> Unit = {},
    onDeclarationReminderDaysChanged: (String) -> Unit = {},
    onPaymentReminderDaysChanged: (String) -> Unit = {},
    onDeclarationEnabledChanged: (Boolean) -> Unit = {},
    onPaymentEnabledChanged: (Boolean) -> Unit = {},
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onScheduleTestReminder: (ReminderType, Long) -> Unit = { _, _ -> },
    onImportRegistryExtract: () -> Unit = {},
    onImportCertificate: () -> Unit = {},
    onApplyPreview: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onExportIncomeEntriesCsv: () -> Unit = {},
    onExportMonthlySummariesCsv: () -> Unit = {},
    onExportBackupJson: () -> Unit = {},
    onImportBackupJson: () -> Unit = {},
    onRateApp: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onSave: () -> Unit,
) {
    var showHelpFaq by rememberSaveable { mutableStateOf(false) }
    var showQuickStartGuide by rememberSaveable { mutableStateOf(false) }
    var testReminderTypeName by rememberSaveable { mutableStateOf(ReminderType.DECLARATION.name) }
    var testReminderDelaySeconds by rememberSaveable { mutableStateOf(5L) }
    val testReminderType = ReminderType.valueOf(testReminderTypeName)
    val reminderDelayOptions = listOf(5L, 15L, 30L)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(title = stringResource(R.string.settings_title))
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { contentPadding ->
        if (showHelpFaq) {
            HelpFaqDialog(
                onDismiss = { showHelpFaq = false },
                onViewQuickStartGuide = {
                    showHelpFaq = false
                    showQuickStartGuide = true
                },
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback,
            )
        }
        if (showQuickStartGuide) {
            QuickStartGuideDialog(
                onDismiss = { showQuickStartGuide = false },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppSection(title = stringResource(R.string.settings_section_document_imports)) {
                Text(
                    stringResource(R.string.settings_document_imports_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onImportRegistryExtract,
                        enabled = !uiState.isDocumentLoading && !uiState.isSaving && !uiState.isDataOperationInProgress,
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isDocumentLoading) {
                                    R.string.import_statement_parsing
                                } else {
                                    R.string.onboarding_import_registration_pdf
                                },
                            ),
                        )
                    }
                    Button(
                        onClick = onImportCertificate,
                        enabled = !uiState.isDocumentLoading && !uiState.isSaving && !uiState.isDataOperationInProgress,
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isDocumentLoading) {
                                    R.string.import_statement_parsing
                                } else {
                                    R.string.onboarding_import_sbs_certificate_pdf
                                },
                            ),
                        )
                    }
                }
                uiState.documentInfoMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                uiState.documentErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.preview?.let { preview ->
                AppSection(title = stringResource(R.string.onboarding_section_preview)) {
                    PreviewHeader(preview = preview)
                    PreviewField(stringResource(R.string.onboarding_display_name), preview.displayName.value, preview.displayName.confidence)
                    PreviewField(stringResource(R.string.onboarding_legal_form), preview.legalForm.value, preview.legalForm.confidence)
                    PreviewField(stringResource(R.string.onboarding_registration_id), preview.registrationId.value, preview.registrationId.confidence)
                    PreviewField(stringResource(R.string.onboarding_registration_date), preview.registrationDate.value?.toString(), preview.registrationDate.confidence)
                    PreviewField(stringResource(R.string.onboarding_legal_address), preview.legalAddress.value, preview.legalAddress.confidence)
                    if (preview.documentType == OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE) {
                        PreviewField(stringResource(R.string.onboarding_activity_type), preview.activityType.value, preview.activityType.confidence)
                        PreviewField(stringResource(R.string.onboarding_certificate_number), preview.certificateNumber.value, preview.certificateNumber.confidence)
                        PreviewField(
                            stringResource(R.string.onboarding_certificate_issued_date),
                            preview.certificateIssuedDate.value?.toString(),
                            preview.certificateIssuedDate.confidence,
                        )
                        PreviewField(
                            stringResource(R.string.onboarding_effective_date),
                            preview.effectiveDate.value?.toString(),
                            preview.effectiveDate.confidence,
                        )
                    }
                    preview.notes.forEach { note ->
                        Text(previewNoteLabel(note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onApplyPreview,
                        enabled = !uiState.isSaving && !uiState.isDataOperationInProgress,
                    ) {
                        Text(stringResource(R.string.onboarding_apply_preview))
                    }
                }
            }

            AppSection(title = stringResource(R.string.settings_section_taxpayer_profile)) {
                OutlinedTextField(
                    value = uiState.registrationId,
                    onValueChange = onRegistrationIdChanged,
                    label = { Text(stringResource(R.string.settings_registration_id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-registration-id-field"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = onDisplayNameChanged,
                    label = { Text(stringResource(R.string.settings_display_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-display-name-field"),
                )
                OutlinedTextField(
                    value = uiState.legalForm,
                    onValueChange = onLegalFormChanged,
                    label = { Text(stringResource(R.string.onboarding_legal_form)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.registrationDate,
                    onValueChange = onRegistrationDateChanged,
                    label = { Text(stringResource(R.string.onboarding_registration_date)) },
                    supportingText = { Text(stringResource(R.string.onboarding_optional_iso_date_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.legalAddress,
                    onValueChange = onLegalAddressChanged,
                    label = { Text(stringResource(R.string.onboarding_legal_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = uiState.activityType,
                    onValueChange = onActivityTypeChanged,
                    label = { Text(stringResource(R.string.onboarding_activity_type)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AppSection(title = stringResource(R.string.settings_section_small_business_status)) {
                OutlinedTextField(
                    value = uiState.certificateNumber,
                    onValueChange = onCertificateNumberChanged,
                    label = { Text(stringResource(R.string.onboarding_certificate_number)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.certificateIssuedDate,
                    onValueChange = onCertificateIssuedDateChanged,
                    label = { Text(stringResource(R.string.onboarding_certificate_issued_date)) },
                    supportingText = { Text(stringResource(R.string.onboarding_optional_iso_date_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DatePickerField(
                    label = stringResource(R.string.settings_effective_date),
                    value = uiState.effectiveDate,
                    onValueChange = onEffectiveDateChanged,
                    testTag = "settings-effective-date-field",
                )
                DecimalField(
                    label = stringResource(R.string.settings_tax_rate),
                    value = uiState.taxRatePercent,
                    onValueChange = onTaxRateChanged,
                    testTag = "settings-tax-rate-field",
                )
            }

            AppSection(title = stringResource(R.string.settings_section_reminder_preferences)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                    Text(
                        stringResource(R.string.settings_notifications_blocked),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("settings-notification-permission-button"),
                    ) {
                        Text(stringResource(R.string.settings_grant_notification_permission))
                    }
                }
                OutlinedTextField(
                    value = uiState.defaultReminderTime,
                    onValueChange = onDefaultReminderTimeChanged,
                    label = { Text(stringResource(R.string.settings_default_reminder_time)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-default-reminder-time-field"),
                    supportingText = { Text(stringResource(R.string.settings_default_reminder_time_hint)) },
                )
                OutlinedTextField(
                    value = uiState.declarationReminderDays,
                    onValueChange = onDeclarationReminderDaysChanged,
                    label = { Text(stringResource(R.string.settings_declaration_reminder_days)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.settings_reminder_days_hint)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_enable_declaration_reminders))
                    Switch(
                        checked = uiState.declarationRemindersEnabled,
                        onCheckedChange = onDeclarationEnabledChanged,
                    )
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = uiState.paymentReminderDays,
                    onValueChange = onPaymentReminderDaysChanged,
                    label = { Text(stringResource(R.string.settings_payment_reminder_days)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.settings_reminder_days_hint)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_enable_payment_reminders))
                    Switch(
                        checked = uiState.paymentRemindersEnabled,
                        onCheckedChange = onPaymentEnabledChanged,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_test_notifications_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_test_notification_type),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReminderType.entries.forEach { type ->
                        FilterChip(
                            selected = testReminderTypeName == type.name,
                            onClick = { testReminderTypeName = type.name },
                            label = {
                                Text(
                                    stringResource(
                                        when (type) {
                                            ReminderType.DECLARATION ->
                                                R.string.settings_test_notification_type_declaration
                                            ReminderType.PAYMENT ->
                                                R.string.settings_test_notification_type_payment
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_test_notification_delay),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    reminderDelayOptions.forEach { delaySeconds ->
                        FilterChip(
                            selected = testReminderDelaySeconds == delaySeconds,
                            onClick = { testReminderDelaySeconds = delaySeconds },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.settings_test_notification_delay_seconds,
                                        delaySeconds,
                                    ),
                                )
                            },
                        )
                    }
                }
                Button(
                    onClick = { onScheduleTestReminder(testReminderType, testReminderDelaySeconds) },
                    enabled = !uiState.isSaving && !uiState.isDataOperationInProgress,
                    modifier = Modifier.testTag("settings-test-notification-button"),
                ) {
                    Text(stringResource(R.string.settings_send_test_notification))
                }
            }

            AppSection(title = stringResource(R.string.settings_section_appearance)) {
                FlowRow {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            AppSection(title = stringResource(R.string.settings_section_data_management)) {
                Text(
                    stringResource(R.string.settings_data_management_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_data_management_warning),
                    color = MaterialTheme.colorScheme.error,
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_export_income_csv),
                    body = stringResource(R.string.settings_export_income_csv_body),
                    onClick = onExportIncomeEntriesCsv,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    testTag = "settings-export-income-csv-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_export_monthly_csv),
                    body = stringResource(R.string.settings_export_monthly_csv_body),
                    onClick = onExportMonthlySummariesCsv,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    testTag = "settings-export-monthly-csv-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_export_backup_json),
                    body = stringResource(R.string.settings_export_backup_json_body),
                    onClick = onExportBackupJson,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    testTag = "settings-export-backup-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_import_backup_json),
                    body = stringResource(R.string.settings_import_backup_json_body),
                    onClick = onImportBackupJson,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    testTag = "settings-import-backup-button",
                )
                if (uiState.isDataOperationInProgress) {
                    Text(stringResource(R.string.settings_data_operation_in_progress))
                }
            }

            AppSection(title = stringResource(R.string.settings_section_help_feedback)) {
                Text(
                    stringResource(R.string.settings_help_feedback_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_open_help_faq),
                    body = stringResource(R.string.settings_open_help_faq_body),
                    onClick = { showHelpFaq = true },
                    enabled = true,
                    testTag = "settings-open-help-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_view_quick_start),
                    body = stringResource(R.string.settings_view_quick_start_body),
                    onClick = { showQuickStartGuide = true },
                    enabled = true,
                    testTag = "settings-view-quick-start-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_rate_app),
                    body = stringResource(R.string.settings_rate_app_body),
                    onClick = onRateApp,
                    enabled = true,
                    testTag = "settings-rate-app-button",
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_send_feedback),
                    body = stringResource(R.string.settings_send_feedback_body),
                    onClick = onSendFeedback,
                    enabled = true,
                    testTag = "settings-send-feedback-button",
                )
            }

            AppSection(title = stringResource(R.string.settings_section_save)) {
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving && !uiState.isDataOperationInProgress,
                    modifier = Modifier.testTag("settings-save-button"),
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    body: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        ) {
            Text(label)
        }
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PreviewHeader(preview: OnboardingImportPreview) {
    KeyValueRow(stringResource(R.string.common_selected_file), preview.sourceFileName)
    KeyValueRow(
        stringResource(R.string.onboarding_detected_document),
        when (preview.documentType) {
            OnboardingDocumentType.REGISTRY_EXTRACT -> stringResource(R.string.onboarding_document_registry_extract)
            OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE -> stringResource(R.string.onboarding_document_sbs_certificate)
        },
    )
}

@Composable
private fun previewNoteLabel(note: OnboardingPreviewNote): String = stringResource(
    when (note) {
        OnboardingPreviewNote.REGISTRY_EFFECTIVE_DATE_MANUAL ->
            R.string.onboarding_note_registry_effective_date_manual
        OnboardingPreviewNote.CERTIFICATE_EFFECTIVE_DATE_AUTOFILLED ->
            R.string.onboarding_note_certificate_effective_date_autofilled
        OnboardingPreviewNote.REVIEW_BEFORE_APPLY ->
            R.string.onboarding_note_review_before_apply
    },
)

@Composable
private fun PreviewField(
    label: String,
    value: String?,
    confidence: ExtractionConfidence,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value ?: stringResource(R.string.common_not_detected),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (!value.isNullOrBlank()) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    stringResource(
                        if (confidence == ExtractionConfidence.CONFIDENT) {
                            R.string.common_confident
                        } else {
                            R.string.common_needs_review
                        },
                    ),
                )
            },
        )
    }
}
