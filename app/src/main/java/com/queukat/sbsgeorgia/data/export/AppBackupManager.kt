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
import com.queukat.sbsgeorgia.data.local.ReminderConfigEntity
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigDao
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigEntity
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileDao
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileEntity
import com.queukat.sbsgeorgia.di.IoDispatcher
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
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
        val document = json.decodeFromString<AppBackupDocument>(content)
        require(document.formatVersion == 1) {
            "Unsupported backup format version ${document.formatVersion}."
        }

        database.withTransaction {
            clearAllTablesForRestore()

            document.taxpayerProfile?.let { taxpayerProfileDao.upsert(it.toEntity()) }
            document.statusConfig?.let { statusConfigDao.upsert(it.toEntity()) }
            document.reminderConfig?.let { reminderConfigDao.upsert(it.toEntity()) }
            if (document.incomeEntries.isNotEmpty()) {
                incomeEntryDao.insertAll(document.incomeEntries.map(IncomeEntryPayload::toEntity))
            }
            if (document.monthlyDeclarationRecords.isNotEmpty()) {
                monthlyDeclarationRecordDao.insertAll(document.monthlyDeclarationRecords.map(MonthlyDeclarationRecordPayload::toEntity))
            }
            if (document.fxRates.isNotEmpty()) {
                fxRateDao.insertAll(document.fxRates.map(FxRatePayload::toEntity))
            }
            if (document.importedStatements.isNotEmpty()) {
                importedStatementDao.insertAll(document.importedStatements.map(ImportedStatementPayload::toEntity))
            }
            if (document.importedTransactions.isNotEmpty()) {
                importedTransactionDao.replaceAll(document.importedTransactions.map(ImportedTransactionPayload::toEntity))
            }
        }

        BackupRestoreResult(
            reminderConfigImported = document.reminderConfig != null,
            incomeEntryCount = document.incomeEntries.size,
            monthlyRecordCount = document.monthlyDeclarationRecords.size,
            importedStatementCount = document.importedStatements.size,
            importedTransactionCount = document.importedTransactions.size,
        )
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

private fun TaxpayerProfileEntity.toPayload(): TaxpayerProfilePayload = TaxpayerProfilePayload(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = baseCurrencyView.name,
    legalForm = legalForm,
    registrationDate = registrationDate?.toString(),
    legalAddress = legalAddress,
    activityType = activityType,
)

private fun TaxpayerProfilePayload.toEntity(): TaxpayerProfileEntity = TaxpayerProfileEntity(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = enumValueOf(baseCurrencyView),
    legalForm = legalForm,
    registrationDate = registrationDate?.let(LocalDate::parse),
    legalAddress = legalAddress,
    activityType = activityType,
)

private fun SmallBusinessStatusConfigEntity.toPayload(): SmallBusinessStatusConfigPayload = SmallBusinessStatusConfigPayload(
    effectiveDate = effectiveDate.toString(),
    defaultTaxRatePercent = defaultTaxRatePercent.toPlainString(),
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate?.toString(),
)

private fun SmallBusinessStatusConfigPayload.toEntity(): SmallBusinessStatusConfigEntity = SmallBusinessStatusConfigEntity(
    effectiveDate = LocalDate.parse(effectiveDate),
    defaultTaxRatePercent = BigDecimal(defaultTaxRatePercent),
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate?.let(LocalDate::parse),
)

private fun ReminderConfigEntity.toPayload(): ReminderConfigPayload = ReminderConfigPayload(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = defaultReminderTime.toString(),
    themeMode = themeMode.name,
)

private fun ReminderConfigPayload.toEntity(): ReminderConfigEntity = ReminderConfigEntity(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = LocalTime.parse(defaultReminderTime),
    themeMode = enumValueOf(themeMode),
)

private fun IncomeEntryEntity.toPayload(): IncomeEntryPayload = IncomeEntryPayload(
    id = id,
    sourceType = sourceType.name,
    incomeDate = incomeDate.toString(),
    originalAmount = originalAmount.toPlainString(),
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = declarationInclusion.name,
    gelEquivalent = gelEquivalent?.toPlainString(),
    rateSource = rateSource.name,
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun IncomeEntryPayload.toEntity(): IncomeEntryEntity = IncomeEntryEntity(
    id = id,
    sourceType = enumValueOf(sourceType),
    incomeDate = LocalDate.parse(incomeDate),
    originalAmount = BigDecimal(originalAmount),
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = enumValueOf(declarationInclusion),
    gelEquivalent = gelEquivalent?.let(::BigDecimal),
    rateSource = enumValueOf(rateSource),
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun MonthlyDeclarationRecordEntity.toPayload(): MonthlyDeclarationRecordPayload = MonthlyDeclarationRecordPayload(
    periodKey = periodKey,
    year = year,
    month = month,
    workflowStatus = workflowStatus,
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate?.toString(),
    paymentSentDate = paymentSentDate?.toString(),
    paymentCreditedDate = paymentCreditedDate?.toString(),
    paymentAmountGel = paymentAmountGel?.toPlainString(),
    notes = notes,
)

private fun MonthlyDeclarationRecordPayload.toEntity(): MonthlyDeclarationRecordEntity = MonthlyDeclarationRecordEntity(
    periodKey = periodKey,
    year = year,
    month = month,
    workflowStatus = workflowStatus,
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate?.let(LocalDate::parse),
    paymentSentDate = paymentSentDate?.let(LocalDate::parse),
    paymentCreditedDate = paymentCreditedDate?.let(LocalDate::parse),
    paymentAmountGel = paymentAmountGel?.let(::BigDecimal),
    notes = notes,
)

private fun FxRateEntity.toPayload(): FxRatePayload = FxRatePayload(
    id = id,
    rateDate = rateDate.toString(),
    currencyCode = currencyCode,
    units = units,
    rateToGel = rateToGel.toPlainString(),
    source = source.name,
    manualOverride = manualOverride,
)

private fun FxRatePayload.toEntity(): FxRateEntity = FxRateEntity(
    id = id,
    rateDate = LocalDate.parse(rateDate),
    currencyCode = currencyCode,
    units = units,
    rateToGel = BigDecimal(rateToGel),
    source = enumValueOf(source),
    manualOverride = manualOverride,
)

private fun ImportedStatementEntity.toPayload(): ImportedStatementPayload = ImportedStatementPayload(
    id = id,
    sourceFileName = sourceFileName,
    sourceFingerprint = sourceFingerprint,
    importedAtEpochMillis = importedAtEpochMillis,
)

private fun ImportedStatementPayload.toEntity(): ImportedStatementEntity = ImportedStatementEntity(
    id = id,
    sourceFileName = sourceFileName,
    sourceFingerprint = sourceFingerprint,
    importedAtEpochMillis = importedAtEpochMillis,
)

private fun ImportedTransactionEntity.toPayload(): ImportedTransactionPayload = ImportedTransactionPayload(
    id = id,
    statementId = statementId,
    transactionFingerprint = transactionFingerprint,
    incomeDate = incomeDate?.toString(),
    description = description,
    additionalInformation = additionalInformation,
    paidOut = paidOut?.toPlainString(),
    paidIn = paidIn?.toPlainString(),
    balance = balance?.toPlainString(),
    suggestedInclusion = suggestedInclusion.name,
    finalInclusion = finalInclusion.name,
)

private fun ImportedTransactionPayload.toEntity(): ImportedTransactionEntity = ImportedTransactionEntity(
    id = id,
    statementId = statementId,
    transactionFingerprint = transactionFingerprint,
    incomeDate = incomeDate?.let(LocalDate::parse),
    description = description,
    additionalInformation = additionalInformation,
    paidOut = paidOut?.let(::BigDecimal),
    paidIn = paidIn?.let(::BigDecimal),
    balance = balance?.let(::BigDecimal),
    suggestedInclusion = enumValueOf(suggestedInclusion),
    finalInclusion = enumValueOf(finalInclusion),
)
