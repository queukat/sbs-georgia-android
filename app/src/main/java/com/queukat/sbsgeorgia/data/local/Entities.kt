package com.queukat.sbsgeorgia.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.queukat.sbsgeorgia.domain.model.BaseCurrencyView
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "taxpayer_profile")
data class TaxpayerProfileEntity(
    @PrimaryKey val singletonId: Int = 1,
    val registrationId: String,
    val displayName: String,
    val baseCurrencyView: BaseCurrencyView,
    val legalForm: String?,
    val registrationDate: LocalDate?,
    val legalAddress: String?,
    val activityType: String?,
)

@Entity(tableName = "small_business_status_config")
data class SmallBusinessStatusConfigEntity(
    @PrimaryKey val singletonId: Int = 1,
    val effectiveDate: LocalDate,
    val defaultTaxRatePercent: BigDecimal,
    val certificateNumber: String?,
    val certificateIssuedDate: LocalDate?,
)

@Entity(tableName = "reminder_config")
data class ReminderConfigEntity(
    @PrimaryKey val singletonId: Int = 1,
    val declarationReminderDays: List<Int>,
    val paymentReminderDays: List<Int>,
    val declarationRemindersEnabled: Boolean,
    val paymentRemindersEnabled: Boolean,
    val defaultReminderTime: LocalTime,
    val themeMode: ThemeMode,
)

@Entity(tableName = "income_entry")
data class IncomeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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
    val sourceStatementId: Long?,
    val sourceTransactionFingerprint: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "monthly_declaration_record",
    indices = [Index(value = ["year", "month"], unique = true)],
)
data class MonthlyDeclarationRecordEntity(
    @PrimaryKey val periodKey: String,
    val year: Int,
    val month: Int,
    val workflowStatus: String,
    val zeroDeclarationPrepared: Boolean,
    val declarationFiledDate: LocalDate?,
    val paymentSentDate: LocalDate?,
    val paymentCreditedDate: LocalDate?,
    val paymentAmountGel: BigDecimal?,
    val notes: String,
)

@Entity(
    tableName = "imported_statement",
    indices = [Index(value = ["sourceFingerprint"], unique = true)],
)
data class ImportedStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceFileName: String,
    val sourceFingerprint: String,
    val importedAtEpochMillis: Long,
)

@Entity(
    tableName = "imported_transaction",
    indices = [Index(value = ["transactionFingerprint"], unique = true)],
)
data class ImportedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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

@Entity(
    tableName = "fx_rate",
    indices = [Index(value = ["rateDate", "currencyCode", "manualOverride"], unique = true)],
)
data class FxRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rateDate: LocalDate,
    val currencyCode: String,
    val units: Int,
    val rateToGel: BigDecimal,
    val source: FxRateSource,
    val manualOverride: Boolean,
)

fun TaxpayerProfileEntity.toDomain(): TaxpayerProfile = TaxpayerProfile(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = baseCurrencyView,
    legalForm = legalForm,
    registrationDate = registrationDate,
    legalAddress = legalAddress,
    activityType = activityType,
)

fun TaxpayerProfile.toEntity(): TaxpayerProfileEntity = TaxpayerProfileEntity(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = baseCurrencyView,
    legalForm = legalForm,
    registrationDate = registrationDate,
    legalAddress = legalAddress,
    activityType = activityType,
)

fun SmallBusinessStatusConfigEntity.toDomain(): SmallBusinessStatusConfig = SmallBusinessStatusConfig(
    effectiveDate = effectiveDate,
    defaultTaxRatePercent = defaultTaxRatePercent,
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate,
)

fun SmallBusinessStatusConfig.toEntity(): SmallBusinessStatusConfigEntity = SmallBusinessStatusConfigEntity(
    effectiveDate = effectiveDate,
    defaultTaxRatePercent = defaultTaxRatePercent,
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate,
)

fun ReminderConfigEntity.toDomain(): ReminderConfig = ReminderConfig(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = defaultReminderTime,
    themeMode = themeMode,
)

fun ReminderConfig.toEntity(): ReminderConfigEntity = ReminderConfigEntity(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = defaultReminderTime,
    themeMode = themeMode,
)
