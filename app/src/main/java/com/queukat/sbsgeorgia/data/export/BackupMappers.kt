package com.queukat.sbsgeorgia.data.export

import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigEntity
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigEntity
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileEntity
import com.queukat.sbsgeorgia.domain.model.BaseCurrencyView
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

internal fun TaxpayerProfileEntity.toPayload(): TaxpayerProfilePayload = TaxpayerProfilePayload(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = baseCurrencyView.dbCode,
    legalForm = legalForm,
    registrationDate = registrationDate?.toString(),
    legalAddress = legalAddress,
    activityType = activityType,
)

internal fun TaxpayerProfilePayload.toEntity(): TaxpayerProfileEntity = TaxpayerProfileEntity(
    registrationId = registrationId,
    displayName = displayName,
    baseCurrencyView = BaseCurrencyView.fromPersisted(baseCurrencyView),
    legalForm = legalForm,
    registrationDate = registrationDate?.let(LocalDate::parse),
    legalAddress = legalAddress,
    activityType = activityType,
)

internal fun SmallBusinessStatusConfigEntity.toPayload(): SmallBusinessStatusConfigPayload = SmallBusinessStatusConfigPayload(
    effectiveDate = effectiveDate.toString(),
    defaultTaxRatePercent = defaultTaxRatePercent.toPlainString(),
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate?.toString(),
)

internal fun SmallBusinessStatusConfigPayload.toEntity(): SmallBusinessStatusConfigEntity = SmallBusinessStatusConfigEntity(
    effectiveDate = LocalDate.parse(effectiveDate),
    defaultTaxRatePercent = BigDecimal(defaultTaxRatePercent),
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate?.let(LocalDate::parse),
)

internal fun ReminderConfigEntity.toPayload(): ReminderConfigPayload = ReminderConfigPayload(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = defaultReminderTime.toString(),
    themeMode = themeMode.dbCode,
)

internal fun ReminderConfigPayload.toEntity(): ReminderConfigEntity = ReminderConfigEntity(
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays,
    declarationRemindersEnabled = declarationRemindersEnabled,
    paymentRemindersEnabled = paymentRemindersEnabled,
    defaultReminderTime = LocalTime.parse(defaultReminderTime),
    themeMode = ThemeMode.fromPersisted(themeMode),
)

internal fun IncomeEntryEntity.toPayload(): IncomeEntryPayload = IncomeEntryPayload(
    id = id,
    sourceType = sourceType.dbCode,
    incomeDate = incomeDate.toString(),
    originalAmount = originalAmount.toPlainString(),
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = declarationInclusion.dbCode,
    gelEquivalent = gelEquivalent?.toPlainString(),
    rateSource = rateSource.dbCode,
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun IncomeEntryPayload.toEntity(): IncomeEntryEntity = IncomeEntryEntity(
    id = id,
    sourceType = IncomeSourceType.fromPersisted(sourceType),
    incomeDate = LocalDate.parse(incomeDate),
    originalAmount = BigDecimal(originalAmount),
    originalCurrency = originalCurrency,
    sourceCategory = sourceCategory,
    note = note,
    declarationInclusion = DeclarationInclusion.fromPersisted(declarationInclusion),
    gelEquivalent = gelEquivalent?.let(::BigDecimal),
    rateSource = FxRateSource.fromPersisted(rateSource),
    manualFxOverride = manualFxOverride,
    sourceStatementId = sourceStatementId,
    sourceTransactionFingerprint = sourceTransactionFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun MonthlyDeclarationRecordEntity.toPayload(): MonthlyDeclarationRecordPayload = MonthlyDeclarationRecordPayload(
    periodKey = periodKey,
    year = year,
    month = month,
    workflowStatus = MonthlyWorkflowStatus.fromPersisted(workflowStatus).dbCode,
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate?.toString(),
    paymentSentDate = paymentSentDate?.toString(),
    paymentCreditedDate = paymentCreditedDate?.toString(),
    paymentAmountGel = paymentAmountGel?.toPlainString(),
    notes = notes,
)

internal fun MonthlyDeclarationRecordPayload.toEntity(): MonthlyDeclarationRecordEntity = MonthlyDeclarationRecordEntity(
    periodKey = periodKey,
    year = year,
    month = month,
    workflowStatus = MonthlyWorkflowStatus.fromPersisted(workflowStatus).dbCode,
    zeroDeclarationPrepared = zeroDeclarationPrepared,
    declarationFiledDate = declarationFiledDate?.let(LocalDate::parse),
    paymentSentDate = paymentSentDate?.let(LocalDate::parse),
    paymentCreditedDate = paymentCreditedDate?.let(LocalDate::parse),
    paymentAmountGel = paymentAmountGel?.let(::BigDecimal),
    notes = notes,
)

internal fun FxRateEntity.toPayload(): FxRatePayload = FxRatePayload(
    id = id,
    rateDate = rateDate.toString(),
    currencyCode = currencyCode,
    units = units,
    rateToGel = rateToGel.toPlainString(),
    source = source.dbCode,
    manualOverride = manualOverride,
)

internal fun FxRatePayload.toEntity(): FxRateEntity = FxRateEntity(
    id = id,
    rateDate = LocalDate.parse(rateDate),
    currencyCode = currencyCode,
    units = units,
    rateToGel = BigDecimal(rateToGel),
    source = FxRateSource.fromPersisted(source),
    manualOverride = manualOverride,
)

internal fun ImportedStatementEntity.toPayload(): ImportedStatementPayload = ImportedStatementPayload(
    id = id,
    sourceFileName = sourceFileName,
    sourceFingerprint = sourceFingerprint,
    importedAtEpochMillis = importedAtEpochMillis,
)

internal fun ImportedStatementPayload.toEntity(): ImportedStatementEntity = ImportedStatementEntity(
    id = id,
    sourceFileName = sourceFileName,
    sourceFingerprint = sourceFingerprint,
    importedAtEpochMillis = importedAtEpochMillis,
)

internal fun ImportedTransactionEntity.toPayload(): ImportedTransactionPayload = ImportedTransactionPayload(
    id = id,
    statementId = statementId,
    transactionFingerprint = transactionFingerprint,
    incomeDate = incomeDate?.toString(),
    description = description,
    additionalInformation = additionalInformation,
    paidOut = paidOut?.toPlainString(),
    paidIn = paidIn?.toPlainString(),
    balance = balance?.toPlainString(),
    suggestedInclusion = suggestedInclusion.dbCode,
    finalInclusion = finalInclusion.dbCode,
)

internal fun ImportedTransactionPayload.toEntity(): ImportedTransactionEntity = ImportedTransactionEntity(
    id = id,
    statementId = statementId,
    transactionFingerprint = transactionFingerprint,
    incomeDate = incomeDate?.let(LocalDate::parse),
    description = description,
    additionalInformation = additionalInformation,
    paidOut = paidOut?.let(::BigDecimal),
    paidIn = paidIn?.let(::BigDecimal),
    balance = balance?.let(::BigDecimal),
    suggestedInclusion = DeclarationInclusion.fromPersisted(suggestedInclusion),
    finalInclusion = DeclarationInclusion.fromPersisted(finalInclusion),
)
