package com.queukat.sbsgeorgia.ui.common.backup

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.usecase.ImportBackupJsonUseCase
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class BackupRestoreUiResult(
    val message: String,
    val setupComplete: Boolean,
)

class BackupRestoreController @Inject constructor(
    private val textDocumentStore: TextDocumentStore,
    private val importBackupJsonUseCase: ImportBackupJsonUseCase,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun restore(uriString: String): BackupRestoreUiResult {
        val content = textDocumentStore.readText(uriString)
        val result = importBackupJsonUseCase(content)
        val importedReminderConfig = settingsRepository.observeReminderConfig().first()
        if (importedReminderConfig != null) {
            reminderScheduler.reschedule(importedReminderConfig)
        } else {
            reminderScheduler.cancelAll()
        }

        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        val setupComplete = profile != null &&
            config != null &&
            profile.registrationId.isNotBlank() &&
            profile.displayName.isNotBlank()

        return BackupRestoreUiResult(
            message = result.toSummaryMessage(context),
            setupComplete = setupComplete,
        )
    }
}

fun BackupRestoreResult.toSummaryMessage(context: Context): String = context.getString(
    R.string.settings_message_backup_imported_summary,
    incomeEntryCount,
    monthlyRecordCount,
    importedStatementCount,
    importedTransactionCount,
)
