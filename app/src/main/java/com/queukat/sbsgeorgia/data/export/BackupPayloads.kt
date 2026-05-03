package com.queukat.sbsgeorgia.data.export

import kotlinx.serialization.Serializable

@Serializable
data class AppBackupDocument(
    val formatVersion: Int = 1,
    val exportedAtEpochMillis: Long,
    val taxpayerProfile: TaxpayerProfilePayload? = null,
    val statusConfig: SmallBusinessStatusConfigPayload? = null,
    val reminderConfig: ReminderConfigPayload? = null,
    val incomeEntries: List<IncomeEntryPayload> = emptyList(),
    val monthlyDeclarationRecords: List<MonthlyDeclarationRecordPayload> = emptyList(),
    val fxRates: List<FxRatePayload> = emptyList(),
    val importedStatements: List<ImportedStatementPayload> = emptyList(),
    val importedTransactions: List<ImportedTransactionPayload> = emptyList()
)

@Serializable
data class TaxpayerProfilePayload(
    val registrationId: String,
    val displayName: String,
    val baseCurrencyView: String,
    val legalForm: String? = null,
    val registrationDate: String? = null,
    val legalAddress: String? = null,
    val activityType: String? = null
)

@Serializable
data class SmallBusinessStatusConfigPayload(
    val effectiveDate: String,
    val defaultTaxRatePercent: String,
    val certificateNumber: String? = null,
    val certificateIssuedDate: String? = null
)

@Serializable
data class ReminderConfigPayload(
    val declarationReminderDays: List<Int>,
    val paymentReminderDays: List<Int>,
    val declarationRemindersEnabled: Boolean,
    val paymentRemindersEnabled: Boolean,
    val defaultReminderTime: String,
    val themeMode: String
)

@Serializable
data class IncomeEntryPayload(
    val id: Long,
    val sourceType: String,
    val incomeDate: String,
    val originalAmount: String,
    val originalCurrency: String,
    val sourceCategory: String,
    val note: String,
    val declarationInclusion: String,
    val gelEquivalent: String? = null,
    val rateSource: String,
    val manualFxOverride: Boolean,
    val sourceStatementId: Long? = null,
    val sourceTransactionFingerprint: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class MonthlyDeclarationRecordPayload(
    val periodKey: String,
    val year: Int,
    val month: Int,
    val workflowStatus: String,
    val zeroDeclarationPrepared: Boolean,
    val declarationFiledDate: String? = null,
    val paymentSentDate: String? = null,
    val paymentCreditedDate: String? = null,
    val paymentAmountGel: String? = null,
    val notes: String
)

@Serializable
data class FxRatePayload(
    val id: Long,
    val rateDate: String,
    val currencyCode: String,
    val units: Int,
    val rateToGel: String,
    val source: String,
    val manualOverride: Boolean
)

@Serializable
data class ImportedStatementPayload(
    val id: Long,
    val sourceFileName: String,
    val sourceFingerprint: String,
    val importedAtEpochMillis: Long
)

@Serializable
data class ImportedTransactionPayload(
    val id: Long,
    val statementId: Long,
    val transactionFingerprint: String,
    val incomeDate: String? = null,
    val description: String,
    val additionalInformation: String? = null,
    val paidOut: String? = null,
    val paidIn: String? = null,
    val balance: String? = null,
    val suggestedInclusion: String,
    val finalInclusion: String
)

data class BackupRestoreResult(
    val reminderConfigImported: Boolean,
    val incomeEntryCount: Int,
    val monthlyRecordCount: Int,
    val importedStatementCount: Int,
    val importedTransactionCount: Int
)
