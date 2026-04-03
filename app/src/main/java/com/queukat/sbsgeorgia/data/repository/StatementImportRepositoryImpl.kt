package com.queukat.sbsgeorgia.data.repository

import androidx.room.withTransaction
import com.queukat.sbsgeorgia.data.local.ImportedStatementDao
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionDao
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryDao
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmImportedStatementResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatementImportRepositoryImpl @Inject constructor(
    private val database: SbsGeorgiaDatabase,
    private val importedStatementDao: ImportedStatementDao,
    private val importedTransactionDao: ImportedTransactionDao,
    private val incomeEntryDao: IncomeEntryDao,
) : StatementImportRepository {
    override suspend fun hasStatementFingerprint(sourceFingerprint: String): Boolean =
        importedStatementDao.existsBySourceFingerprint(sourceFingerprint)

    override suspend fun hasTransactionFingerprint(transactionFingerprint: String): Boolean =
        importedTransactionDao.existsByFingerprint(transactionFingerprint) ||
            incomeEntryDao.existsBySourceTransactionFingerprint(transactionFingerprint)

    override suspend fun confirmImport(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
        importedAtEpochMillis: Long,
    ): ConfirmImportedStatementResult = database.withTransaction {
        if (importedStatementDao.existsBySourceFingerprint(sourceFingerprint)) {
            return@withTransaction ConfirmImportedStatementResult(
                importedIncomeCount = 0,
                storedTransactionCount = 0,
                skippedDuplicateCount = rows.size,
                excludedCount = rows.count { it.finalInclusion != DeclarationInclusion.INCLUDED },
            )
        }

        val statementId = importedStatementDao.insert(
            ImportedStatementEntity(
                sourceFileName = sourceFileName,
                sourceFingerprint = sourceFingerprint,
                importedAtEpochMillis = importedAtEpochMillis,
            ),
        )

        val nonPreviewDuplicates = rows.filterNot { it.duplicate }
        val insertResults = importedTransactionDao.insertAll(
            nonPreviewDuplicates.map { row ->
                ImportedTransactionEntity(
                    statementId = statementId,
                    transactionFingerprint = row.transactionFingerprint,
                    incomeDate = row.incomeDate,
                    description = row.description,
                    additionalInformation = row.additionalInformation,
                    paidOut = row.paidOut?.amount,
                    paidIn = row.paidIn?.amount,
                    balance = row.balance?.amount,
                    suggestedInclusion = row.suggestedInclusion,
                    finalInclusion = row.finalInclusion,
                )
            },
        )

        var storedTransactionCount = 0
        var importedIncomeCount = 0
        var skippedDuplicateCount = rows.count { it.duplicate }

        nonPreviewDuplicates.zip(insertResults).forEach { (row, insertId) ->
            if (insertId == -1L) {
                skippedDuplicateCount += 1
                return@forEach
            }

            storedTransactionCount += 1
            if (row.finalInclusion == DeclarationInclusion.INCLUDED) {
                incomeEntryDao.upsert(
                    IncomeEntryEntity(
                        sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                        incomeDate = row.incomeDate,
                        originalAmount = row.amount,
                        originalCurrency = row.currency.uppercase(),
                        sourceCategory = row.sourceCategory.trim(),
                        note = buildNote(row.description, row.additionalInformation),
                        declarationInclusion = DeclarationInclusion.INCLUDED,
                        gelEquivalent = null,
                        rateSource = FxRateSource.NONE,
                        manualFxOverride = false,
                        sourceStatementId = statementId,
                        sourceTransactionFingerprint = row.transactionFingerprint,
                        createdAtEpochMillis = importedAtEpochMillis,
                        updatedAtEpochMillis = importedAtEpochMillis,
                    ),
                )
                importedIncomeCount += 1
            }
        }

        ConfirmImportedStatementResult(
            importedIncomeCount = importedIncomeCount,
            storedTransactionCount = storedTransactionCount,
            skippedDuplicateCount = skippedDuplicateCount,
            excludedCount = rows.count { it.finalInclusion != DeclarationInclusion.INCLUDED },
        )
    }

    private fun buildNote(description: String, additionalInformation: String?): String =
        listOf(description.trim(), additionalInformation?.trim().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
}
