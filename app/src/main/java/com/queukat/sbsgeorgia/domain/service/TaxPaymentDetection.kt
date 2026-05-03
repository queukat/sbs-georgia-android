package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.StatementMoney
import java.math.BigDecimal

object TaxPaymentDetection {
    fun isLikelyTaxPayment(
        description: String,
        additionalInformation: String?,
        paidOut: StatementMoney?,
        paidIn: StatementMoney?
    ): Boolean {
        if (paidOut?.amount?.signum() != 1) return false
        if (paidIn?.amount?.signum() == 1) return false
        return matchesTaxPaymentText(description, additionalInformation)
    }

    fun matchesTaxPaymentText(description: String, additionalInformation: String?): Boolean {
        val normalized =
            listOf(description, additionalInformation.orEmpty())
                .joinToString(" ")
                .lowercase()
        return taxPaymentHints.any { it in normalized }
    }

    fun resolveOutgoingAmount(paidOut: StatementMoney?, fallbackAmount: BigDecimal): BigDecimal? =
        paidOut?.amount?.takeIf { it.signum() == 1 } ?: fallbackAmount.takeIf { it.signum() == 1 }

    private val taxPaymentHints =
        listOf(
            "tax unified code",
            "unified code of taxes",
            "treasury single account",
            "revenue service",
            "tax inspection",
            "gadaxadebis ertiani kodi",
            "ხაზინის ერთიანი ანგარიში",
            "გადასახადების ერთიანი კოდი",
            "საგადასახადო ინსპექცია",
            "საგადასახადო",
            "გადასახადები"
        )
}
