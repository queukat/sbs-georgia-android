package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState

@Composable
internal fun DocumentImportSection(
    uiState: SettingsUiState,
    onImportRegistryExtract: () -> Unit,
    onImportCertificate: () -> Unit
) {
    AppSection(title = stringResource(R.string.settings_section_document_imports)) {
        Text(
            stringResource(R.string.settings_document_imports_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onImportRegistryExtract,
                enabled =
                !uiState.isDocumentLoading &&
                    !uiState.isSaving &&
                    !uiState.isDataOperationInProgress
            ) {
                Text(
                    stringResource(
                        if (uiState.isDocumentLoading) {
                            R.string.import_statement_parsing
                        } else {
                            R.string.onboarding_import_registration_pdf
                        }
                    )
                )
            }
            Button(
                onClick = onImportCertificate,
                enabled =
                !uiState.isDocumentLoading &&
                    !uiState.isSaving &&
                    !uiState.isDataOperationInProgress
            ) {
                Text(
                    stringResource(
                        if (uiState.isDocumentLoading) {
                            R.string.import_statement_parsing
                        } else {
                            R.string.onboarding_import_sbs_certificate_pdf
                        }
                    )
                )
            }
        }
        if (uiState.isDocumentLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        uiState.documentInfoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        uiState.documentErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
