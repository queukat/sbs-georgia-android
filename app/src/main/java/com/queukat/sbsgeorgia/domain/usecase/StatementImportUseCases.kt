package com.queukat.sbsgeorgia.domain.usecase
import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmStatementImportWorkflowResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.LoadImportPreviewResult
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import com.queukat.sbsgeorgia.domain.service.TbcStatementParser
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LoadStatementImportPreviewUseCase @Inject constructor(
    private val statementDocumentReader: StatementDocumentReader,
    private val statementTextExtractor: StatementTextExtractor,
    private val tbcStatementParser: TbcStatementParser,
    private val statementImportRepository: StatementImportRepository,
) {
    suspend operator fun invoke(uriString: String): LoadImportPreviewResult {
        val document = statementDocumentReader.read(uriString)
        if (statementImportRepository.hasStatementFingerprint(document.sourceFingerprint)) {
            return LoadImportPreviewResult(
                alreadyImported = true,
                message = "This PDF has already been imported. The source fingerprint matches an existing statement.",
            )
        }

        val extractedText = statementTextExtractor.extractText(document.bytes)
        val preview = tbcStatementParser.parse(
            sourceFileName = document.fileName,
            sourceFingerprint = document.sourceFingerprint,
            extractedText = extractedText,
        )
        val duplicateAwareRows = preview.rows.map { row ->
            row.copy(
                duplicate = statementImportRepository.hasTransactionFingerprint(row.transactionFingerprint),
            )
        }

        return LoadImportPreviewResult(
            preview = preview.copy(rows = duplicateAwareRows),
            message = if (preview.skippedLineCount > 0) {
                "${preview.skippedLineCount} statement lines could not be parsed and were skipped."
            } else {
                null
            },
        )
    }
}

class ConfirmStatementImportUseCase @Inject constructor(
    private val statementImportRepository: StatementImportRepository,
    private val resolveFxForMonthsUseCase: ResolveFxForMonthsUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
    ): ConfirmStatementImportWorkflowResult {
        val sanitizedRows = rows.map { row ->
            if (row.finalInclusion == DeclarationInclusion.INCLUDED) {
                require(row.amount.signum() > 0) {
                    "Included imported income rows must have an amount greater than zero."
                }
                require(row.currency.isNotBlank()) {
                    "Included imported income rows must have a currency."
                }
            }
            row
        }

        val importResult = statementImportRepository.confirmImport(
            sourceFileName = sourceFileName,
            sourceFingerprint = sourceFingerprint,
            rows = sanitizedRows,
            importedAtEpochMillis = clock.millis(),
        )

        val monthsNeedingFxResolution = sanitizedRows
            .asSequence()
            .filter { row ->
                !row.duplicate &&
                    row.finalInclusion == DeclarationInclusion.INCLUDED &&
                    !row.currency.equals("GEL", ignoreCase = true)
            }
            .map { YearMonth.from(it.incomeDate) }
            .toSet()
        val fxResult = resolveFxForMonthsUseCase(monthsNeedingFxResolution)

        return ConfirmStatementImportWorkflowResult(
            importResult = importResult,
            autoResolvedFxEntryCount = fxResult.resolvedEntryCount,
            remainingUnresolvedFxEntryCount = fxResult.unresolvedEntryCount,
        )
    }
}

data class BulkMonthFxResolutionResult(
    val resolvedEntryCount: Int,
    val unresolvedEntryCount: Int,
)

class ResolveFxForMonthsUseCase @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val resolveMonthFxUseCase: ResolveMonthFxUseCase,
) {
    suspend operator fun invoke(yearMonths: Set<YearMonth>): BulkMonthFxResolutionResult {
        if (yearMonths.isEmpty()) {
            return BulkMonthFxResolutionResult(
                resolvedEntryCount = 0,
                unresolvedEntryCount = 0,
            )
        }

        var resolvedCount = 0
        var unresolvedCount = 0
        yearMonths.sorted().forEach { yearMonth ->
            val result = resolveMonthFxUseCase(incomeRepository.observeByMonth(yearMonth).first())
            resolvedCount += result.resolvedEntryCount
            unresolvedCount += result.unresolvedEntryCount
        }

        return BulkMonthFxResolutionResult(
            resolvedEntryCount = resolvedCount,
            unresolvedEntryCount = unresolvedCount,
        )
    }
}
