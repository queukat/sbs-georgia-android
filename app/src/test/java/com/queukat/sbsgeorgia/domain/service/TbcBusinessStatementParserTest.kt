package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.domain.service.tbc.TbcStatementParser
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TbcBusinessStatementParserTest {
    private val parser = TbcStatementParser()

    @Test
    fun parsesBusinessGelStatementAndIncludesOnlyNetCardPayouts() {
        val preview = parser.parse("business.pdf", "business-fixture", fixture())

        assertEquals(7, preview.rows.size)
        assertEquals(0, preview.skippedLineCount)
        assertTrue(preview.rows.all { it.suggestedCurrency == "GEL" })
        assertEquals(7, preview.rows.map { it.transactionFingerprint }.distinct().size)
        assertEquals(
            BigDecimal("102.00"),
            preview.rows.mapNotNull { it.paidIn?.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        )
        assertEquals(
            BigDecimal("6.00"),
            preview.rows.mapNotNull { it.paidOut?.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        )
        assertEquals(BigDecimal("96.00"), preview.rows.last().balance?.amount)

        val income = preview.rows.filter { it.suggestedInclusion == DeclarationInclusion.INCLUDED }
        assertEquals(listOf("2026-09-20", "2026-10-01"), income.map { it.incomeDate.toString() })
        assertTrue(income.all { it.suggestedSourceCategory == SourceCategoryPresets.CARD_ACQUIRING_PAYOUT })
        // Net 49.00 + commission 1.00 is not silently inflated to gross 50.00.
        assertEquals(listOf(BigDecimal("49.00"), BigDecimal("49.00")), income.map { it.suggestedAmount })
        assertTrue(income.all { it.paidIn?.amount == it.suggestedAmount })
        assertEquals(5, preview.rows.count { it.suggestedInclusion == DeclarationInclusion.EXCLUDED })
    }

    @Test
    fun recognizesWrappedDescriptionAndSpacedBatchFields() {
        val text = fixture()
            .replace("მიღებული თანხის გაცემა", "მიღებული თანხის\nგაცემა")
            .replace(";Card;mid:", "; Card ; mid: ")
        val preview = parser.parse("business.pdf", "wrapped-fixture", text)

        assertEquals(7, preview.rows.size)
        assertEquals(0, preview.skippedLineCount)
        assertEquals(2, preview.rows.count { it.suggestedInclusion == DeclarationInclusion.INCLUDED })
    }

    @Test
    fun payoutDescriptionDoesNotTurnAnOutgoingMovementIntoIncome() {
        val preview = parser.parse(
            "reversal.pdf",
            "outgoing-fixture",
            """
                Opening Balance 49.00 GEL
                20/09/2026მიღებული თანხის გაცემა - ნეტი თანხა 49.00;საკომისიო 1.00;Batch:100001;Card;mid:1000001 49.00 0.00
            """.trimIndent()
        )

        val row = preview.rows.single()
        assertEquals(BigDecimal("49.00"), row.paidOut?.amount)
        assertEquals(DeclarationInclusion.EXCLUDED, row.suggestedInclusion)
    }

    @Test
    fun incompletePayoutSignatureDoesNotOverrideFeeExclusion() {
        val withoutBatch = fixture().replace("Batch:100001;Card;mid:1000001", "Reference:100001")
        val preview = parser.parse("business.pdf", "incomplete-fixture", withoutBatch)

        assertEquals(1, preview.rows.count { it.suggestedInclusion == DeclarationInclusion.INCLUDED })
        assertEquals(DeclarationInclusion.EXCLUDED, preview.rows[4].suggestedInclusion)
    }

    private fun fixture(): String = requireNotNull(
        javaClass.getResource("/fixtures/tbc_statement_business_gel_extracted.txt")
    ).readText()
}
