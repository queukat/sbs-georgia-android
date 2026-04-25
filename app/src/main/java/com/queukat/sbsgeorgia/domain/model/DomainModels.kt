package com.queukat.sbsgeorgia.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class BaseCurrencyView(val dbCode: String) {
    GEL("gel"),
    ;

    companion object {
        fun fromPersisted(value: String): BaseCurrencyView = persistedEnumValue(
            rawValue = value,
            dbCode = BaseCurrencyView::dbCode,
        )
    }
}

enum class ThemeMode(val dbCode: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromPersisted(value: String): ThemeMode = persistedEnumValue(
            rawValue = value,
            dbCode = ThemeMode::dbCode,
        )
    }
}

enum class IncomeSourceType(val dbCode: String) {
    MANUAL("manual"),
    IMPORTED_STATEMENT("imported_statement"),
    ;

    companion object {
        fun fromPersisted(value: String): IncomeSourceType = persistedEnumValue(
            rawValue = value,
            dbCode = IncomeSourceType::dbCode,
        )
    }
}

enum class DeclarationInclusion(val dbCode: String) {
    INCLUDED("included"),
    EXCLUDED("excluded"),
    REVIEW_REQUIRED("review_required"),
    ;

    companion object {
        fun fromPersisted(value: String): DeclarationInclusion = persistedEnumValue(
            rawValue = value,
            dbCode = DeclarationInclusion::dbCode,
        )
    }
}

enum class FxRateSource(val dbCode: String) {
    NONE("none"),
    OFFICIAL_NBG_JSON("official_nbg_json"),
    MANUAL_OVERRIDE("manual_override"),
    ;

    companion object {
        fun fromPersisted(value: String): FxRateSource = persistedEnumValue(
            rawValue = value,
            dbCode = FxRateSource::dbCode,
        )
    }
}

enum class MonthlyWorkflowStatus(val dbCode: String) {
    DRAFT("draft"),
    READY_TO_FILE("ready_to_file"),
    FILED("filed"),
    TAX_PAYMENT_PENDING("tax_payment_pending"),
    PAYMENT_SENT("payment_sent"),
    PAYMENT_CREDITED("payment_credited"),
    SETTLED("settled"),
    OVERDUE("overdue"),
    ;

    companion object {
        fun fromPersisted(value: String): MonthlyWorkflowStatus = persistedEnumValue(
            rawValue = value,
            dbCode = MonthlyWorkflowStatus::dbCode,
        )
    }
}

enum class TaxPaymentStatus {
    NOT_STARTED,
    PENDING,
    SENT,
    CREDITED,
    SETTLED,
    OVERDUE,
}

data class TaxpayerProfile(
    val registrationId: String,
    val displayName: String,
    val baseCurrencyView: BaseCurrencyView = BaseCurrencyView.GEL,
    val legalForm: String? = null,
    val registrationDate: LocalDate? = null,
    val legalAddress: String? = null,
    val activityType: String? = null,
)

data class SmallBusinessStatusConfig(
    val effectiveDate: LocalDate,
    val defaultTaxRatePercent: BigDecimal,
    val certificateNumber: String? = null,
    val certificateIssuedDate: LocalDate? = null,
)

data class ReminderConfig(
    val declarationReminderDays: List<Int>,
    val paymentReminderDays: List<Int>,
    val declarationRemindersEnabled: Boolean,
    val paymentRemindersEnabled: Boolean,
    val defaultReminderTime: LocalTime,
    val themeMode: ThemeMode,
)

data class ImportedStatement(
    val id: Long,
    val sourceFileName: String,
    val sourceFingerprint: String,
    val importedAtEpochMillis: Long,
)

data class ImportedTransaction(
    val id: Long,
    val statementId: Long,
    val transactionFingerprint: String,
    val incomeDate: LocalDate?,
    val description: String,
    val additionalInformation: String?,
    val paidOut: BigDecimal?,
    val paidIn: BigDecimal?,
    val balance: BigDecimal?,
    val suggestedInclusion: DeclarationInclusion,
    val finalInclusion: DeclarationInclusion,
)

data class IncomeEntry(
    val id: Long = 0L,
    val sourceType: IncomeSourceType,
    val incomeDate: LocalDate,
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val sourceCategory: String,
    val note: String,
    val declarationInclusion: DeclarationInclusion,
    val gelEquivalent: BigDecimal?,
    val rateSource: FxRateSource,
    val manualFxOverride: Boolean,
    val sourceStatementId: Long? = null,
    val sourceTransactionFingerprint: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

fun IncomeEntry.requiresFxResolution(): Boolean =
    !originalCurrency.equals("GEL", ignoreCase = true) && gelEquivalent == null

data class FxRate(
    val rateDate: LocalDate,
    val currencyCode: String,
    val units: Int,
    val rateToGel: BigDecimal,
    val source: FxRateSource,
    val manualOverride: Boolean,
)

data class MonthlyDeclarationRecord(
    val yearMonth: YearMonth,
    val workflowStatus: MonthlyWorkflowStatus,
    val zeroDeclarationPrepared: Boolean,
    val declarationFiledDate: LocalDate? = null,
    val paymentSentDate: LocalDate? = null,
    val paymentCreditedDate: LocalDate? = null,
    val paymentAmountGel: BigDecimal? = null,
    val notes: String = "",
)

data class FilingWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val dueDate: LocalDate,
)

data class MonthlyCurrencyTotal(
    val currencyCode: String,
    val amount: BigDecimal,
)

data class MonthlyDeclarationPeriod(
    val incomeMonth: YearMonth,
    val filingWindow: FilingWindow,
    val inScope: Boolean,
    val outOfScope: Boolean,
)

data class MonthlyDeclarationSnapshot(
    val period: MonthlyDeclarationPeriod,
    val workflowStatus: MonthlyWorkflowStatus,
    val graph20TotalGel: BigDecimal,
    val graph15CumulativeGel: BigDecimal,
    val originalCurrencyTotals: List<MonthlyCurrencyTotal>,
    val estimatedTaxAmountGel: BigDecimal?,
    val unresolvedFxCount: Int,
    val zeroDeclarationSuggested: Boolean,
    val zeroDeclarationPrepared: Boolean,
    val reviewNeeded: Boolean,
    val setupRequired: Boolean,
    val record: MonthlyDeclarationRecord?,
) {
    val paidTaxAmountGel: BigDecimal?
        get() = record?.paymentAmountGel

    val taxPaymentDifferenceGel: BigDecimal?
        get() {
            val estimatedTax = estimatedTaxAmountGel ?: return null
            val paidTax = paidTaxAmountGel ?: return null
            return paidTax.subtract(estimatedTax).setScale(2, RoundingMode.HALF_UP)
        }

    val taxPaymentMismatch: Boolean
        get() = taxPaymentDifferenceGel?.compareTo(BigDecimal.ZERO)?.let { it != 0 } ?: false

    val taxPaymentUnderpaid: Boolean
        get() = taxPaymentDifferenceGel?.signum() == -1
}

data class DashboardSummary(
    val taxpayerName: String?,
    val registrationId: String?,
    val setupComplete: Boolean,
    val ytdIncomeGel: BigDecimal,
    val unresolvedFxCount: Int,
    val unsettledMonthsCount: Int,
    val paidTaxAmountGel: BigDecimal,
    val paymentMismatchMonthsCount: Int,
    val currentDuePeriod: MonthlyDeclarationSnapshot?,
    val nextReminderDay: Int?,
)

private inline fun <reified T : Enum<T>> persistedEnumValue(
    rawValue: String,
    dbCode: (T) -> String,
): T {
    val normalized = rawValue.trim()
    return enumValues<T>().firstOrNull { value ->
        dbCode(value).equals(normalized, ignoreCase = true) ||
            value.name.equals(normalized, ignoreCase = true)
    } ?: throw IllegalArgumentException("Unknown ${T::class.simpleName} value '$rawValue'")
}
