package com.queukat.sbsgeorgia.ui.importstatement

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStatementContractTest {
    @Test
    fun invalidForIncludedImportRequiresTransactionDate() {
        val row =
            validRow().copy(
                incomeDate = null,
                finalInclusion = DeclarationInclusion.INCLUDED
            )

        assertTrue(row.isInvalidForIncludedImport())
    }

    @Test
    fun invalidForIncludedImportAllowsExcludedRowWithoutDate() {
        val row =
            validRow().copy(
                incomeDate = null,
                finalInclusion = DeclarationInclusion.EXCLUDED
            )

        assertFalse(row.isInvalidForIncludedImport())
    }

    @Test
    fun invalidForIncludedImportAcceptsCompleteIncludedRow() {
        assertFalse(validRow().isInvalidForIncludedImport())
    }

    @Test
    fun invalidForIncludedImportRejectsNonIsoCurrencyCode() {
        val row = validRow().copy(currency = "lari")

        assertTrue(row.isInvalidForIncludedImport())
    }

    @Test
    fun invalidForIncludedImportAcceptsTrimmedLowercaseIsoCurrencyCode() {
        val row = validRow().copy(currency = " usd ")

        assertFalse(row.isInvalidForIncludedImport())
    }

    @Test
    fun currencyCodeValidationNormalizesAndRejectsMalformedValues() {
        assertTrue(isIsoLikeCurrencyCode(" usd "))
        assertFalse(isIsoLikeCurrencyCode("US"))
        assertFalse(isIsoLikeCurrencyCode("???"))
    }

    @Test
    fun reviewSummaryCountsProblemRowsAndFinalDecisions() {
        val rows =
            listOf(
                validRow(),
                validRow().copy(
                    transactionFingerprint = "tx-2",
                    suggestedInclusion = DeclarationInclusion.REVIEW_REQUIRED,
                    finalInclusion = DeclarationInclusion.EXCLUDED
                ),
                validRow().copy(
                    transactionFingerprint = "tx-3",
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    isTaxPaymentCandidate = true
                ),
                validRow().copy(
                    transactionFingerprint = "tx-4",
                    duplicate = true,
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    isTaxPaymentCandidate = true
                )
            )

        assertTrue(rows[1].needsReview())
        assertFalse(rows[2].needsReview())
        assertFalse(rows[3].needsReview())
        assertEquals(1, rows.willImportCount())
        assertEquals(1, rows.needsReviewCount())
        assertEquals(1, rows.pendingReviewDecisionCount())
        assertEquals(2, rows.excludedCount())
        assertEquals(1, rows.duplicateCount())
        assertEquals(1, rows.taxPaymentCandidateCount())
    }

    @Test
    fun excludedTaxPaymentIsVisibleWithoutBlockingImportReview() {
        val row =
            validRow().copy(
                finalInclusion = DeclarationInclusion.EXCLUDED,
                isTaxPaymentCandidate = true
            )

        assertFalse(row.requiresManualReviewDecision())
        assertFalse(row.isPendingManualReviewDecision())
        assertFalse(row.needsReview())
    }

    @Test
    fun everyReviewFilterUsesTheSameRowsAsItsCount() {
        val rows =
            listOf(
                validRow(),
                validRow().copy(
                    transactionFingerprint = "tx-review",
                    suggestedInclusion = DeclarationInclusion.REVIEW_REQUIRED,
                    finalInclusion = DeclarationInclusion.EXCLUDED
                ),
                validRow().copy(
                    transactionFingerprint = "tx-tax",
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    isTaxPaymentCandidate = true,
                    reviewDecisionMade = true
                ),
                validRow().copy(
                    transactionFingerprint = "tx-duplicate-tax",
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    isTaxPaymentCandidate = true,
                    duplicate = true
                )
            )

        ImportStatementFilter.entries.forEach { filter ->
            assertEquals(rows.filterFor(filter).size, rows.countFor(filter))
        }
        assertEquals(
            listOf("tx-tax"),
            rows
                .filterFor(ImportStatementFilter.TAX_PAYMENTS)
                .map(ImportStatementRowUiState::transactionFingerprint)
        )
    }

    @Test
    fun invalidIncludedRowStillNeedsReviewAfterDecision() {
        val row =
            validRow().copy(
                amount = "",
                reviewDecisionMade = true
            )

        assertTrue(row.needsReview())
    }

    private fun validRow(): ImportStatementRowUiState = ImportStatementRowUiState(
        transactionFingerprint = "tx-1",
        incomeDate = LocalDate.of(2026, 3, 15),
        description = "FOR SOFTWARE SERVICES",
        additionalInformation = "Invoice 001",
        paidOut = null,
        paidIn = StatementMoney(BigDecimal("125.50"), "USD"),
        balance = StatementMoney(BigDecimal("1240.75"), "USD"),
        suggestedInclusion = DeclarationInclusion.INCLUDED,
        finalInclusion = DeclarationInclusion.INCLUDED,
        amount = "125.50",
        currency = "USD",
        sourceCategory = "Software services",
        duplicate = false
    )
}
