package com.queukat.sbsgeorgia.ui.settings

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.ExportBackupJsonUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportIncomeEntriesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ExportMonthlySummariesCsvUseCase
import com.queukat.sbsgeorgia.domain.usecase.ImportBackupJsonUseCase
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import kotlinx.coroutines.flow.first

internal data class SettingsBackupActionResult(
    val message: String,
    val shouldReloadSettings: Boolean = false,
)

internal class SettingsBackupActions(
    private val textDocumentStore: TextDocumentStore,
    private val exportIncomeEntriesCsvUseCase: ExportIncomeEntriesCsvUseCase,
    private val exportMonthlySummariesCsvUseCase: ExportMonthlySummariesCsvUseCase,
    private val exportBackupJsonUseCase: ExportBackupJsonUseCase,
    private val importBackupJsonUseCase: ImportBackupJsonUseCase,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val context: Context,
) {
    suspend fun exportIncomeEntriesCsv(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportIncomeEntriesCsvUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_income_csv_exported),
        )
    }

    suspend fun exportMonthlySummariesCsv(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportMonthlySummariesCsvUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_monthly_csv_exported),
        )
    }

    suspend fun exportBackupJson(uriString: String): SettingsBackupActionResult {
        textDocumentStore.writeText(uriString, exportBackupJsonUseCase())
        return SettingsBackupActionResult(
            message = context.getString(R.string.settings_message_backup_exported),
        )
    }

    suspend fun importBackupJson(uriString: String): SettingsBackupActionResult {
        val content = textDocumentStore.readText(uriString)
        val result = importBackupJsonUseCase(content)
        val importedReminderConfig = settingsRepository.observeReminderConfig().first()
        if (importedReminderConfig != null) {
            reminderScheduler.reschedule(importedReminderConfig)
        } else {
            reminderScheduler.cancelAll()
        }
        return SettingsBackupActionResult(
            message = result.toSummaryMessage(context),
            shouldReloadSettings = true,
        )
    }
}

internal fun BackupRestoreResult.toSummaryMessage(context: Context): String = context.getString(
    R.string.settings_message_backup_imported_summary,
    incomeEntryCount,
    monthlyRecordCount,
    importedStatementCount,
    importedTransactionCount,
)
