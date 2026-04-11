package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
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
