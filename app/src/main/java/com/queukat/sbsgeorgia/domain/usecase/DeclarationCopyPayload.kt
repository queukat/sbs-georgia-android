package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeclarationCopyBundle(
    val graph20: String,
    val graph15: String,
    val taxAmount: String,
    val treasuryCode: String,
    val paymentComment: String,
    val declarationText: String,
    val paymentText: String,
    val fullText: String,
)

internal const val TREASURY_CODE = "101001000"

fun buildPaymentComment(registrationId: String?, yearMonth: YearMonth): String {
    if (registrationId.isNullOrBlank()) return ""
    val monthLabel = yearMonth.atDay(1).format(paymentMonthFormatter)
    return "$registrationId small business tax for $monthLabel"
}

fun buildDeclarationCopyBundle(
    snapshot: MonthlyDeclarationSnapshot?,
    registrationId: String?,
    yearMonth: YearMonth,
): DeclarationCopyBundle? {
    if (snapshot == null) return null

    val graph20 = plainDecimal(snapshot.graph20TotalGel)
    val graph15 = plainDecimal(snapshot.graph15CumulativeGel)
    val taxAmount = plainDecimal(snapshot.estimatedTaxAmountGel ?: BigDecimal.ZERO)
    val paymentComment = buildPaymentComment(registrationId, yearMonth)
    val declarationText = "Graph 20: $graph20\nGraph 15: $graph15"
    val paymentText = buildString {
        appendLine("Treasury code: $TREASURY_CODE")
        appendLine("Tax amount: $taxAmount")
        append("Payment comment: $paymentComment")
    }
    return DeclarationCopyBundle(
        graph20 = graph20,
        graph15 = graph15,
        taxAmount = taxAmount,
        treasuryCode = TREASURY_CODE,
        paymentComment = paymentComment,
        declarationText = declarationText,
        paymentText = paymentText,
        fullText = "$declarationText\n$paymentText",
    )
}

private fun plainDecimal(value: BigDecimal): String =
    value.setScale(2, RoundingMode.HALF_UP).toPlainString()

private val paymentMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
