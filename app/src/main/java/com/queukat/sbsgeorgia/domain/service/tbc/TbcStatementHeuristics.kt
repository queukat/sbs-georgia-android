package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ImportedStatementPreviewRow
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import com.queukat.sbsgeorgia.domain.model.StatementMoney
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
    fallbackCurrency: String?,
): ImportedStatementPreviewRow {
    val suggestionText = listOf(description, additionalInformation.orEmpty())
        .joinToString(" ")
        .lowercase()
    val normalizedOutgoing = paidOut?.takeIf { it.amount > BigDecimal.ZERO }
    val normalizedIncoming = paidIn?.takeIf { it.amount > BigDecimal.ZERO }
    val isTaxPayment = TaxPaymentDetection.isLikelyTaxPayment(
        description = description,
        additionalInformation = additionalInformation,
        paidOut = normalizedOutgoing,
        paidIn = normalizedIncoming,
    )
    val suggestedInclusion = when {
        normalizedIncoming != null && nonTaxableHints.any { it in suggestionText } -> DeclarationInclusion.EXCLUDED
        normalizedIncoming != null && taxableHints.any { it in suggestionText } -> DeclarationInclusion.INCLUDED
        normalizedIncoming != null -> DeclarationInclusion.REVIEW_REQUIRED
        normalizedOutgoing != null -> DeclarationInclusion.EXCLUDED
        fallbackAmount != null -> DeclarationInclusion.REVIEW_REQUIRED
        else -> DeclarationInclusion.EXCLUDED
    }
    val suggestedAmount = when {
        normalizedIncoming != null -> normalizedIncoming.amount
        normalizedOutgoing != null -> normalizedOutgoing.amount
        fallbackAmount != null -> fallbackAmount
        else -> BigDecimal.ZERO
    }
    val suggestedSourceCategory = when {
        isTaxPayment -> SourceCategoryPresets.TAX_PAYMENT
        nonTaxableHints.any { it in suggestionText } && bankFeeHints.any { it in suggestionText } ->
            SourceCategoryPresets.BANK_FEE
        normalizedOutgoing != null && nonTaxableHints.any { it in suggestionText } ->
            SourceCategoryPresets.OWN_ACCOUNT_TRANSFER
        normalizedIncoming != null && taxableHints.any { it in suggestionText } ->
            SourceCategoryPresets.SOFTWARE_SERVICES
        normalizedIncoming != null ->
            SourceCategoryPresets.IMPORTED_STATEMENT_INCOME
        else ->
            SourceCategoryPresets.IMPORTED_STATEMENT_REVIEW
    }

    return ImportedStatementPreviewRow(
        transactionFingerprint = fingerprintFor(
            incomeDate = incomeDate,
            description = description,
            additionalInformation = additionalInformation,
            paidOut = paidOut,
            paidIn = paidIn,
            balance = balance,
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
        suggestedCurrency = paidIn?.currency ?: paidOut?.currency ?: balance?.currency ?: fallbackCurrency,
    )
}

internal val taxableHints = listOf(
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
    "ინვოისი",
)

internal val nonTaxableHints = listOf(
    "internal transfer",
    "own account",
    "fee",
    "commission",
    "charge",
    "bank fee",
    "საკუთარ ანგარიშებს შორის გადარიცხვა",
    "შიდა გადარიცხვა",
    "საკომისიო",
    "კომისია",
)

internal val incomingDirectionHints = taxableHints + listOf(
    "client payment",
    "კლიენტის გადახდა",
)

internal val outgoingDirectionHints = nonTaxableHints + listOf(
    "transfer between your accounts",
    "საკუთარ ანგარიშებს შორის გადარიცხვა",
)

internal val bankFeeHints = listOf(
    "fee",
    "commission",
    "charge",
    "საკომისიო",
    "კომისია",
)
