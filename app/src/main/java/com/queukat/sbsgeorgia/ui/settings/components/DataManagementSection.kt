package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState

@Composable
internal fun DataManagementSection(
    uiState: SettingsUiState,
    onExportIncomeEntriesCsv: () -> Unit,
    onExportMonthlySummariesCsv: () -> Unit,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit,
) {
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
}
