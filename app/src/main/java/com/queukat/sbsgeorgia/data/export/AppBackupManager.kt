package com.queukat.sbsgeorgia.data.export

import androidx.room.withTransaction
import com.queukat.sbsgeorgia.data.local.FxRateDao
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.local.ImportedStatementDao
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionDao
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryDao
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordDao
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigDao
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigDao
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileDao
import com.queukat.sbsgeorgia.di.IoDispatcher
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class AppBackupManager @Inject constructor(
    private val database: SbsGeorgiaDatabase,
    private val taxpayerProfileDao: TaxpayerProfileDao,
    private val statusConfigDao: SmallBusinessStatusConfigDao,
    private val reminderConfigDao: ReminderConfigDao,
    private val incomeEntryDao: IncomeEntryDao,
    private val monthlyDeclarationRecordDao: MonthlyDeclarationRecordDao,
    private val fxRateDao: FxRateDao,
    private val importedStatementDao: ImportedStatementDao,
    private val importedTransactionDao: ImportedTransactionDao,
    private val json: Json,
    private val backupValidator: BackupValidator,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun exportJson(): String = withContext(ioDispatcher) {
        val document = AppBackupDocument(
            exportedAtEpochMillis = clock.millis(),
            taxpayerProfile = taxpayerProfileDao.get()?.toPayload(),
            statusConfig = statusConfigDao.get()?.toPayload(),
            reminderConfig = reminderConfigDao.get()?.toPayload(),
            incomeEntries = incomeEntryDao.getAll().map(IncomeEntryEntity::toPayload),
            monthlyDeclarationRecords = monthlyDeclarationRecordDao.getAll().map(MonthlyDeclarationRecordEntity::toPayload),
            fxRates = fxRateDao.getAll().map(FxRateEntity::toPayload),
            importedStatements = importedStatementDao.getAll().map(ImportedStatementEntity::toPayload),
            importedTransactions = importedTransactionDao.getAll().map(ImportedTransactionEntity::toPayload),
        )
        json.encodeToString(document)
    }

    suspend fun importJson(content: String): BackupRestoreResult = withContext(ioDispatcher) {
        val restorePlan = backupValidator.buildRestorePlan(content)

        database.withTransaction {
            clearAllTablesForRestore()

            restorePlan.taxpayerProfile?.let { taxpayerProfileDao.upsert(it) }
            restorePlan.statusConfig?.let { statusConfigDao.upsert(it) }
            restorePlan.reminderConfig?.let { reminderConfigDao.upsert(it) }
            if (restorePlan.incomeEntries.isNotEmpty()) {
                incomeEntryDao.insertAll(restorePlan.incomeEntries)
            }
            if (restorePlan.monthlyDeclarationRecords.isNotEmpty()) {
                monthlyDeclarationRecordDao.insertAll(restorePlan.monthlyDeclarationRecords)
            }
            if (restorePlan.fxRates.isNotEmpty()) {
                fxRateDao.insertAll(restorePlan.fxRates)
            }
            if (restorePlan.importedStatements.isNotEmpty()) {
                importedStatementDao.insertAll(restorePlan.importedStatements)
            }
            if (restorePlan.importedTransactions.isNotEmpty()) {
                importedTransactionDao.replaceAll(restorePlan.importedTransactions)
            }
        }

        restorePlan.toRestoreResult()
    }

    private suspend fun clearAllTablesForRestore() {
        importedTransactionDao.clear()
        importedStatementDao.clear()
        incomeEntryDao.clear()
        monthlyDeclarationRecordDao.clear()
        fxRateDao.clear()
        reminderConfigDao.clear()
        statusConfigDao.clear()
        taxpayerProfileDao.clear()
    }
}
