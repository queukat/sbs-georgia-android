@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsChoiceRow
import com.queukat.sbsgeorgia.ui.common.SbsPrimaryButton
import com.queukat.sbsgeorgia.ui.common.SbsScreenScaffold
import com.queukat.sbsgeorgia.ui.common.SbsSecondaryButton
import com.queukat.sbsgeorgia.ui.common.SbsStickyActionContainer
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportAction

@Composable
fun OnboardingRoute(innerPadding: PaddingValues = PaddingValues()) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingImportAction by remember { mutableStateOf<DocumentImportAction?>(null) }
    var pendingRestoreBackupUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            val action = pendingImportAction
            if (uri != null && action != null) {
                viewModel.loadDocument(uri, action)
            }
            pendingImportAction = null
        }
    val restoreBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                if (uiState.hasExistingSetupData) {
                    pendingRestoreBackupUri = uri.toString()
                } else {
                    viewModel.restoreBackup(uri)
                }
            }
        }

    OnboardingScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onImportRegistryExtract = {
            pendingImportAction = DocumentImportAction.IMPORT_REGISTRY_EXTRACT
            pickerLauncher.launch(arrayOf("application/pdf"))
        },
        onImportCertificate = {
            pendingImportAction = DocumentImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE
            pickerLauncher.launch(arrayOf("application/pdf"))
        },
        onRestoreBackup = {
            restoreBackupLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream")
            )
        },
        onApplyPreview = viewModel::applyPreview,
        onDisplayNameChanged = viewModel::updateDisplayName,
        onLegalFormChanged = viewModel::updateLegalForm,
        onRegistrationIdChanged = viewModel::updateRegistrationId,
        onRegistrationDateChanged = viewModel::updateRegistrationDate,
        onLegalAddressChanged = viewModel::updateLegalAddress,
        onActivityTypeChanged = viewModel::updateActivityType,
        onCertificateNumberChanged = viewModel::updateCertificateNumber,
        onCertificateIssuedDateChanged = viewModel::updateCertificateIssuedDate,
        onEffectiveDateChanged = viewModel::updateEffectiveDate,
        onTaxRatePercentChanged = viewModel::updateTaxRatePercent,
        onComplete = viewModel::completeOnboarding
    )

    pendingRestoreBackupUri?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingRestoreBackupUri = null },
            title = { Text(stringResource(R.string.onboarding_restore_backup_confirm_title)) },
            text = { Text(stringResource(R.string.onboarding_restore_backup_confirm_body)) },
            dismissButton = {
                TextButton(onClick = { pendingRestoreBackupUri = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreBackupUri = null
                        viewModel.restoreBackup(uriString.toUri())
                    },
                    enabled = !uiState.isRestoringBackup && !uiState.isLoading && !uiState.isSaving
                ) {
                    Text(stringResource(R.string.onboarding_restore_backup_confirm_action))
                }
            }
        )
    }
}

private enum class OnboardingWizardStep {
    SETUP_METHOD,
    REVIEW_FOUND_VALUES,
    REQUIRED_FIELDS
}

private enum class OnboardingSetupMethod {
    IMPORT_DOCUMENT,
    RESTORE_BACKUP,
    MANUAL_SETUP
}

private enum class OnboardingImportDocument {
    REGISTRY_EXTRACT,
    SBS_CERTIFICATE
}

@Composable
fun OnboardingScreen(
    innerPadding: PaddingValues,
    uiState: OnboardingUiState,
    onImportRegistryExtract: () -> Unit,
    onImportCertificate: () -> Unit,
    onRestoreBackup: () -> Unit,
    onApplyPreview: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onLegalFormChanged: (String) -> Unit,
    onRegistrationIdChanged: (String) -> Unit,
    onRegistrationDateChanged: (String) -> Unit,
    onLegalAddressChanged: (String) -> Unit,
    onActivityTypeChanged: (String) -> Unit,
    onCertificateNumberChanged: (String) -> Unit,
    onCertificateIssuedDateChanged: (String) -> Unit,
    onEffectiveDateChanged: (java.time.LocalDate) -> Unit,
    onTaxRatePercentChanged: (String) -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by rememberSaveable(uiState.preview != null) {
        mutableStateOf(
            if (uiState.preview != null) {
                OnboardingWizardStep.REVIEW_FOUND_VALUES
            } else {
                OnboardingWizardStep.SETUP_METHOD
            }
        )
    }
    var selectedSetupMethod by rememberSaveable {
        mutableStateOf<OnboardingSetupMethod?>(
            if (uiState.preview != null) OnboardingSetupMethod.IMPORT_DOCUMENT else null
        )
    }
    var selectedImportDocument by rememberSaveable {
        mutableStateOf(OnboardingImportDocument.REGISTRY_EXTRACT)
    }
    var restoreRequested by rememberSaveable { mutableStateOf(false) }
    var restoreInfoMessageBeforeRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val isBusy = uiState.isLoading || uiState.isSaving || uiState.isRestoringBackup

    LaunchedEffect(uiState.preview) {
        if (uiState.preview != null) {
            selectedSetupMethod = OnboardingSetupMethod.IMPORT_DOCUMENT
            currentStep = OnboardingWizardStep.REVIEW_FOUND_VALUES
        }
    }

    LaunchedEffect(restoreRequested, isBusy, uiState.infoMessage, uiState.errorMessage) {
        if (!restoreRequested || isBusy) return@LaunchedEffect
        when {
            uiState.errorMessage != null -> restoreRequested = false
            uiState.infoMessage != null &&
                uiState.infoMessage != restoreInfoMessageBeforeRequest -> {
                selectedSetupMethod = OnboardingSetupMethod.RESTORE_BACKUP
                currentStep = OnboardingWizardStep.REVIEW_FOUND_VALUES
                restoreRequested = false
            }
        }
    }

    val primaryActionEnabled =
        !isBusy &&
            when (currentStep) {
                OnboardingWizardStep.SETUP_METHOD -> selectedSetupMethod != null
                OnboardingWizardStep.REVIEW_FOUND_VALUES -> true
                OnboardingWizardStep.REQUIRED_FIELDS -> true
            }

    fun goBack() {
        currentStep =
            when (currentStep) {
                OnboardingWizardStep.SETUP_METHOD -> OnboardingWizardStep.SETUP_METHOD
                OnboardingWizardStep.REVIEW_FOUND_VALUES -> OnboardingWizardStep.SETUP_METHOD
                OnboardingWizardStep.REQUIRED_FIELDS ->
                    if (selectedSetupMethod == OnboardingSetupMethod.MANUAL_SETUP) {
                        OnboardingWizardStep.SETUP_METHOD
                    } else {
                        OnboardingWizardStep.REVIEW_FOUND_VALUES
                    }
            }
    }

    fun continueForward() {
        when (currentStep) {
            OnboardingWizardStep.SETUP_METHOD -> {
                when (selectedSetupMethod) {
                    OnboardingSetupMethod.IMPORT_DOCUMENT -> {
                        when (selectedImportDocument) {
                            OnboardingImportDocument.REGISTRY_EXTRACT -> onImportRegistryExtract()
                            OnboardingImportDocument.SBS_CERTIFICATE -> onImportCertificate()
                        }
                    }
                    OnboardingSetupMethod.RESTORE_BACKUP -> {
                        restoreRequested = true
                        restoreInfoMessageBeforeRequest = uiState.infoMessage
                        onRestoreBackup()
                    }
                    OnboardingSetupMethod.MANUAL_SETUP -> {
                        currentStep = OnboardingWizardStep.REQUIRED_FIELDS
                    }
                    null -> Unit
                }
            }
            OnboardingWizardStep.REVIEW_FOUND_VALUES -> {
                if (uiState.preview != null) {
                    onApplyPreview()
                }
                currentStep = OnboardingWizardStep.REQUIRED_FIELDS
            }
            OnboardingWizardStep.REQUIRED_FIELDS -> onComplete()
        }
    }

    SbsScreenScaffold(
        innerPadding = innerPadding,
        contentPadding =
        PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 24.dp
        ),
        bottomAction = {
            OnboardingBottomActions(
                currentStep = currentStep,
                isBusy = isBusy,
                busyLabelRes =
                when {
                    uiState.isRestoringBackup -> R.string.onboarding_restoring_backup
                    uiState.isLoading -> R.string.import_statement_parsing
                    else -> R.string.workflow_saving
                },
                errorMessage = uiState.errorMessage,
                primaryActionEnabled = primaryActionEnabled,
                onBack = ::goBack,
                onPrimaryAction = ::continueForward
            )
        }
    ) { contentPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (currentStep) {
                OnboardingWizardStep.SETUP_METHOD ->
                    SetupMethodStep(
                        uiState = uiState,
                        selectedSetupMethod = selectedSetupMethod,
                        selectedImportDocument = selectedImportDocument,
                        onSetupMethodSelected = { selectedSetupMethod = it },
                        onImportDocumentSelected = { selectedImportDocument = it }
                    )
                OnboardingWizardStep.REVIEW_FOUND_VALUES ->
                    ReviewFoundValuesStep(
                        uiState = uiState,
                        setupMethod = selectedSetupMethod
                    )
                OnboardingWizardStep.REQUIRED_FIELDS ->
                    RequiredFieldsStep(
                        uiState = uiState,
                        onDisplayNameChanged = onDisplayNameChanged,
                        onLegalFormChanged = onLegalFormChanged,
                        onRegistrationIdChanged = onRegistrationIdChanged,
                        onRegistrationDateChanged = onRegistrationDateChanged,
                        onLegalAddressChanged = onLegalAddressChanged,
                        onActivityTypeChanged = onActivityTypeChanged,
                        onCertificateNumberChanged = onCertificateNumberChanged,
                        onCertificateIssuedDateChanged = onCertificateIssuedDateChanged,
                        onEffectiveDateChanged = onEffectiveDateChanged,
                        onTaxRatePercentChanged = onTaxRatePercentChanged
                    )
            }
        }
    }
}

@Composable
private fun OnboardingBottomActions(
    currentStep: OnboardingWizardStep,
    isBusy: Boolean,
    busyLabelRes: Int,
    errorMessage: String?,
    primaryActionEnabled: Boolean,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    SbsStickyActionContainer(
        isLoading = isBusy,
        statusMessage = errorMessage,
        statusModifier = Modifier.testTag("onboarding-error-summary")
    ) {
        if (currentStep != OnboardingWizardStep.SETUP_METHOD) {
            SbsSecondaryButton(
                label = stringResource(R.string.common_back),
                onClick = onBack,
                enabled = !isBusy,
                modifier = Modifier.testTag("onboarding-back-button")
            )
        }
        SbsPrimaryButton(
            label =
            stringResource(
                when {
                    isBusy -> busyLabelRes
                    currentStep == OnboardingWizardStep.REQUIRED_FIELDS ->
                        R.string.onboarding_finish_setup
                    else -> R.string.onboarding_continue
                }
            ),
            onClick = onPrimaryAction,
            enabled = primaryActionEnabled,
            modifier =
            Modifier
                .then(
                    if (currentStep == OnboardingWizardStep.SETUP_METHOD) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    }
                )
                .testTag("onboarding-primary-action-button")
        )
    }
}

@Composable
private fun SetupMethodStep(
    uiState: OnboardingUiState,
    selectedSetupMethod: OnboardingSetupMethod?,
    selectedImportDocument: OnboardingImportDocument,
    onSetupMethodSelected: (OnboardingSetupMethod) -> Unit,
    onImportDocumentSelected: (OnboardingImportDocument) -> Unit
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("onboarding-step-method"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_wizard_step_setup_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.onboarding_wizard_step_setup_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SbsChoiceRow(
            selected = selectedSetupMethod == OnboardingSetupMethod.IMPORT_DOCUMENT,
            title = stringResource(R.string.onboarding_method_import_title),
            body = stringResource(R.string.onboarding_method_import_body),
            modifier = Modifier.testTag("onboarding-method-import-button"),
            onClick = { onSetupMethodSelected(OnboardingSetupMethod.IMPORT_DOCUMENT) }
        )
        if (selectedSetupMethod == OnboardingSetupMethod.IMPORT_DOCUMENT) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("onboarding-import-document-options")
            ) {
                Text(
                    text = stringResource(R.string.onboarding_import_document_type),
                    style = MaterialTheme.typography.labelLarge
                )
                SbsChoiceRow(
                    selected = selectedImportDocument == OnboardingImportDocument.REGISTRY_EXTRACT,
                    title = stringResource(R.string.onboarding_import_registration_pdf),
                    body = stringResource(R.string.onboarding_import_registration_pdf_body),
                    modifier = Modifier.testTag("onboarding-import-registry-button"),
                    onClick = {
                        onImportDocumentSelected(OnboardingImportDocument.REGISTRY_EXTRACT)
                    }
                )
                SbsChoiceRow(
                    selected = selectedImportDocument == OnboardingImportDocument.SBS_CERTIFICATE,
                    title = stringResource(R.string.onboarding_import_sbs_certificate_pdf),
                    body = stringResource(R.string.onboarding_import_sbs_certificate_pdf_body),
                    modifier = Modifier.testTag("onboarding-import-certificate-button"),
                    onClick = {
                        onImportDocumentSelected(OnboardingImportDocument.SBS_CERTIFICATE)
                    }
                )
            }
        }
        SbsChoiceRow(
            selected = selectedSetupMethod == OnboardingSetupMethod.RESTORE_BACKUP,
            title = stringResource(R.string.onboarding_method_restore_title),
            body = stringResource(R.string.onboarding_method_restore_body),
            modifier = Modifier.testTag("onboarding-method-restore-button"),
            onClick = { onSetupMethodSelected(OnboardingSetupMethod.RESTORE_BACKUP) }
        )
        SbsChoiceRow(
            selected = selectedSetupMethod == OnboardingSetupMethod.MANUAL_SETUP,
            title = stringResource(R.string.onboarding_method_manual_title),
            body = stringResource(R.string.onboarding_method_manual_body),
            modifier = Modifier.testTag("onboarding-method-manual-button"),
            onClick = { onSetupMethodSelected(OnboardingSetupMethod.MANUAL_SETUP) }
        )
        uiState.infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReviewFoundValuesStep(uiState: OnboardingUiState, setupMethod: OnboardingSetupMethod?) {
    val preview = uiState.preview
    AppSection(
        title =
        stringResource(
            if (preview != null) {
                R.string.onboarding_wizard_step_review_title
            } else {
                R.string.onboarding_restore_review_title
            }
        ),
        modifier = Modifier.testTag("onboarding-step-review")
    ) {
        Text(
            text =
            stringResource(
                if (preview != null) {
                    R.string.onboarding_wizard_step_review_body
                } else {
                    R.string.onboarding_restore_review_body
                }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (preview != null) {
            PreviewHeader(preview = preview)
            PreviewField(
                stringResource(R.string.onboarding_display_name),
                preview.displayName.value,
                preview.displayName.confidence
            )
            PreviewField(
                stringResource(R.string.onboarding_legal_form),
                preview.legalForm.value,
                preview.legalForm.confidence
            )
            PreviewField(
                stringResource(R.string.onboarding_registration_id),
                preview.registrationId.value,
                preview.registrationId.confidence
            )
            PreviewField(
                stringResource(R.string.onboarding_registration_date),
                preview.registrationDate.value?.toString(),
                preview.registrationDate.confidence
            )
            PreviewField(
                stringResource(R.string.onboarding_legal_address),
                preview.legalAddress.value,
                preview.legalAddress.confidence
            )
            if (preview.documentType == OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE) {
                PreviewField(
                    stringResource(R.string.onboarding_activity_type),
                    preview.activityType.value,
                    preview.activityType.confidence
                )
                PreviewField(
                    stringResource(R.string.onboarding_certificate_number),
                    preview.certificateNumber.value,
                    preview.certificateNumber.confidence
                )
                PreviewField(
                    stringResource(R.string.onboarding_certificate_issued_date),
                    preview.certificateIssuedDate.value?.toString(),
                    preview.certificateIssuedDate.confidence
                )
                PreviewField(
                    stringResource(R.string.onboarding_effective_date),
                    preview.effectiveDate.value?.toString(),
                    preview.effectiveDate.confidence
                )
            }
            preview.notes.forEach { note ->
                Text(
                    previewNoteLabel(note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            RestoredFieldSummary(uiState = uiState)
        }
        if (setupMethod == OnboardingSetupMethod.IMPORT_DOCUMENT && preview != null) {
            Text(
                text = stringResource(R.string.onboarding_apply_preview_via_continue),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        uiState.infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RequiredFieldsStep(
    uiState: OnboardingUiState,
    onDisplayNameChanged: (String) -> Unit,
    onLegalFormChanged: (String) -> Unit,
    onRegistrationIdChanged: (String) -> Unit,
    onRegistrationDateChanged: (String) -> Unit,
    onLegalAddressChanged: (String) -> Unit,
    onActivityTypeChanged: (String) -> Unit,
    onCertificateNumberChanged: (String) -> Unit,
    onCertificateIssuedDateChanged: (String) -> Unit,
    onEffectiveDateChanged: (java.time.LocalDate) -> Unit,
    onTaxRatePercentChanged: (String) -> Unit
) {
    AppSection(
        title = stringResource(R.string.onboarding_wizard_step_details_title),
        modifier = Modifier.testTag("onboarding-step-details")
    ) {
        Text(
            text = stringResource(R.string.onboarding_wizard_step_details_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        uiState.infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }

    AppSection(title = stringResource(R.string.onboarding_section_taxpayer)) {
        OnboardingTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChanged,
            label = stringResource(R.string.onboarding_display_name),
            error = uiState.displayNameError,
            testTag = "onboarding-display-name-field"
        )
        OnboardingTextField(
            value = uiState.legalForm,
            onValueChange = onLegalFormChanged,
            label = stringResource(R.string.onboarding_legal_form)
        )
        OnboardingTextField(
            value = uiState.registrationId,
            onValueChange = onRegistrationIdChanged,
            label = stringResource(R.string.onboarding_registration_id),
            error = uiState.registrationIdError,
            testTag = "onboarding-registration-id-field"
        )
        OnboardingTextField(
            value = uiState.registrationDate,
            onValueChange = onRegistrationDateChanged,
            label = stringResource(R.string.onboarding_registration_date),
            error = uiState.registrationDateError,
            supportingText = stringResource(R.string.onboarding_optional_iso_date_hint)
        )
        OnboardingTextField(
            value = uiState.legalAddress,
            onValueChange = onLegalAddressChanged,
            label = stringResource(R.string.onboarding_legal_address),
            minLines = 2
        )
        OnboardingTextField(
            value = uiState.activityType,
            onValueChange = onActivityTypeChanged,
            label = stringResource(R.string.onboarding_activity_type)
        )
    }

    AppSection(title = stringResource(R.string.onboarding_section_status)) {
        OnboardingTextField(
            value = uiState.certificateNumber,
            onValueChange = onCertificateNumberChanged,
            label = stringResource(R.string.onboarding_certificate_number)
        )
        OnboardingTextField(
            value = uiState.certificateIssuedDate,
            onValueChange = onCertificateIssuedDateChanged,
            label = stringResource(R.string.onboarding_certificate_issued_date),
            error = uiState.certificateIssuedDateError,
            supportingText = stringResource(R.string.onboarding_optional_iso_date_hint)
        )
        DatePickerField(
            label = stringResource(R.string.onboarding_effective_date),
            value = uiState.effectiveDate,
            onValueChange = onEffectiveDateChanged,
            testTag = "onboarding-effective-date-field"
        )
        OnboardingTextField(
            value = uiState.taxRatePercent,
            onValueChange = onTaxRatePercentChanged,
            label = stringResource(R.string.onboarding_default_tax_rate),
            error = uiState.taxRatePercentError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            testTag = "onboarding-tax-rate-field"
        )
    }
}

@Composable
private fun RestoredFieldSummary(uiState: OnboardingUiState) {
    KeyValueRow(
        stringResource(R.string.onboarding_display_name),
        uiState.displayName.ifBlank { stringResource(R.string.common_not_detected) }
    )
    KeyValueRow(
        stringResource(R.string.onboarding_registration_id),
        uiState.registrationId.ifBlank { stringResource(R.string.common_not_detected) }
    )
    KeyValueRow(
        stringResource(R.string.onboarding_effective_date),
        uiState.effectiveDate.toString()
    )
    KeyValueRow(
        stringResource(R.string.onboarding_default_tax_rate),
        uiState.taxRatePercent
    )
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supportingText: String? = null,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    testTag: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText =
        (error ?: supportingText)?.let { text ->
            { Text(text) }
        },
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    )
}

@Composable
private fun PreviewHeader(preview: OnboardingImportPreview) {
    KeyValueRow(stringResource(R.string.common_selected_file), preview.sourceFileName)
    KeyValueRow(
        stringResource(R.string.onboarding_detected_document),
        when (preview.documentType) {
            OnboardingDocumentType.REGISTRY_EXTRACT -> stringResource(
                R.string.onboarding_document_registry_extract
            )
            OnboardingDocumentType.SMALL_BUSINESS_STATUS_CERTIFICATE -> stringResource(
                R.string.onboarding_document_sbs_certificate
            )
        }
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
    }
)

@Composable
private fun PreviewField(label: String, value: String?, confidence: ExtractionConfidence) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value ?: stringResource(R.string.common_not_detected),
            style = MaterialTheme.typography.bodyLarge
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
                        }
                    )
                )
            }
        )
    }
}
