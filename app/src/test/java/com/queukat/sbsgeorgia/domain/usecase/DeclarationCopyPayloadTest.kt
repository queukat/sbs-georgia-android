package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeclarationCopyPayloadTest {
    @Test
    fun `buildDeclarationCopyBundle keeps canonical English payload labels and dot decimals`() {
        val yearMonth = YearMonth.of(2026, 3)
        val bundle =
            buildDeclarationCopyBundle(
                snapshot = sampleSnapshot(yearMonth),
                registrationId = "123456789",
                yearMonth = yearMonth
            )

        requireNotNull(bundle)
        assertEquals("123.45", bundle.graph20)
        assertEquals("456.78", bundle.graph15)
        assertEquals("1.23", bundle.taxAmount)
        assertEquals("101001000", bundle.treasuryCode)
        assertEquals(
            "123456789 small business tax for March 2026",
            bundle.paymentComment
        )
        assertEquals(
            "Graph 20: 123.45\nGraph 15: 456.78",
            bundle.declarationText
        )
        assertEquals(
            "Treasury code: 101001000\n" +
                "Tax amount: 1.23\n" +
                "Payment comment: 123456789 small business tax for March 2026",
            bundle.paymentText
        )
        assertEquals(
            "${bundle.declarationText}\n${bundle.paymentText}",
            bundle.fullText
        )
    }

    @Test
    fun `payment payload normalizes registration id and does not create a comment from blank data`() {
        val yearMonth = YearMonth.of(2026, 3)

        assertEquals(
            "123456789 small business tax for March 2026",
            buildPaymentComment(" 123456789 ", yearMonth)
        )
        assertEquals("", buildPaymentComment("   ", yearMonth))
        assertEquals("", buildPaymentComment(null, yearMonth))

        val bundle = buildDeclarationCopyBundle(sampleSnapshot(yearMonth), "   ", yearMonth)

        requireNotNull(bundle)
        assertEquals(TREASURY_CODE, bundle.treasuryCode)
        assertEquals("", bundle.paymentComment)
        assertEquals(
            "Treasury code: 101001000\nTax amount: 1.23\nPayment comment: ",
            bundle.paymentText
        )
        assertNull(buildDeclarationCopyBundle(null, "123456789", yearMonth))
    }

    private fun sampleSnapshot(yearMonth: YearMonth): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
        period =
        MonthlyDeclarationPeriod(
            incomeMonth = yearMonth,
            filingWindow =
            FilingWindow(
                start = LocalDate.of(2026, 4, 1),
                endInclusive = LocalDate.of(2026, 4, 15),
                dueDate = LocalDate.of(2026, 4, 15)
            ),
            inScope = true,
            outOfScope = false
        ),
        workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
        graph20TotalGel = BigDecimal("123.45"),
        graph15CumulativeGel = BigDecimal("456.78"),
        originalCurrencyTotals = emptyList(),
        estimatedTaxAmountGel = BigDecimal("1.23"),
        unresolvedFxCount = 0,
        zeroDeclarationSuggested = false,
        zeroDeclarationPrepared = false,
        reviewNeeded = false,
        setupRequired = false,
        record = null
    )
}
