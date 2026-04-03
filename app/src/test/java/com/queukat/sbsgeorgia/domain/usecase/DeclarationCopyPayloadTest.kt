package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarationCopyPayloadTest {
    @Test
    fun `buildDeclarationCopyBundle keeps dot decimal values and payment comment`() {
        val yearMonth = YearMonth.of(2026, 3)
        val bundle = buildDeclarationCopyBundle(
            snapshot = sampleSnapshot(yearMonth),
            registrationId = "306449082",
            yearMonth = yearMonth,
        )

        requireNotNull(bundle)
        assertEquals("123.45", bundle.graph20)
        assertEquals("456.78", bundle.graph15)
        assertEquals("1.23", bundle.taxAmount)
        assertEquals("101001000", bundle.treasuryCode)
        assertEquals(
            "306449082 small business tax for March 2026",
            bundle.paymentComment,
        )
        assertTrue(bundle.fullText.contains("Graph 20: 123.45"))
        assertTrue(bundle.fullText.contains("Graph 15: 456.78"))
        assertTrue(bundle.fullText.contains("Tax amount: 1.23"))
    }

    private fun sampleSnapshot(yearMonth: YearMonth): MonthlyDeclarationSnapshot =
        MonthlyDeclarationSnapshot(
            period = MonthlyDeclarationPeriod(
                incomeMonth = yearMonth,
                filingWindow = FilingWindow(
                    start = LocalDate.of(2026, 4, 1),
                    endInclusive = LocalDate.of(2026, 4, 15),
                    dueDate = LocalDate.of(2026, 4, 15),
                ),
                inScope = true,
                outOfScope = false,
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
            record = null,
        )
}
