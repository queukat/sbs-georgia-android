package com.queukat.sbsgeorgia.data.export

import com.queukat.sbsgeorgia.data.local.DeclarationFormConfigEntity
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigEntity
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigEntity
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileEntity
import com.queukat.sbsgeorgia.data.local.toEntity
import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import com.queukat.sbsgeorgia.domain.model.normalizeCurrencyCode
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.serialization.json.Json

internal data class BackupRestorePlan(
    val taxpayerProfile: TaxpayerProfileEntity?,
    val statusConfig: SmallBusinessStatusConfigEntity?,
    val reminderConfig: ReminderConfigEntity?,
    val declarationFormConfig: DeclarationFormConfigEntity,
    val incomeEntries: List<IncomeEntryEntity>,
    val monthlyDeclarationRecords: List<MonthlyDeclarationRecordEntity>,
    val fxRates: List<FxRateEntity>,
    val importedStatements: List<ImportedStatementEntity>,
    val importedTransactions: List<ImportedTransactionEntity>
) {
    fun toRestoreResult(): BackupRestoreResult = BackupRestoreResult(
        reminderConfigImported = reminderConfig != null,
        incomeEntryCount = incomeEntries.size,
        monthlyRecordCount = monthlyDeclarationRecords.size,
        importedStatementCount = importedStatements.size,
        importedTransactionCount = importedTransactions.size
    )
}

class BackupValidator
@Inject
constructor(private val json: Json) {
    internal fun buildRestorePlan(content: String): BackupRestorePlan {
        BackupRestoreLog.info("BackupValidator", "buildRestorePlan start (chars=${content.length})")
        val document =
            runCatching { json.decodeFromString<AppBackupDocument>(content) }
                .getOrElse { error ->
                    BackupRestoreLog.error("BackupValidator", "JSON decode failed", error)
                    throw IllegalArgumentException("Backup JSON is invalid.", error)
                }

        BackupRestoreLog.info(
            "BackupValidator",
            "JSON decoded (formatVersion=${document.formatVersion}, " +
                "hasProfile=${document.taxpayerProfile != null}, " +
                "hasStatus=${document.statusConfig != null}, " +
                "hasReminder=${document.reminderConfig != null}, " +
                "hasForm=${document.declarationFormConfig != null}, " +
                "income=${document.incomeEntries.size}, " +
                "monthly=${document.monthlyDeclarationRecords.size}, " +
                "fx=${document.fxRates.size}, " +
                "statements=${document.importedStatements.size}, " +
                "tx=${document.importedTransactions.size})"
        )

        require(document.formatVersion == 1) {
            BackupRestoreLog.error(
                "BackupValidator",
                "Unsupported formatVersion=${document.formatVersion}"
            )
            "Unsupported backup format version ${document.formatVersion}."
        }

        val restorePlan =
            BackupRestorePlan(
                taxpayerProfile = document.taxpayerProfile?.validateAs("taxpayer profile") {
                    toEntity()
                },
                statusConfig = document.statusConfig?.validateAs("status config") {
                    toEntity()
                },
                reminderConfig = document.reminderConfig?.validateAs("reminder config") {
                    toEntity()
                },
                declarationFormConfig = document.declarationFormConfig?.validateAs(
                    "declaration form config"
                ) {
                    toEntity()
                } ?: DeclarationFormConfig().toEntity(),
                incomeEntries =
                document.incomeEntries.mapIndexed { index, payload ->
                    payload.validateAs("income entry #$index") { toEntity() }
                },
                monthlyDeclarationRecords =
                document.monthlyDeclarationRecords.mapIndexed { index, payload ->
                    payload.validateAs("monthly declaration record #$index") { toEntity() }
                },
                fxRates =
                document.fxRates.mapIndexed { index, payload ->
                    payload.validateAs("FX rate #$index") { toEntity() }
                },
                importedStatements =
                document.importedStatements.mapIndexed { index, payload ->
                    payload.validateAs("imported statement #$index") { toEntity() }
                },
                importedTransactions =
                document.importedTransactions.mapIndexed { index, payload ->
                    payload.validateAs("imported transaction #$index") { toEntity() }
                }
            )

        BackupRestoreLog.info("BackupValidator", "All payloads mapped to entities; running validateIntegrity")
        try {
            restorePlan.validateIntegrity()
        } catch (error: IllegalArgumentException) {
            BackupRestoreLog.error("BackupValidator", "validateIntegrity failed: ${error.message}", error)
            throw error
        }
        BackupRestoreLog.info("BackupValidator", "validateIntegrity passed")
        return restorePlan
    }
}

private inline fun <T, R> T.validateAs(label: String, block: T.() -> R): R = runCatching { block() }
    .getOrElse { error ->
        BackupRestoreLog.error("BackupValidator", "validateAs($label) failed", error)
        throw IllegalArgumentException("Backup contains invalid $label.", error)
    }

private fun BackupRestorePlan.validateIntegrity() {
    BackupRestoreLog.info("BackupValidator", "validateIntegrity step: uniqueness checks")
    incomeEntries.requireUniqueBy("income entry ids") { it.id }
    monthlyDeclarationRecords.requireUniqueBy("monthly declaration period keys") { it.periodKey }
    fxRates.requireUniqueBy("FX rate ids") { it.id }
    fxRates.requireUniqueBy("FX rate date/currency/manual pairs") {
        Triple(it.rateDate, normalizeCurrencyCode(it.currencyCode), it.manualOverride)
    }
    importedStatements.requireUniqueBy("imported statement ids") { it.id }
    importedStatements.requireUniqueBy("imported statement fingerprints") { it.sourceFingerprint }
    importedTransactions.requireUniqueBy("imported transaction ids") { it.id }
    importedTransactions.requireUniqueBy("imported transaction fingerprints") {
        it.transactionFingerprint
    }
    incomeEntries
        .filter { !it.sourceTransactionFingerprint.isNullOrBlank() }
        .requireUniqueBy("income entry sourceTransactionFingerprint values") {
            requireNotNull(it.sourceTransactionFingerprint)
        }

    BackupRestoreLog.info("BackupValidator", "validateIntegrity step: incomeEntries (${incomeEntries.size})")
    validateIncomeEntries()

    BackupRestoreLog.info("BackupValidator", "validateIntegrity step: statusConfig")
    statusConfig?.let { entity ->
        requireLogged(
            entity.defaultTaxRatePercent.signum() >= 0,
            { "defaultTaxRatePercent=${entity.defaultTaxRatePercent}" }
        ) {
            "Backup status config must have non-negative defaultTaxRatePercent."
        }
    }

    BackupRestoreLog.info("BackupValidator", "validateIntegrity step: reminders")
    validateReminders()

    BackupRestoreLog.info("BackupValidator", "validateIntegrity step: fxRates (${fxRates.size})")
    validateFxRates()

    BackupRestoreLog.info(
        "BackupValidator",
        "validateIntegrity step: monthlyRecords (${monthlyDeclarationRecords.size}, " +
            "statuses=${monthlyDeclarationRecords.groupingBy { it.workflowStatus }.eachCount()}, " +
            "zeroPrepared=${monthlyDeclarationRecords.count { it.zeroDeclarationPrepared }})"
    )
    validateMonthlyRecords()

    BackupRestoreLog.info(
        "BackupValidator",
        "validateIntegrity step: linkage (tx=${importedTransactions.size}, income=${incomeEntries.size})"
    )
    validateTransactionLinkage()
}

private fun BackupRestorePlan.validateIncomeEntries() {
    incomeEntries.forEach { entity ->
        requireLogged(
            entity.originalAmount.signum() > 0,
            {
                "id=${entity.id}, incomeDate=${entity.incomeDate}, " +
                    "originalAmount=${entity.originalAmount}, currency=${entity.originalCurrency}"
            }
        ) {
            "Backup income entry '${entity.id}' must have positive originalAmount."
        }
        requireLogged(
            entity.gelEquivalent == null || entity.gelEquivalent.signum() >= 0,
            { "id=${entity.id}, incomeDate=${entity.incomeDate}, gelEquivalent=${entity.gelEquivalent}" }
        ) {
            "Backup income entry '${entity.id}' has a negative GEL amount."
        }
        requireLogged(
            isIsoLikeCurrencyCode(entity.originalCurrency),
            { "id=${entity.id}, originalCurrency=${entity.originalCurrency}" }
        ) {
            "Backup income entry '${entity.id}' must have valid originalCurrency."
        }
    }
}

private fun BackupRestorePlan.validateFxRates() {
    fxRates.forEach { entity ->
        requireLogged(
            isIsoLikeCurrencyCode(entity.currencyCode),
            { "id=${entity.id}, rateDate=${entity.rateDate}, currencyCode=${entity.currencyCode}" }
        ) {
            "Backup FX rate '${entity.id}' must have valid currencyCode."
        }
        requireLogged(
            entity.units > 0,
            {
                "id=${entity.id}, rateDate=${entity.rateDate}, " +
                    "currency=${entity.currencyCode}, units=${entity.units}"
            }
        ) {
            "Backup FX rate '${entity.id}' must have positive units."
        }
        requireLogged(
            entity.rateToGel.signum() > 0,
            {
                "id=${entity.id}, rateDate=${entity.rateDate}, " +
                    "currency=${entity.currencyCode}, rateToGel=${entity.rateToGel}"
            }
        ) {
            "Backup FX rate '${entity.id}' must have positive rateToGel."
        }
    }
}

private fun BackupRestorePlan.validateTransactionLinkage() {
    val importedStatementIds = importedStatements.map(ImportedStatementEntity::id).toSet()
    val incomeEntriesBySourceFingerprint =
        incomeEntries
            .filter { !it.sourceTransactionFingerprint.isNullOrBlank() }
            .associateBy { requireNotNull(it.sourceTransactionFingerprint) }
    val importedTransactionsByFingerprint = importedTransactions.associateBy(
        ImportedTransactionEntity::transactionFingerprint
    )
    importedTransactions.forEach { entity ->
        requireLogged(
            entity.statementId in importedStatementIds,
            { "txId=${entity.id}, statementId=${entity.statementId}, fp=${entity.transactionFingerprint}" }
        ) {
            "Backup imported transaction '${entity.transactionFingerprint}' references missing " +
                "statementId ${entity.statementId}."
        }
        if (entity.finalInclusion == DeclarationInclusion.INCLUDED) {
            requireLogged(
                entity.incomeDate != null,
                { "txId=${entity.id}, statementId=${entity.statementId}, fp=${entity.transactionFingerprint}" }
            ) {
                "Backup imported transaction '${entity.transactionFingerprint}' is included but has no incomeDate."
            }
            val linkedIncomeEntry = incomeEntriesBySourceFingerprint[entity.transactionFingerprint]
            requireLogged(
                linkedIncomeEntry != null,
                {
                    "txId=${entity.id}, statementId=${entity.statementId}, " +
                        "incomeDate=${entity.incomeDate}, fp=${entity.transactionFingerprint}"
                }
            ) {
                "Backup imported transaction '${entity.transactionFingerprint}' is included but has " +
                    "no linked income entry."
            }
            val checkedIncomeEntry = requireNotNull(linkedIncomeEntry)
            requireLogged(
                checkedIncomeEntry.originalAmount.signum() > 0,
                {
                    "txId=${entity.id}, incomeId=${checkedIncomeEntry.id}, " +
                        "originalAmount=${checkedIncomeEntry.originalAmount}"
                }
            ) {
                "Backup imported transaction '${entity.transactionFingerprint}' has linked income entry " +
                    "without positive originalAmount."
            }
        }
    }

    incomeEntries.forEach { entity ->
        val hasSourceStatementId = entity.sourceStatementId != null
        val hasSourceTransactionFingerprint = !entity.sourceTransactionFingerprint.isNullOrBlank()
        if (hasSourceStatementId || hasSourceTransactionFingerprint) {
            requireLogged(
                hasSourceStatementId && hasSourceTransactionFingerprint,
                {
                    "incomeId=${entity.id}, hasStmtId=$hasSourceStatementId, " +
                        "hasTxFp=$hasSourceTransactionFingerprint"
                }
            ) {
                "Backup income entry '${entity.id}' must provide both sourceStatementId and " +
                    "sourceTransactionFingerprint."
            }
            val sourceStatementId = requireNotNull(entity.sourceStatementId)
            val sourceTransactionFingerprint = requireNotNull(entity.sourceTransactionFingerprint)
            requireLogged(
                sourceStatementId in importedStatementIds,
                { "incomeId=${entity.id}, sourceStatementId=$sourceStatementId" }
            ) {
                "Backup income entry '${entity.id}' references missing sourceStatementId $sourceStatementId."
            }
            val importedTransaction = importedTransactionsByFingerprint[sourceTransactionFingerprint]
            requireLogged(
                importedTransaction != null,
                { "incomeId=${entity.id}, sourceTxFp=$sourceTransactionFingerprint" }
            ) {
                "Backup income entry '${entity.id}' references missing sourceTransactionFingerprint " +
                    "'$sourceTransactionFingerprint'."
            }
            val checkedTransaction = requireNotNull(importedTransaction)
            requireLogged(
                checkedTransaction.finalInclusion == entity.declarationInclusion,
                {
                    "incomeId=${entity.id}, entryInclusion=${entity.declarationInclusion}, " +
                        "txInclusion=${checkedTransaction.finalInclusion}"
                }
            ) {
                "Backup income entry '${entity.id}' has a conflicting transaction inclusion."
            }
            requireLogged(
                checkedTransaction.statementId == sourceStatementId,
                {
                    "incomeId=${entity.id}, entryStmtId=$sourceStatementId, " +
                        "txStmtId=${checkedTransaction.statementId}"
                }
            ) {
                "Backup income entry '${entity.id}' has conflicting sourceStatementId/" +
                    "sourceTransactionFingerprint linkage."
            }
        }
    }
}

private fun BackupRestorePlan.validateReminders() {
    reminderConfig?.let { entity ->
        requireLogged(
            entity.declarationReminderDays.all { it in 1..15 },
            { "declarationReminderDays=${entity.declarationReminderDays}" }
        ) {
            "Backup reminder config has invalid declaration reminder days."
        }
        requireLogged(
            entity.paymentReminderDays.all { it in 1..15 },
            { "paymentReminderDays=${entity.paymentReminderDays}" }
        ) {
            "Backup reminder config has invalid payment reminder days."
        }
    }
}

private fun BackupRestorePlan.validateMonthlyRecords() {
    monthlyDeclarationRecords.forEach { entity ->
        requireLogged(
            entity.workflowStatus != MonthlyWorkflowStatus.OVERDUE.dbCode,
            {
                "periodKey=${entity.periodKey}, workflowStatus=${entity.workflowStatus}, " +
                    "zeroPrepared=${entity.zeroDeclarationPrepared}"
            }
        ) {
            "Backup monthly declaration record '${entity.periodKey}' cannot persist an overdue status."
        }
        requireLogged(
            entity.paymentAmountGel == null || entity.paymentAmountGel.signum() > 0,
            {
                "periodKey=${entity.periodKey}, workflowStatus=${entity.workflowStatus}, " +
                    "zeroPrepared=${entity.zeroDeclarationPrepared}, " +
                    "paymentAmountGel=${entity.paymentAmountGel}, " +
                    "hasFiledDate=${entity.declarationFiledDate != null}, " +
                    "hasSentDate=${entity.paymentSentDate != null}, " +
                    "hasCreditedDate=${entity.paymentCreditedDate != null}"
            }
        ) {
            "Backup monthly declaration record '${entity.periodKey}' must have positive paymentAmountGel."
        }
        val expectedPeriodKey = YearMonth.of(entity.year, entity.month).toString()
        requireLogged(
            entity.periodKey == expectedPeriodKey,
            {
                "periodKey=${entity.periodKey}, year=${entity.year}, month=${entity.month}, " +
                    "expected=$expectedPeriodKey"
            }
        ) {
            "Backup monthly declaration record '${entity.periodKey}' does not match " +
                "year=${entity.year}, month=${entity.month}."
        }
    }
}

private inline fun requireLogged(condition: Boolean, crossinline logDetails: () -> String, lazyMessage: () -> String) {
    if (!condition) {
        val message = lazyMessage()
        BackupRestoreLog.error("BackupValidator", "$message | ${logDetails()}")
        throw IllegalArgumentException(message)
    }
}

private inline fun <T, K> List<T>.requireUniqueBy(label: String, keySelector: (T) -> K) {
    val uniqueCount = map(keySelector).toSet().size
    if (uniqueCount != size) {
        BackupRestoreLog.error(
            "BackupValidator",
            "Duplicate check failed for $label (total=$size, unique=$uniqueCount)"
        )
    }
    require(uniqueCount == size) { "Backup contains duplicate $label." }
}
