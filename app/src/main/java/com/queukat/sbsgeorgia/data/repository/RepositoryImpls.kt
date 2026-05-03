package com.queukat.sbsgeorgia.data.repository

import androidx.room.withTransaction
import com.queukat.sbsgeorgia.data.local.ImportedTransactionDao
import com.queukat.sbsgeorgia.data.local.IncomeEntryDao
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordDao
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigDao
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigDao
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileDao
import com.queukat.sbsgeorgia.data.local.toDomain
import com.queukat.sbsgeorgia.data.local.toEntity
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl
@Inject
constructor(
    private val taxpayerProfileDao: TaxpayerProfileDao,
    private val statusConfigDao: SmallBusinessStatusConfigDao,
    private val reminderConfigDao: ReminderConfigDao
) : SettingsRepository {
    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> = taxpayerProfileDao.observe().map {
        it?.toDomain()
    }

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = statusConfigDao.observe().map {
        it?.toDomain()
    }

    override fun observeReminderConfig(): Flow<ReminderConfig?> = reminderConfigDao.observe().map {
        it?.toDomain()
    }

    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) {
        taxpayerProfileDao.upsert(profile.toEntity())
    }

    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) {
        statusConfigDao.upsert(config.toEntity())
    }

    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        reminderConfigDao.upsert(config.toEntity())
    }
}

@Singleton
class IncomeRepositoryImpl
@Inject
constructor(
    private val database: SbsGeorgiaDatabase,
    private val incomeEntryDao: IncomeEntryDao,
    private val importedTransactionDao: ImportedTransactionDao
) : IncomeRepository {
    override fun observeAll(): Flow<List<IncomeEntry>> = incomeEntryDao.observeAll().map { entities ->
        entities.map(IncomeEntryEntity::toDomain)
    }

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = incomeEntryDao
        .observeByMonth(
            startDate = yearMonth.atDay(1).toString(),
            endDate = yearMonth.atEndOfMonth().toString()
        ).map { entities -> entities.map(IncomeEntryEntity::toDomain) }

    override suspend fun getById(id: Long): IncomeEntry? = incomeEntryDao.getById(id)?.toDomain()

    override suspend fun upsert(entry: IncomeEntry): Long = database.withTransaction {
        val id = incomeEntryDao.upsert(entry.toEntity())
        entry.sourceTransactionFingerprint
            ?.takeIf(String::isNotBlank)
            ?.let { fingerprint ->
                importedTransactionDao.updateFinalInclusionByFingerprint(
                    transactionFingerprint = fingerprint,
                    finalInclusion = entry.declarationInclusion
                )
            }
        id
    }

    override suspend fun deleteById(id: Long) {
        database.withTransaction {
            val existing = incomeEntryDao.getById(id) ?: return@withTransaction
            incomeEntryDao.deleteById(id)
            existing.sourceTransactionFingerprint
                ?.takeIf(String::isNotBlank)
                ?.let { fingerprint ->
                    importedTransactionDao.updateFinalInclusionByFingerprint(
                        transactionFingerprint = fingerprint,
                        finalInclusion = DeclarationInclusion.EXCLUDED
                    )
                }
        }
    }
}

@Singleton
class MonthlyDeclarationRepositoryImpl
@Inject
constructor(
    private val monthlyDeclarationRecordDao: MonthlyDeclarationRecordDao
) : MonthlyDeclarationRepository {
    override fun observeAll(): Flow<List<MonthlyDeclarationRecord>> =
        monthlyDeclarationRecordDao.observeAll().map { entities ->
            entities.map(MonthlyDeclarationRecordEntity::toDomain)
        }

    override fun observeByMonth(yearMonth: YearMonth): Flow<MonthlyDeclarationRecord?> =
        monthlyDeclarationRecordDao.observeByPeriod(yearMonth.toString()).map { it?.toDomain() }

    override suspend fun upsert(record: MonthlyDeclarationRecord) {
        monthlyDeclarationRecordDao.upsert(record.toEntity())
    }
}

private fun IncomeEntryEntity.toDomain(): IncomeEntry = IncomeEntry(
    id = id,
    sourceType = sourceType,
    incomeDate = incomeDate,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = declarationInclusion,
    gelEquivalent = gelEquivalent,
    rateSource = rateSource,
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun IncomeEntry.toEntity(): IncomeEntryEntity = IncomeEntryEntity(
    id = id,
    sourceType = sourceType,
    incomeDate = incomeDate,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = declarationInclusion,
    gelEquivalent = gelEquivalent,
    rateSource = rateSource,
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun MonthlyDeclarationRecordEntity.toDomain(): MonthlyDeclarationRecord = MonthlyDeclarationRecord(
    yearMonth = YearMonth.of(year, month),
    workflowStatus = MonthlyWorkflowStatus.fromPersisted(workflowStatus),
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate,
    paymentSentDate = paymentSentDate,
    paymentCreditedDate = paymentCreditedDate,
    paymentAmountGel = paymentAmountGel,
    notes = notes
)

private fun MonthlyDeclarationRecord.toEntity(): MonthlyDeclarationRecordEntity = MonthlyDeclarationRecordEntity(
    periodKey = yearMonth.toString(),
    year = yearMonth.year,
    month = yearMonth.monthValue,
    workflowStatus = workflowStatus.dbCode,
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate,
    paymentSentDate = paymentSentDate,
    paymentCreditedDate = paymentCreditedDate,
    paymentAmountGel = paymentAmountGel,
    notes = notes
)
