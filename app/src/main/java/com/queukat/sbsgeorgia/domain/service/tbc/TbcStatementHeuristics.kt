package com.queukat.sbsgeorgia.domain.service.tbc

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.service.TaxPaymentDetection
import java.math.BigDecimal
import java.time.LocalDate

internal fun buildPreviewRow(
    incomeDate: LocalDate,
    description: String,
    additionalInformation: String?,
    paidOut: StatementMoney?,
    paidIn: StatementMoney?,
    balance: StatementMoney?,
    fallbackAmount: BigDecimal?,
    fallbackCurrency: String?
): ImportedStatementPreviewRow {
    val suggestionText =
        listOf(description, additionalInformation.orEmpty())
            .joinToString(" ")
            .lowercase()
    val normalizedOutgoing = paidOut?.takeIf { it.amount > BigDecimal.ZERO }
    val normalizedIncoming = paidIn?.takeIf { it.amount > BigDecimal.ZERO }
    val hasNonTaxableHint = nonTaxableHints.any { it in suggestionText }
    val hasTaxableHint = taxableHints.any { it in suggestionText }
    val hasBankFeeHint = bankFeeHints.any { it in suggestionText }
    val isCurrencyConversion = fxConversionHints.any { it in suggestionText }
    val isTaxPayment =
        TaxPaymentDetection.isLikelyTaxPayment(
            description = description,
            additionalInformation = additionalInformation,
            paidOut = normalizedOutgoing,
            paidIn = normalizedIncoming
        )
    val suggestedInclusion = suggestInclusion(
        hasIncoming = normalizedIncoming != null,
        hasOutgoing = normalizedOutgoing != null,
        hasFallbackAmount = fallbackAmount != null,
        hasNonTaxableHint = hasNonTaxableHint,
        hasTaxableHint = hasTaxableHint
    )
    val suggestedAmount =
        when {
            normalizedIncoming != null -> normalizedIncoming.amount
            normalizedOutgoing != null -> normalizedOutgoing.amount
            fallbackAmount != null -> fallbackAmount
            else -> BigDecimal.ZERO
        }
    val suggestedSourceCategory = suggestSourceCategory(
        hasIncoming = normalizedIncoming != null,
        hasOutgoing = normalizedOutgoing != null,
        hasNonTaxableHint = hasNonTaxableHint,
        hasTaxableHint = hasTaxableHint,
        hasBankFeeHint = hasBankFeeHint,
        isCurrencyConversion = isCurrencyConversion,
        isTaxPayment = isTaxPayment
    )

    return ImportedStatementPreviewRow(
        transactionFingerprint =
        fingerprintFor(
            incomeDate = incomeDate,
            description = description,
            additionalInformation = additionalInformation,
            paidOut = paidOut,
            paidIn = paidIn,
            balance = balance
        ),
        incomeDate = incomeDate,
        description = description,
        additionalInformation = additionalInformation,
        paidOut = paidOut,
        paidIn = paidIn,
        balance = balance,
        suggestedInclusion = suggestedInclusion,
        suggestedSourceCategory = suggestedSourceCategory,
        suggestedAmount = suggestedAmount,
        suggestedCurrency =
        paidIn?.currency ?: paidOut?.currency ?: balance?.currency ?: fallbackCurrency
    )
}

private fun suggestInclusion(
    hasIncoming: Boolean,
    hasOutgoing: Boolean,
    hasFallbackAmount: Boolean,
    hasNonTaxableHint: Boolean,
    hasTaxableHint: Boolean
): DeclarationInclusion = when {
    hasIncoming && hasNonTaxableHint -> DeclarationInclusion.EXCLUDED
    hasIncoming && hasTaxableHint -> DeclarationInclusion.INCLUDED
    hasIncoming -> DeclarationInclusion.REVIEW_REQUIRED
    hasOutgoing -> DeclarationInclusion.EXCLUDED
    hasFallbackAmount -> DeclarationInclusion.REVIEW_REQUIRED
    else -> DeclarationInclusion.EXCLUDED
}

private fun suggestSourceCategory(
    hasIncoming: Boolean,
    hasOutgoing: Boolean,
    hasNonTaxableHint: Boolean,
    hasTaxableHint: Boolean,
    hasBankFeeHint: Boolean,
    isCurrencyConversion: Boolean,
    isTaxPayment: Boolean
): String = when {
    isTaxPayment -> SourceCategoryPresets.TAX_PAYMENT
    isCurrencyConversion -> SourceCategoryPresets.CURRENCY_CONVERSION
    hasNonTaxableHint && hasBankFeeHint -> SourceCategoryPresets.BANK_FEE
    hasOutgoing && hasNonTaxableHint -> SourceCategoryPresets.OWN_ACCOUNT_TRANSFER
    hasIncoming && hasTaxableHint -> SourceCategoryPresets.SOFTWARE_SERVICES
    hasIncoming -> SourceCategoryPresets.IMPORTED_STATEMENT_INCOME
    else -> SourceCategoryPresets.IMPORTED_STATEMENT_REVIEW
}

internal val taxableHints =
    listOf(
        "software service",
        "software services",
        "consulting",
        "invoice",
        "development services",
        "service payment",
        "freelance",
        "პროგრამული მომსახურება",
        "პროგრამული სერვისი",
        "სერვისის გადახდა",
        "ინვოისი"
    )

internal val fxConversionHints =
    listOf(
        "currency conversion",
        "foreign exchange",
        "fx conversion",
        "კონვერტ"
    )

internal val nonTaxableHints =
    listOf(
        "internal transfer",
        "transfer between your accounts",
        "own account",
        "fee",
        "commission",
        "charge",
        "bank fee",
        "საკუთარ ანგარიშებს შორის გადარიცხვა",
        "შიდა გადარიცხვა",
        "საკომისიო",
        "კომისია"
    ) + fxConversionHints

internal val incomingDirectionHints =
    taxableHints +
        listOf(
            "client payment",
            "კლიენტის გადახდა"
        )

internal val outgoingDirectionHints =
    nonTaxableHints +
        listOf(
            "pos wallet",
            "card payment",
            "payment by card",
            "cash withdrawal",
            "atm withdrawal",
            "treasury account",
            "101001000",
            "ბარათით გადახდა",
            "ნაღდი ფულის გატანა",
            "ხაზინის ერთიანი ანგარიში"
        )

internal val bankFeeHints =
    listOf(
        "fee",
        "commission",
        "charge",
        "საკომისიო",
        "კომისია"
    )
