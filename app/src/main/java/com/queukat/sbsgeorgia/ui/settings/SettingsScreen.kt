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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import java.time.LocalDate

@Composable
fun SettingsRoute(
    innerPadding: PaddingValues,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.settings_saved)
    var notificationPermissionGranted by remember {
        mutableStateOf(isNotificationPermissionGranted(context))
    }
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
        onEffectiveDateChanged = viewModel::updateEffectiveDate,
        onTaxRateChanged = viewModel::updateTaxRatePercent,
        onDefaultReminderTimeChanged = viewModel::updateDefaultReminderTime,
        onDeclarationReminderDaysChanged = viewModel::updateDeclarationReminderDays,
        onPaymentReminderDaysChanged = viewModel::updatePaymentReminderDays,
        onDeclarationEnabledChanged = viewModel::updateDeclarationEnabled,
        onPaymentEnabledChanged = viewModel::updatePaymentEnabled,
        onThemeModeChanged = viewModel::updateThemeMode,
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
        onSave = viewModel::save,
    )
}

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    notificationPermissionGranted: Boolean,
    onRegistrationIdChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onEffectiveDateChanged: (java.time.LocalDate) -> Unit,
    onTaxRateChanged: (String) -> Unit,
    onDefaultReminderTimeChanged: (String) -> Unit,
    onDeclarationReminderDaysChanged: (String) -> Unit,
    onPaymentReminderDaysChanged: (String) -> Unit,
    onDeclarationEnabledChanged: (Boolean) -> Unit,
    onPaymentEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onExportIncomeEntriesCsv: () -> Unit = {},
    onExportMonthlySummariesCsv: () -> Unit = {},
    onExportBackupJson: () -> Unit = {},
    onImportBackupJson: () -> Unit = {},
    onSave: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(title = stringResource(R.string.settings_title))
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { contentPadding ->
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
                Button(
                    onClick = onExportIncomeEntriesCsv,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-export-income-csv-button"),
                ) {
                    Text(stringResource(R.string.settings_export_income_csv))
                }
                Button(
                    onClick = onExportMonthlySummariesCsv,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-export-monthly-csv-button"),
                ) {
                    Text(stringResource(R.string.settings_export_monthly_csv))
                }
                Button(
                    onClick = onExportBackupJson,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-export-backup-button"),
                ) {
                    Text(stringResource(R.string.settings_export_backup_json))
                }
                Button(
                    onClick = onImportBackupJson,
                    enabled = !uiState.isDataOperationInProgress && !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-import-backup-button"),
                ) {
                    Text(stringResource(R.string.settings_import_backup_json))
                }
                if (uiState.isDataOperationInProgress) {
                    Text(stringResource(R.string.settings_data_operation_in_progress))
                }
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

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
