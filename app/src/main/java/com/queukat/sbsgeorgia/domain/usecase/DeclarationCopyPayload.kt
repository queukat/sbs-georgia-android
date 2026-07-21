package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeclarationCopyValue(val field: DeclarationFormField, val value: String)

data class DeclarationCopyBundle(
    val declarationValues: List<DeclarationCopyValue>,
    val taxAmount: String,
    val treasuryCode: String,
    val paymentComment: String,
    val declarationText: String,
    val paymentText: String,
    val fullText: String
)

internal const val TREASURY_CODE = "101001000"

internal object DeclarationCopyPayloadLabels {
    const val TREASURY_CODE = "Treasury code"
    const val TAX_AMOUNT = "Tax amount"
    const val PAYMENT_COMMENT = "Payment comment"
}

fun buildPaymentComment(registrationId: String?, yearMonth: YearMonth): String {
    val normalizedRegistrationId = registrationId?.trim().orEmpty()
    if (normalizedRegistrationId.isBlank()) return ""
    val monthLabel = yearMonth.atDay(1).format(paymentMonthFormatter)
    return "$normalizedRegistrationId small business tax for $monthLabel"
}

fun buildDeclarationCopyBundle(
    snapshot: MonthlyDeclarationSnapshot?,
    registrationId: String?,
    yearMonth: YearMonth,
    formConfig: DeclarationFormConfig = DeclarationFormConfig()
): DeclarationCopyBundle? {
    if (snapshot == null) return null

    val declarationValues =
        buildList {
            if (formConfig.includeCumulativeIncome) {
                add(
                    DeclarationCopyValue(
                        field = DeclarationFormField.CUMULATIVE_INCOME,
                        value = plainDecimal(snapshot.graph15CumulativeGel)
                    )
                )
            }
            if (formConfig.includeMonthlyIncome) {
                add(
                    DeclarationCopyValue(
                        field = formConfig.monthlyIncomeField,
                        value = plainDecimal(snapshot.graph20TotalGel)
                    )
                )
            }
        }.sortedBy { it.field.fieldNumber }
    val taxAmount = plainDecimal(snapshot.estimatedTaxAmountGel ?: BigDecimal.ZERO)
    val paymentComment = buildPaymentComment(registrationId, yearMonth)
    // Payload labels are operational text pasted outside the app; UI copy labels stay localized.
    val declarationText = declarationValues.joinToString("\n") { copyValue ->
        "${copyValue.field.payloadLabel()}: ${copyValue.value}"
    }
    val paymentText =
        buildString {
            appendLine("${DeclarationCopyPayloadLabels.TREASURY_CODE}: $TREASURY_CODE")
            appendLine("${DeclarationCopyPayloadLabels.TAX_AMOUNT}: $taxAmount")
            append("${DeclarationCopyPayloadLabels.PAYMENT_COMMENT}: $paymentComment")
        }
    return DeclarationCopyBundle(
        declarationValues = declarationValues,
        taxAmount = taxAmount,
        treasuryCode = TREASURY_CODE,
        paymentComment = paymentComment,
        declarationText = declarationText,
        paymentText = paymentText,
        fullText = listOf(declarationText, paymentText).filter(String::isNotBlank).joinToString("\n")
    )
}

private fun DeclarationFormField.payloadLabel(): String = when (this) {
    DeclarationFormField.CUMULATIVE_INCOME ->
        "Field (15) - cumulative income since year start"
    DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME ->
        "Field (18) - monthly cash-register income"
    DeclarationFormField.MONTHLY_POS_INCOME ->
        "Field (19) - monthly POS-terminal income"
    DeclarationFormField.MONTHLY_NON_CASH_INCOME ->
        "Field (20) - monthly non-cash income excluding POS"
    DeclarationFormField.MONTHLY_OTHER_INCOME ->
        "Field (21) - monthly other income"
}

private fun plainDecimal(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

private val paymentMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
