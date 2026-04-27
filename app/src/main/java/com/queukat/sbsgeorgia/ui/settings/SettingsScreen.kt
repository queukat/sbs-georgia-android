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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportAction
import com.queukat.sbsgeorgia.ui.help.HelpFaqDialog
import com.queukat.sbsgeorgia.ui.help.QuickStartGuideDialog
import com.queukat.sbsgeorgia.ui.help.openFeedbackPage
import com.queukat.sbsgeorgia.ui.help.openPlayStoreListing
import com.queukat.sbsgeorgia.ui.settings.components.AppearanceSettingsSection
import com.queukat.sbsgeorgia.ui.settings.components.DataManagementSection
import com.queukat.sbsgeorgia.ui.settings.components.DocumentImportSection
import com.queukat.sbsgeorgia.ui.settings.components.DocumentPreviewSection
import com.queukat.sbsgeorgia.ui.settings.components.HelpFeedbackSection
import com.queukat.sbsgeorgia.ui.settings.components.ReminderSettingsSection
import com.queukat.sbsgeorgia.ui.settings.components.SmallBusinessStatusSection
import com.queukat.sbsgeorgia.ui.settings.components.TaxpayerSettingsSection
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
    var pendingDocumentImportAction by remember { mutableStateOf<DocumentImportAction?>(null) }
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
    var pendingBackupImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    val importBackupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        pendingBackupImportUri = uri?.toString()
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
            pendingDocumentImportAction = DocumentImportAction.IMPORT_REGISTRY_EXTRACT
            importDocumentLauncher.launch(arrayOf("application/pdf"))
        },
        onImportCertificate = {
            pendingDocumentImportAction = DocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE
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
            importBackupJsonLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream"),
            )
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

    pendingBackupImportUri?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingBackupImportUri = null },
            title = { Text(stringResource(R.string.settings_restore_backup_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_backup_confirm_body)) },
            dismissButton = {
                TextButton(onClick = { pendingBackupImportUri = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingBackupImportUri = null
                        viewModel.importBackupJson(uriString)
                    },
                    enabled = !uiState.isDataOperationInProgress,
                ) {
                    Text(stringResource(R.string.settings_restore_backup_confirm_action))
                }
            },
        )
    }
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
    onEffectiveDateChanged: (LocalDate) -> Unit = {},
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
    val isPrimaryActionEnabled = !uiState.isSaving && !uiState.isDataOperationInProgress && !uiState.isDocumentLoading
    val showPrimaryProgress = uiState.isSaving || uiState.isDataOperationInProgress || uiState.isDocumentLoading

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(title = stringResource(R.string.settings_title))
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showPrimaryProgress) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onSave,
                        enabled = isPrimaryActionEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isSaving) {
                                    R.string.settings_saving
                                } else {
                                    R.string.settings_save
                                },
                            ),
                        )
                    }
                }
            }
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
            DocumentImportSection(
                uiState = uiState,
                onImportRegistryExtract = onImportRegistryExtract,
                onImportCertificate = onImportCertificate,
            )

            uiState.preview?.let { preview ->
                DocumentPreviewSection(
                    preview = preview,
                    isActionEnabled = !uiState.isSaving && !uiState.isDataOperationInProgress,
                    onApplyPreview = onApplyPreview,
                )
            }

            TaxpayerSettingsSection(
                uiState = uiState,
                onRegistrationIdChanged = onRegistrationIdChanged,
                onDisplayNameChanged = onDisplayNameChanged,
                onLegalFormChanged = onLegalFormChanged,
                onRegistrationDateChanged = onRegistrationDateChanged,
                onLegalAddressChanged = onLegalAddressChanged,
                onActivityTypeChanged = onActivityTypeChanged,
            )

            SmallBusinessStatusSection(
                uiState = uiState,
                onCertificateNumberChanged = onCertificateNumberChanged,
                onCertificateIssuedDateChanged = onCertificateIssuedDateChanged,
                onEffectiveDateChanged = onEffectiveDateChanged,
                onTaxRateChanged = onTaxRateChanged,
            )

            ReminderSettingsSection(
                uiState = uiState,
                notificationPermissionGranted = notificationPermissionGranted,
                testReminderType = testReminderType,
                testReminderDelaySeconds = testReminderDelaySeconds,
                onDefaultReminderTimeChanged = onDefaultReminderTimeChanged,
                onDeclarationReminderDaysChanged = onDeclarationReminderDaysChanged,
                onPaymentReminderDaysChanged = onPaymentReminderDaysChanged,
                onDeclarationEnabledChanged = onDeclarationEnabledChanged,
                onPaymentEnabledChanged = onPaymentEnabledChanged,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onTestReminderTypeChanged = { testReminderTypeName = it.name },
                onTestReminderDelayChanged = { testReminderDelaySeconds = it },
                onScheduleTestReminder = onScheduleTestReminder,
            )

            AppearanceSettingsSection(
                selectedThemeMode = uiState.themeMode,
                onThemeModeChanged = onThemeModeChanged,
            )

            DataManagementSection(
                uiState = uiState,
                onExportIncomeEntriesCsv = onExportIncomeEntriesCsv,
                onExportMonthlySummariesCsv = onExportMonthlySummariesCsv,
                onExportBackupJson = onExportBackupJson,
                onImportBackupJson = onImportBackupJson,
            )

            HelpFeedbackSection(
                onOpenHelpFaq = { showHelpFaq = true },
                onViewQuickStart = { showQuickStartGuide = true },
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback,
            )
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
