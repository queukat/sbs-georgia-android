package com.queukat.sbsgeorgia.ui.settings

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.domain.usecase.ExportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportIncomeEntriesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportMonthlySummariesCsvUseCase

internal data class SettingsBackupActionResult(val message: String, val shouldReloadSettings: Boolean = false)

internal class SettingsBackupActions(
    private val textDocumentStore: TextDocumentStore,
    private val exportIncomeEntriesCsvUseCase: ExportIncomeEntriesCsvUseCase,
    private val exportMonthlySummariesCsvUseCase: ExportMonthlySummariesCsvUseCase,
    private val exportBackupJsonUseCase: ExportBackupJsonUseCase,
    private val context: Context
) {
    suspend fun exportIncomeEntriesCsv(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportIncomeEntriesCsvUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_income_csv_exported)
        )
    }

    suspend fun exportMonthlySummariesCsv(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportMonthlySummariesCsvUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_monthly_csv_exported)
        )
    }

    suspend fun exportBackupJson(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportBackupJsonUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_backup_exported)
        )
    }
}
