package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreview
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmStatementImportWorkflowResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.LoadImportPreviewResult
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import com.queukat.sbsgeorgia.domain.service.TaxPaymentDetection
import com.queukat.sbsgeorgia.domain.service.TbcStatementParser
import java.math.BigDecimal
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
        val existingImport = statementImportRepository.getStatementImportInfo(document.sourceFingerprint)

        var firstParsingFailure: Throwable? = null
        val preview = statementTextExtractor.extractTextCandidates(document.bytes)
            .mapNotNull { extractedText ->
                runCatching {
                    tbcStatementParser.parse(
                        sourceFileName = document.fileName,
                        sourceFingerprint = document.sourceFingerprint,
                        extractedText = extractedText,
                    )
                }.getOrElse { error ->
                    if (firstParsingFailure == null) {
                        firstParsingFailure = error
                    }
                    null
                }
            }
            .maxByOrNull(::previewQualityScore)
            ?: throw (firstParsingFailure ?: IllegalStateException("Unable to parse the selected TBC statement PDF."))
        val previewDuplicateFingerprints = preview.rows
            .groupingBy(ImportedStatementPreviewRow::transactionFingerprint)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val seenFingerprints = mutableSetOf<String>()
        val duplicateAwareRows = preview.rows.map { row ->
            val alreadyImported = statementImportRepository.hasTransactionFingerprint(row.transactionFingerprint)
            val duplicateInsidePreview = row.transactionFingerprint in previewDuplicateFingerprints &&
                !seenFingerprints.add(row.transactionFingerprint)
            row.copy(duplicate = alreadyImported || duplicateInsidePreview)
        }

        return LoadImportPreviewResult(
            preview = preview.copy(rows = duplicateAwareRows),
            existingImport = existingImport,
            message = if (preview.skippedLineCount > 0) {
                "${preview.skippedLineCount} statement lines could not be parsed and were skipped."
            } else {
                null
            },
        )
    }

    private fun previewQualityScore(preview: ImportedStatementPreview): Int {
        val uniqueFingerprintCount = preview.rows
            .map(ImportedStatementPreviewRow::transactionFingerprint)
            .distinct()
            .size
        val duplicateFingerprintCount = preview.rows.size - uniqueFingerprintCount
        val embeddedDateArtifactCount = preview.rows.count { row ->
            row.description.hasEmbeddedTransactionDate() || row.additionalInformation.hasEmbeddedTransactionDate()
        }
        val incomingCount = preview.rows.count { row ->
            row.paidIn?.amount?.signum() == 1
        }
        val balanceCount = preview.rows.count { row ->
            row.balance?.amount?.signum() != null
        }
        return uniqueFingerprintCount * 10_000 +
            incomingCount * 150 +
            balanceCount * 10 -
            duplicateFingerprintCount * 5_000 -
            embeddedDateArtifactCount * 2_000 -
            preview.skippedLineCount * 250
    }
}

private fun String?.hasEmbeddedTransactionDate(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return false
    return transactionDateRegex.containsMatchIn(value)
}

private val transactionDateRegex = Regex("\\b\\d{2}/\\d{2}/\\d{4}\\b")

class ConfirmStatementImportUseCase @Inject constructor(
    private val statementImportRepository: StatementImportRepository,
    private val resolveFxForMonthsUseCase: ResolveFxForMonthsUseCase,
    private val applyImportedTaxPaymentsUseCase: ApplyImportedTaxPaymentsUseCase,
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
        val taxPaymentResult = applyImportedTaxPaymentsUseCase(sanitizedRows)

        return ConfirmStatementImportWorkflowResult(
            importResult = importResult,
            autoResolvedFxEntryCount = fxResult.resolvedEntryCount,
            remainingUnresolvedFxEntryCount = fxResult.unresolvedEntryCount,
            appliedTaxPaymentCount = taxPaymentResult.appliedCount,
            skippedTaxPaymentCount = taxPaymentResult.skippedCount,
        )
    }
}

data class ApplyImportedTaxPaymentsResult(
    val appliedCount: Int,
    val skippedCount: Int,
)

class ApplyImportedTaxPaymentsUseCase @Inject constructor(
    private val monthlyDeclarationRepository: MonthlyDeclarationRepository,
) {
    suspend operator fun invoke(rows: List<ApprovedImportedStatementRow>): ApplyImportedTaxPaymentsResult {
        val groupedPayments = rows
            .asSequence()
            .filterNot(ApprovedImportedStatementRow::duplicate)
            .filter { it.finalInclusion != DeclarationInclusion.INCLUDED }
            .filter { row ->
                TaxPaymentDetection.isLikelyTaxPayment(
                    description = row.description,
                    additionalInformation = row.additionalInformation,
                    paidOut = row.paidOut,
                    paidIn = row.paidIn,
                )
            }
            .mapNotNull { row ->
                val paymentAmount = TaxPaymentDetection.resolveOutgoingAmount(row.paidOut, row.amount) ?: return@mapNotNull null
                YearMonth.from(row.incomeDate).minusMonths(1) to (row.incomeDate to paymentAmount)
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

        if (groupedPayments.isEmpty()) {
            return ApplyImportedTaxPaymentsResult(
                appliedCount = 0,
                skippedCount = 0,
            )
        }

        var appliedCount = 0
        var skippedCount = 0
        groupedPayments.toSortedMap().forEach { (yearMonth, payments) ->
            val paymentDate = payments.maxOf { it.first }
            val paymentAmount = payments.fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }
            if (paymentAmount.signum() != 1) {
                skippedCount += payments.size
                return@forEach
            }

            val existing = monthlyDeclarationRepository.observeByMonth(yearMonth).first()
            monthlyDeclarationRepository.upsert(
                com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord(
                    yearMonth = yearMonth,
                    workflowStatus = com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = existing?.zeroDeclarationPrepared ?: false,
                    declarationFiledDate = existing?.declarationFiledDate ?: paymentDate,
                    paymentSentDate = paymentDate,
                    paymentCreditedDate = paymentDate,
                    paymentAmountGel = paymentAmount,
                    notes = existing?.notes.orEmpty(),
                ),
            )
            appliedCount += payments.size
        }

        return ApplyImportedTaxPaymentsResult(
            appliedCount = appliedCount,
            skippedCount = skippedCount,
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
