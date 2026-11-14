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
import com.queukat.sbsgeorgia.domain.model.ImportedStatementImportInfo
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.normalizeCurrencyCode
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatementImportRepositoryImpl
@Inject
constructor(
    private val database: SbsGeorgiaDatabase,
    private val importedStatementDao: ImportedStatementDao,
    private val importedTransactionDao: ImportedTransactionDao,
    private val incomeEntryDao: IncomeEntryDao
) : StatementImportRepository {
    override suspend fun getStatementImportInfo(sourceFingerprint: String): ImportedStatementImportInfo? =
        importedStatementDao.getBySourceFingerprint(sourceFingerprint)?.let { entity ->
            ImportedStatementImportInfo(
                sourceFileName = entity.sourceFileName,
                sourceFingerprint = entity.sourceFingerprint,
                importedAtEpochMillis = entity.importedAtEpochMillis
            )
        }

    override suspend fun existingTransactionFingerprints(fingerprints: Set<String>): Set<String> = buildSet {
        // Stay safely below the SQLite bind-parameter limit on old Android versions.
        fingerprints.toList().chunked(400).forEach { batch ->
            addAll(importedTransactionDao.existingFingerprints(batch))
            addAll(incomeEntryDao.existingSourceFingerprints(batch))
        }
    }

    override suspend fun recoverableTransactionFingerprints(fingerprints: Set<String>): Set<String> = buildSet {
        fingerprints.toList().chunked(400).forEach { batch ->
            addAll(importedTransactionDao.recoverableFingerprints(batch))
        }
    }

    override suspend fun confirmImport(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
        importedAtEpochMillis: Long
    ): ConfirmImportedStatementResult = database.withTransaction {
        val statementId =
            importedStatementDao.getBySourceFingerprint(sourceFingerprint)?.id
                ?: importedStatementDao.insert(
                    ImportedStatementEntity(
                        sourceFileName = sourceFileName,
                        sourceFingerprint = sourceFingerprint,
                        importedAtEpochMillis = importedAtEpochMillis
                    )
                )

        // Re-evaluate all duplicate guards inside the write transaction: the
        // user's preview may be stale or another import may have completed.
        val uniqueRows = rows.filterNot { it.duplicate }
            .distinctBy { it.transactionFingerprint }
        val fingerprints = uniqueRows.mapTo(mutableSetOf()) { it.transactionFingerprint }
        val existing = existingTransactionFingerprints(fingerprints)
        val recoverable = recoverableTransactionFingerprints(fingerprints)
        val newRows = uniqueRows.filterNot { it.transactionFingerprint in existing }
        val restoring = uniqueRows.filter {
            it.transactionFingerprint in recoverable &&
                it.finalInclusion == DeclarationInclusion.INCLUDED
        }
        val insertResults = importedTransactionDao.insertAll(
            newRows.map { row ->
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
                    finalInclusion = row.finalInclusion
                )
            }
        )

        var storedTransactionCount = 0
        var importedIncomeCount = 0
        var skippedDuplicateCount = rows.size - newRows.size - restoring.size
        var excludedCount = 0

        newRows.zip(insertResults).forEach { (row, insertedId) ->
            if (insertedId == -1L) {
                skippedDuplicateCount += 1
                return@forEach
            }
            storedTransactionCount += 1
            if (row.finalInclusion == DeclarationInclusion.INCLUDED) {
                insertIncomeEntry(row, statementId, importedAtEpochMillis)
                importedIncomeCount += 1
            } else {
                excludedCount += 1
            }
        }

        restoring.forEach { row ->
            val previous = requireNotNull(importedTransactionDao.getByFingerprint(row.transactionFingerprint))
            val incomeDate = requireNotNull(row.incomeDate) {
                "Restored imported income rows must have an income date."
            }
            check(importedTransactionDao.restoreIncludedByFingerprint(row.transactionFingerprint, incomeDate) == 1)
            // Keep the original statement link; a re-imported overlapping PDF
            // may have a different source fingerprint and therefore statementId.
            insertIncomeEntry(row, previous.statementId, importedAtEpochMillis)
            importedIncomeCount += 1
        }

        ConfirmImportedStatementResult(
            importedIncomeCount = importedIncomeCount,
            storedTransactionCount = storedTransactionCount,
            skippedDuplicateCount = skippedDuplicateCount,
            excludedCount = excludedCount
        )
    }

    private suspend fun insertIncomeEntry(
        row: ApprovedImportedStatementRow,
        statementId: Long,
        importedAtEpochMillis: Long
    ) {
        val incomeDate = requireNotNull(row.incomeDate) {
            "Included imported income rows must have an income date."
        }
        incomeEntryDao.upsert(
            IncomeEntryEntity(
                sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                incomeDate = incomeDate,
                originalAmount = row.amount,
                originalCurrency = normalizeCurrencyCode(row.currency),
                sourceCategory = row.sourceCategory.trim(),
                note = buildNote(row.description, row.additionalInformation),
                declarationInclusion = DeclarationInclusion.INCLUDED,
                gelEquivalent = row.amount.takeIf { normalizeCurrencyCode(row.currency) == "GEL" },
                rateSource = FxRateSource.NONE,
                manualFxOverride = false,
                sourceStatementId = statementId,
                sourceTransactionFingerprint = row.transactionFingerprint,
                createdAtEpochMillis = importedAtEpochMillis,
                updatedAtEpochMillis = importedAtEpochMillis
            )
        )
    }

    private fun buildNote(description: String, additionalInformation: String?): String =
        listOf(description.trim(), additionalInformation?.trim().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
}
