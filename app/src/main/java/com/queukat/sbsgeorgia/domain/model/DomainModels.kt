package com.queukat.sbsgeorgia.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class BaseCurrencyView {
    GEL,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class IncomeSourceType {
    MANUAL,
    IMPORTED_STATEMENT,
}

enum class DeclarationInclusion {
    INCLUDED,
    EXCLUDED,
    REVIEW_REQUIRED,
}

enum class FxRateSource {
    NONE,
    OFFICIAL_NBG_JSON,
    MANUAL_OVERRIDE,
}

enum class MonthlyWorkflowStatus {
    DRAFT,
    READY_TO_FILE,
    FILED,
    TAX_PAYMENT_PENDING,
    PAYMENT_SENT,
    PAYMENT_CREDITED,
    SETTLED,
    OVERDUE,
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
