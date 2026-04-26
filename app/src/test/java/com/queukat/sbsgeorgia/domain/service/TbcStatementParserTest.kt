package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.service.tbc.TbcStatementParser
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TbcStatementParserTest {
    private val parser = TbcStatementParser()

    @Test
    fun parsesTbcFixtureAndExtractsRows() {
        val preview = parser.parse(
            sourceFileName = "tbc-sample.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_extracted.txt"),
        )

        assertEquals(4, preview.rows.size)
        assertEquals("tbc-sample.pdf", preview.sourceFileName)
        assertEquals("USD", preview.rows.first().suggestedCurrency)
        assertEquals("125.50", preview.rows.first().suggestedAmount.toPlainString())
    }

    @Test
    fun appliesTaxableAndNonTaxableHeuristics() {
        val preview = parser.parse(
            sourceFileName = "tbc-sample.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_extracted.txt"),
        )

        val softwareServices = preview.rows.first { it.description == "FOR SOFTWARE SERVICES" }
        val internalTransfer = preview.rows.first { it.description == "INTERNAL TRANSFER" }
        val genericInbound = preview.rows.first { it.description == "CLIENT PAYMENT" }

        assertEquals(DeclarationInclusion.INCLUDED, softwareServices.suggestedInclusion)
        assertEquals("Software services", softwareServices.suggestedSourceCategory)
        assertEquals(DeclarationInclusion.EXCLUDED, internalTransfer.suggestedInclusion)
        assertEquals("Own account transfer", internalTransfer.suggestedSourceCategory)
        assertEquals(DeclarationInclusion.REVIEW_REQUIRED, genericInbound.suggestedInclusion)
        assertTrue(genericInbound.transactionFingerprint.isNotBlank())
    }

    @Test
    fun parsesAcrossMultiplePagesWithRepeatedHeaders() {
        val preview = parser.parse(
            sourceFileName = "tbc-multipage.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_multipage_extracted.txt"),
        )

        assertEquals(4, preview.rows.size)
        val commission = preview.rows.first { it.description == "BANK COMMISSION" }
        assertEquals(DeclarationInclusion.EXCLUDED, commission.suggestedInclusion)
        assertEquals("Bank fee", commission.suggestedSourceCategory)
        assertEquals("Own account transfer", preview.rows.last().suggestedSourceCategory)
    }

    @Test
    fun mergesWrappedTransactionLinesIntoSinglePreviewRows() {
        val preview = parser.parse(
            sourceFileName = "tbc-wrapped.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_wrapped_extracted.txt"),
        )

        assertEquals(2, preview.rows.size)
        val wrappedSoftwareRow = preview.rows.first()
        val wrappedClientPayment = preview.rows.last()

        assertEquals("175.00", wrappedSoftwareRow.suggestedAmount.toPlainString())
        assertEquals(DeclarationInclusion.INCLUDED, wrappedSoftwareRow.suggestedInclusion)
        assertTrue(wrappedSoftwareRow.additionalInformation.orEmpty().contains("Invoice 003"))
        assertEquals(DeclarationInclusion.REVIEW_REQUIRED, wrappedClientPayment.suggestedInclusion)
        assertTrue(wrappedClientPayment.additionalInformation.orEmpty().contains("mobile release"))
    }

    @Test
    fun handlesWhitespaceVariantsWithoutLosingSuggestionQuality() {
        val preview = parser.parse(
            sourceFileName = "tbc-whitespace.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_whitespace_extracted.txt"),
        )

        assertEquals(4, preview.rows.size)
        assertEquals(
            DeclarationInclusion.INCLUDED,
            preview.rows.first { it.description.equals("for software services", ignoreCase = true) }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.EXCLUDED,
            preview.rows.first { it.description.equals("bank fee", ignoreCase = true) }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.EXCLUDED,
            preview.rows.first { it.description.equals("Own Account Transfer", ignoreCase = true) }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.REVIEW_REQUIRED,
            preview.rows.first { it.description.equals("Client Payment", ignoreCase = true) }.suggestedInclusion,
        )
    }

    @Test
    fun parsesGeorgianHeadersAndKeepsSafeSuggestionQuality() {
        val preview = parser.parse(
            sourceFileName = "tbc-georgian.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_georgian_extracted.txt"),
        )

        assertEquals(4, preview.rows.size)
        assertEquals("USD", preview.rows.first().suggestedCurrency)
        assertEquals(
            DeclarationInclusion.INCLUDED,
            preview.rows.first { it.description == "პროგრამული მომსახურება" }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.EXCLUDED,
            preview.rows.first { it.description == "საბანკო საკომისიო" }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.EXCLUDED,
            preview.rows.first { it.description == "საკუთარ ანგარიშებს შორის გადარიცხვა" }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.REVIEW_REQUIRED,
            preview.rows.first { it.description == "კლიენტის გადახდა" }.suggestedInclusion,
        )
    }

    @Test
    fun parsesBilingualHeaderVariant() {
        val preview = parser.parse(
            sourceFileName = "tbc-bilingual.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_bilingual_headers_extracted.txt"),
        )

        assertEquals(2, preview.rows.size)
        assertEquals(
            DeclarationInclusion.INCLUDED,
            preview.rows.first { it.description == "FOR SOFTWARE SERVICES" }.suggestedInclusion,
        )
        assertEquals(
            DeclarationInclusion.EXCLUDED,
            preview.rows.first { it.description == "INTERNAL TRANSFER" }.suggestedInclusion,
        )
    }

    @Test
    fun parsesRealCollapsedBilingualStatementExport() {
        val preview = parser.parse(
            sourceFileName = "statement-818670212_260402_120010.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = fixtureText("tbc_statement_v1_collapsed_bilingual_extracted.txt"),
        )

        assertEquals(30, preview.rows.size)
        assertEquals("USD", preview.rows.first().suggestedCurrency)
        assertEquals(0, preview.skippedLineCount)

        val firstTransfer = preview.rows.first()
        assertEquals("Transfer between your accounts", firstTransfer.description)
        assertTrue(firstTransfer.additionalInformation.orEmpty().contains("Iaroslav Rychenkov"))
        assertEquals("400.00", firstTransfer.suggestedAmount.toPlainString())
        assertEquals(DeclarationInclusion.EXCLUDED, firstTransfer.suggestedInclusion)

        val softwareIncome = preview.rows.first {
            it.incomeDate.toString() == "2026-01-12" && it.description.equals("for software services", ignoreCase = true)
        }
        assertTrue(softwareIncome.additionalInformation.orEmpty().contains("WAVEACCESS USA"))
        assertEquals("3031.00", softwareIncome.suggestedAmount.toPlainString())
        assertEquals(DeclarationInclusion.INCLUDED, softwareIncome.suggestedInclusion)

        val januaryFee = preview.rows.first {
            it.incomeDate.toString() == "2026-01-28" && it.description.contains("მომსახურების საკომისიო")
        }
        assertTrue(januaryFee.additionalInformation.orEmpty().contains("დებიტორები -"))
        assertEquals("0.74", januaryFee.suggestedAmount.toPlainString())
        assertEquals(DeclarationInclusion.EXCLUDED, januaryFee.suggestedInclusion)
        assertEquals("Bank fee", januaryFee.suggestedSourceCategory)
    }

    @Test
    fun recognizesTreasuryTaxPaymentFromRealGeorgianStatementVariant() {
        val preview = parser.parse(
            sourceFileName = "statement-tax-payment.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = """
                ამონაწერი ანგარიშიდან:
                GE94TB7209445168200001 01/04/2026- 11/04/2026 11/04/2026 17:18:52
                Account Statement:
                ანგარიშის მფლობელი: იაროსლავ რიჩენკოვ
                Account Holder: Iaroslav Rychenkov
                საწყისი ნაშთი / Opening Balance 756.59GEL
                თარიღი
                Date
                დანიშნულება
                Description
                დამატებითი ინფორმაცია
                Additional Information
                გასული
                თანხა
                Paid Out
                შემოსული
                თანხა
                Paid In
                ბალანსი
                Balance
                02/04/2026გადასახადების ერთიანი კოდი ხაზინის ერთიანი ანგარიში. საგადასახადო ინსპექცია
                (გადასახადები), TRESGE22, 101001000
                81.60 674.99
            """.trimIndent(),
        )

        assertEquals(1, preview.rows.size)
        val row = preview.rows.single()
        assertEquals("81.60", row.suggestedAmount.toPlainString())
        assertEquals(DeclarationInclusion.EXCLUDED, row.suggestedInclusion)
        assertEquals("Tax payment", row.suggestedSourceCategory)
    }

    @Test
    fun trimsInlinePageHeaderArtifactsAfterBalance() {
        val preview = parser.parse(
            sourceFileName = "statement-inline-artifacts.pdf",
            sourceFingerprint = "fixture-fingerprint",
            extractedText = """
                ამონაწერი ანგარიშიდან:
                Account Statement:
                საწყისი ნაშთი / Opening Balance 1199.12GEL
                01/01/2026POS wallet - Euro Brand LTD, 30.00 GEL, Dec 31 2025 5:01PM, საყიდლები, MCC: 5611, MC, 515881******3677 30.00 1169.12 1 - 77 Account Statement: GE94TB7209445168200001 01/01/2026- 31/01/2026 01/02/2026 00:00:00 Account Holder: Iaroslav Rychenkov
                01/01/2026POS wallet - TSERTI 4, 11.00 GEL, Dec 31 2025 5:06PM, სასურსათო მაღაზიები, MCC: 5499, MC, 515881******3677 11.00 1158.12
            """.trimIndent(),
        )

        assertEquals(2, preview.rows.size)
        assertEquals(0, preview.skippedLineCount)
        assertEquals("30.00", preview.rows.first().suggestedAmount.toPlainString())
        assertEquals("11.00", preview.rows.last().suggestedAmount.toPlainString())
    }

    private fun fixtureText(fileName: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$fileName"))
            .readText()
}
