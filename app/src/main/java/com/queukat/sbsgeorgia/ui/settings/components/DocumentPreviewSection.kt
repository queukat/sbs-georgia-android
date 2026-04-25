package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow

@Composable
internal fun DocumentPreviewSection(
    preview: OnboardingImportPreview,
    isActionEnabled: Boolean,
    onApplyPreview: () -> Unit,
) {
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
            enabled = isActionEnabled,
        ) {
            Text(stringResource(R.string.onboarding_apply_preview))
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
