package com.queukat.sbsgeorgia.data.export

import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigEntity
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigEntity
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileEntity
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.serialization.json.Json

internal data class BackupRestorePlan(
    val taxpayerProfile: TaxpayerProfileEntity?,
    val statusConfig: SmallBusinessStatusConfigEntity?,
    val reminderConfig: ReminderConfigEntity?,
    val incomeEntries: List<IncomeEntryEntity>,
    val monthlyDeclarationRecords: List<MonthlyDeclarationRecordEntity>,
    val fxRates: List<FxRateEntity>,
    val importedStatements: List<ImportedStatementEntity>,
    val importedTransactions: List<ImportedTransactionEntity>,
) {
    fun toRestoreResult(): BackupRestoreResult = BackupRestoreResult(
        reminderConfigImported = reminderConfig != null,
        incomeEntryCount = incomeEntries.size,
        monthlyRecordCount = monthlyDeclarationRecords.size,
        importedStatementCount = importedStatements.size,
        importedTransactionCount = importedTransactions.size,
    )
}

class BackupValidator @Inject constructor(
    private val json: Json,
) {
    internal fun buildRestorePlan(content: String): BackupRestorePlan {
        val document = runCatching { json.decodeFromString<AppBackupDocument>(content) }
            .getOrElse { error -> throw IllegalArgumentException("Backup JSON is invalid.", error) }

        require(document.formatVersion == 1) {
            "Unsupported backup format version ${document.formatVersion}."
        }

        val restorePlan = BackupRestorePlan(
            taxpayerProfile = document.taxpayerProfile?.validateAs("taxpayer profile") { toEntity() },
            statusConfig = document.statusConfig?.validateAs("status config") { toEntity() },
            reminderConfig = document.reminderConfig?.validateAs("reminder config") { toEntity() },
            incomeEntries = document.incomeEntries.mapIndexed { index, payload ->
                payload.validateAs("income entry #$index") { toEntity() }
            },
            monthlyDeclarationRecords = document.monthlyDeclarationRecords.mapIndexed { index, payload ->
                payload.validateAs("monthly declaration record #$index") { toEntity() }
            },
            fxRates = document.fxRates.mapIndexed { index, payload ->
                payload.validateAs("FX rate #$index") { toEntity() }
            },
            importedStatements = document.importedStatements.mapIndexed { index, payload ->
                payload.validateAs("imported statement #$index") { toEntity() }
            },
            importedTransactions = document.importedTransactions.mapIndexed { index, payload ->
                payload.validateAs("imported transaction #$index") { toEntity() }
            },
        )

        restorePlan.validateIntegrity()
        return restorePlan
    }
}

private inline fun <T, R> T.validateAs(
    label: String,
    block: T.() -> R,
): R = runCatching { block() }
    .getOrElse { error -> throw IllegalArgumentException("Backup contains invalid $label.", error) }

private fun BackupRestorePlan.validateIntegrity() {
    incomeEntries.requireUniqueBy("income entry ids") { it.id }
    monthlyDeclarationRecords.requireUniqueBy("monthly declaration period keys") { it.periodKey }
    fxRates.requireUniqueBy("FX rate ids") { it.id }
    fxRates.requireUniqueBy("FX rate date/currency/manual pairs") {
        Triple(it.rateDate, it.currencyCode.uppercase(), it.manualOverride)
    }
    importedStatements.requireUniqueBy("imported statement ids") { it.id }
    importedStatements.requireUniqueBy("imported statement fingerprints") { it.sourceFingerprint }
    importedTransactions.requireUniqueBy("imported transaction ids") { it.id }
    importedTransactions.requireUniqueBy("imported transaction fingerprints") { it.transactionFingerprint }

    monthlyDeclarationRecords.forEach { entity ->
        val expectedPeriodKey = YearMonth.of(entity.year, entity.month).toString()
        require(entity.periodKey == expectedPeriodKey) {
            "Backup monthly declaration record '${entity.periodKey}' does not match year=${entity.year}, month=${entity.month}."
        }
    }

    val importedStatementIds = importedStatements.map(ImportedStatementEntity::id).toSet()
    importedTransactions.forEach { entity ->
        require(entity.statementId in importedStatementIds) {
            "Backup imported transaction '${entity.transactionFingerprint}' references missing statementId ${entity.statementId}."
        }
    }
}

private inline fun <T, K> List<T>.requireUniqueBy(
    label: String,
    keySelector: (T) -> K,
) {
    val uniqueCount = map(keySelector).toSet().size
    require(uniqueCount == size) { "Backup contains duplicate $label." }
}
