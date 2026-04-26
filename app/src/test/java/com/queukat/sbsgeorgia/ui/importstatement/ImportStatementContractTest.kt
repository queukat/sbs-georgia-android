package com.queukat.sbsgeorgia.ui.importstatement

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.isIsoLikeCurrencyCode
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStatementContractTest {
    @Test
    fun invalidForIncludedImportRequiresTransactionDate() {
        val row = validRow().copy(
            incomeDate = null,
            finalInclusion = DeclarationInclusion.INCLUDED,
        )

        assertTrue(row.isInvalidForIncludedImport())
    }

    @Test
    fun invalidForIncludedImportAllowsExcludedRowWithoutDate() {
        val row = validRow().copy(
            incomeDate = null,
            finalInclusion = DeclarationInclusion.EXCLUDED,
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

    private fun validRow(): ImportStatementRowUiState = ImportStatementRowUiState(
        transactionFingerprint = "tx-1",
        incomeDate = LocalDate.of(2026, 3, 15),
        description = "FOR SOFTWARE SERVICES",
        additionalInformation = "Invoice 001",
        paidOutLabel = null,
        paidInLabel = "125.50 USD",
        balanceLabel = "1240.75 USD",
        suggestedInclusion = DeclarationInclusion.INCLUDED,
        finalInclusion = DeclarationInclusion.INCLUDED,
        amount = "125.50",
        currency = "USD",
        sourceCategory = "Software services",
        duplicate = false,
    )
}
