package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.data.export.AppBackupManager
import com.queukat.sbsgeorgia.data.export.BackupRestoreResult
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyCurrencyTotal
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ExportIncomeEntriesCsvUseCase @Inject constructor(
    private val incomeRepository: IncomeRepository,
) {
    suspend operator fun invoke(): String {
        val entries = incomeRepository.observeAll().first()
            .sortedWith(compareBy<IncomeEntry>({ it.incomeDate }, { it.id }))
        return buildString {
            appendLine(
                csvRow(
                    "id",
                    "sourceType",
                    "incomeDate",
                    "originalAmount",
                    "originalCurrency",
                    "sourceCategory",
                    "note",
                    "declarationInclusion",
                    "gelEquivalent",
                    "rateSource",
                    "manualFxOverride",
                    "sourceStatementId",
                    "sourceTransactionFingerprint",
                    "createdAtEpochMillis",
                    "updatedAtEpochMillis",
                ),
            )
            entries.forEach { entry ->
                appendLine(
                    csvRow(
                        entry.id.toString(),
                        entry.sourceType.name,
                        entry.incomeDate.toString(),
                        entry.originalAmount.toPlainString(),
                        entry.originalCurrency,
                        entry.sourceCategory,
                        entry.note,
                        entry.declarationInclusion.name,
                        entry.gelEquivalent?.toPlainString().orEmpty(),
                        entry.rateSource.name,
                        entry.manualFxOverride.toString(),
                        entry.sourceStatementId?.toString().orEmpty(),
                        entry.sourceTransactionFingerprint.orEmpty(),
                        entry.createdAtEpochMillis.toString(),
                        entry.updatedAtEpochMillis.toString(),
                    ),
                )
            }
        }
    }
}

class ExportMonthlySummariesCsvUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val incomeRepository: IncomeRepository,
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
) {
    suspend operator fun invoke(): String {
        val profile = settingsRepository.observeTaxpayerProfile().first()
        val config = settingsRepository.observeStatusConfig().first()
        val entries = incomeRepository.observeAll().first()
        val records = monthlyDeclarationRepository.observeAll().first()
        val snapshots = relevantYears(entries, records, config)
            .flatMap { year -> planner.buildYearSnapshots(year, profile, config, entries, records) }
            .sortedBy { it.period.incomeMonth }

        return buildString {
            appendLine(
                csvRow(
                    "yearMonth",
                    "filingStart",
                    "filingDue",
                    "inScope",
                    "outOfScope",
                    "workflowStatus",
                    "graph20TotalGel",
                    "graph15CumulativeGel",
                    "estimatedTaxAmountGel",
                    "paidTaxAmountGel",
                    "unresolvedFxCount",
                    "zeroDeclarationSuggested",
                    "zeroDeclarationPrepared",
                    "reviewNeeded",
                    "setupRequired",
                    "originalCurrencyTotals",
                ),
            )
            snapshots.forEach { snapshot ->
                appendLine(
                    csvRow(
                        snapshot.period.incomeMonth.toString(),
                        snapshot.period.filingWindow.start.toString(),
                        snapshot.period.filingWindow.dueDate.toString(),
                        snapshot.period.inScope.toString(),
                        snapshot.period.outOfScope.toString(),
                        snapshot.workflowStatus.name,
                        snapshot.graph20TotalGel.toPlainString(),
                        snapshot.graph15CumulativeGel.toPlainString(),
                        snapshot.estimatedTaxAmountGel?.toPlainString().orEmpty(),
                        snapshot.record?.paymentAmountGel?.toPlainString().orEmpty(),
                        snapshot.unresolvedFxCount.toString(),
                        snapshot.zeroDeclarationSuggested.toString(),
                        snapshot.zeroDeclarationPrepared.toString(),
                        snapshot.reviewNeeded.toString(),
                        snapshot.setupRequired.toString(),
                        snapshot.originalCurrencyTotals.toCsvLabel(),
                    ),
                )
            }
        }
    }

    private fun relevantYears(
        entries: List<IncomeEntry>,
        records: List<MonthlyDeclarationRecord>,
        config: SmallBusinessStatusConfig?,
    ): List<Int> {
        val years = buildSet {
            add(YearMonth.now(clock).year)
            config?.effectiveDate?.year?.let(::add)
            entries.mapTo(this) { it.incomeDate.year }
            records.mapTo(this) { it.yearMonth.year }
        }
        return years.sorted()
    }
}

class ExportBackupJsonUseCase @Inject constructor(
    private val appBackupManager: AppBackupManager,
) {
    suspend operator fun invoke(): String = appBackupManager.exportJson()
}

class ImportBackupJsonUseCase @Inject constructor(
    private val appBackupManager: AppBackupManager,
) {
    suspend operator fun invoke(content: String): BackupRestoreResult = appBackupManager.importJson(content)
}

internal fun chartMonthlyIncomePoints(snapshots: List<MonthlyDeclarationSnapshot>): List<ChartPoint> =
    snapshots.sortedBy { it.period.incomeMonth }
        .map { snapshot ->
            ChartPoint(
                label = snapshot.period.incomeMonth.atDay(1)
                    .format(DateTimeFormatter.ofPattern("LLL", Locale.getDefault()))
                    .uppercase(Locale.getDefault()),
                value = snapshot.graph20TotalGel,
            )
        }

internal fun chartCumulativePoints(snapshots: List<MonthlyDeclarationSnapshot>): List<ChartPoint> =
    snapshots.sortedBy { it.period.incomeMonth }
        .map { snapshot ->
            ChartPoint(
                label = snapshot.period.incomeMonth.atDay(1)
                    .format(DateTimeFormatter.ofPattern("LLL", Locale.getDefault()))
                    .uppercase(Locale.getDefault()),
                value = snapshot.graph15CumulativeGel,
            )
        }

data class ChartPoint(
    val label: String,
    val value: java.math.BigDecimal,
)

private fun csvRow(vararg values: String): String = values.joinToString(",") { value ->
    val escaped = value.replace("\"", "\"\"")
    "\"$escaped\""
}

private fun List<MonthlyCurrencyTotal>.toCsvLabel(): String =
    joinToString(" | ") { total -> "${total.currencyCode} ${total.amount.toPlainString()}" }
