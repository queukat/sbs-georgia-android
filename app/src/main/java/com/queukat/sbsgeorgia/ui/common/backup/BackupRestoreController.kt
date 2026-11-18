package com.queukat.sbsgeorgia.ui.common.backup

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.BackupRestoreLog
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.data.export.runLoggedOperation
import com.queukat.sbsgeorgia.data.export.safeUriDiagnostic
import com.queukat.sbsgeorgia.domain.model.isSetupComplete
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.repository.TextDocumentStore
import com.queukat.sbsgeorgia.domain.service.ReminderSchedulePort
import com.queukat.sbsgeorgia.domain.usecase.ImportBackupJsonUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class BackupRestoreUiResult(val message: String, val setupComplete: Boolean)

class BackupRestoreController
@Inject
constructor(
    private val textDocumentStore: TextDocumentStore,
    private val importBackupJsonUseCase: ImportBackupJsonUseCase,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderSchedulePort,
    @param:ApplicationContext private val context: Context
) {
    suspend fun restore(uriString: String): BackupRestoreUiResult {
        val uriDiag = safeUriDiagnostic(uriString)
        BackupRestoreLog.info("BackupController", "restore start ($uriDiag)")
        return runLoggedOperation("BackupController", "restore failed ($uriDiag)") {
            val content = textDocumentStore.readText(uriString)
            BackupRestoreLog.info("BackupController", "document loaded (chars=${content.length}), importing JSON")

            val result = importBackupJsonUseCase(content)
            BackupRestoreLog.info(
                "BackupController",
                "importJson finished (income=${result.incomeEntryCount}, " +
                    "monthly=${result.monthlyRecordCount}, statements=${result.importedStatementCount}, " +
                    "tx=${result.importedTransactionCount}, reminderConfig=${result.reminderConfigImported})"
            )

            val importedReminderConfig = settingsRepository.observeReminderConfig().first()
            if (importedReminderConfig != null) {
                reminderScheduler.reschedule(importedReminderConfig)
                BackupRestoreLog.info("BackupController", "reminders rescheduled")
            } else {
                reminderScheduler.cancelAll()
                BackupRestoreLog.info("BackupController", "reminders cancelled (none in backup)")
            }

            val profile = settingsRepository.observeTaxpayerProfile().first()
            val config = settingsRepository.observeStatusConfig().first()
            val setupComplete = isSetupComplete(profile, config)
            BackupRestoreLog.info(
                "BackupController",
                "restore finished (hasProfile=${profile != null}, hasConfig=${config != null}, " +
                    "setupComplete=$setupComplete)"
            )

            BackupRestoreUiResult(
                message = result.toSummaryMessage(context),
                setupComplete = setupComplete
            )
        }
    }
}

fun BackupRestoreResult.toSummaryMessage(context: Context): String = context.getString(
    R.string.settings_message_backup_imported_summary,
    incomeEntryCount,
    monthlyRecordCount,
    importedStatementCount,
    importedTransactionCount
)
