@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.onboarding

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow

@Composable
fun OnboardingRoute(
    innerPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingImportAction by remember { mutableStateOf<OnboardingImportAction?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val action = pendingImportAction
        if (uri != null && action != null) {
            viewModel.loadDocument(uri, action)
        }
        pendingImportAction = null
    }

    OnboardingScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onImportRegistryExtract = {
            pendingImportAction = OnboardingImportAction.IMPORT_REGISTRY_EXTRACT
            pickerLauncher.launch(arrayOf("application/pdf"))
        },
        onImportCertificate = {
            pendingImportAction = OnboardingImportAction.IMPORT_SMALL_BUSINESS_CERTIFICATE
            pickerLauncher.launch(arrayOf("application/pdf"))
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
        onComplete = viewModel::completeOnboarding,
    )
}

@Composable
fun OnboardingScreen(
    innerPadding: PaddingValues,
    uiState: OnboardingUiState,
    onImportRegistryExtract: () -> Unit,
    onImportCertificate: () -> Unit,
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
    onComplete: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppSection(title = stringResource(R.string.onboarding_section_intro)) {
                Text(
                    text = stringResource(R.string.onboarding_intro_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IntroStepRow(
                    number = "1",
                    title = stringResource(R.string.onboarding_intro_step_one_title),
                    body = stringResource(R.string.onboarding_intro_step_one_body),
                )
                IntroStepRow(
                    number = "2",
                    title = stringResource(R.string.onboarding_intro_step_two_title),
                    body = stringResource(R.string.onboarding_intro_step_two_body),
                )
                IntroStepRow(
                    number = "3",
                    title = stringResource(R.string.onboarding_intro_step_three_title),
                    body = stringResource(R.string.onboarding_intro_step_three_body),
                )
            }

            AppSection(title = stringResource(R.string.onboarding_section_options)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onImportRegistryExtract,
                        enabled = !uiState.isLoading && !uiState.isSaving,
                        modifier = Modifier.testTag("onboarding-import-registry-button"),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isLoading) {
                                    R.string.import_statement_parsing
                                } else {
                                    R.string.onboarding_import_registration_pdf
                                },
                            ),
                        )
                    }
                    Button(
                        onClick = onImportCertificate,
                        enabled = !uiState.isLoading && !uiState.isSaving,
                        modifier = Modifier.testTag("onboarding-import-certificate-button"),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isLoading) {
                                    R.string.import_statement_parsing
                                } else {
                                    R.string.onboarding_import_sbs_certificate_pdf
                                },
                            ),
                        )
                    }
                }
                Text(stringResource(R.string.onboarding_manual_entry_hint))
                uiState.infoMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                uiState.errorMessage?.let {
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
                        enabled = !uiState.isSaving,
                        modifier = Modifier.testTag("onboarding-apply-preview-button"),
                    ) {
                        Text(stringResource(R.string.onboarding_apply_preview))
                    }
                }
            }

            AppSection(title = stringResource(R.string.onboarding_section_taxpayer)) {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = onDisplayNameChanged,
                    label = { Text(stringResource(R.string.onboarding_display_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding-display-name-field"),
                )
                OutlinedTextField(
                    value = uiState.legalForm,
                    onValueChange = onLegalFormChanged,
                    label = { Text(stringResource(R.string.onboarding_legal_form)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.registrationId,
                    onValueChange = onRegistrationIdChanged,
                    label = { Text(stringResource(R.string.onboarding_registration_id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding-registration-id-field"),
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

            AppSection(title = stringResource(R.string.onboarding_section_status)) {
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
                    label = stringResource(R.string.onboarding_effective_date),
                    value = uiState.effectiveDate,
                    onValueChange = onEffectiveDateChanged,
                    testTag = "onboarding-effective-date-field",
                )
                DecimalField(
                    label = stringResource(R.string.onboarding_default_tax_rate),
                    value = uiState.taxRatePercent,
                    onValueChange = onTaxRatePercentChanged,
                    testTag = "onboarding-tax-rate-field",
                )
            }

            AppSection(title = stringResource(R.string.onboarding_section_continue)) {
                Button(
                    onClick = onComplete,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding-complete-button"),
                ) {
                    Text(
                        stringResource(
                            if (uiState.isSaving) {
                                R.string.workflow_saving
                            } else {
                                R.string.onboarding_continue_to_app
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroStepRow(
    number: String,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
