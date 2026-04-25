package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.ui.common.DateInputParser
import com.queukat.sbsgeorgia.ui.common.DateParseResult
import com.queukat.sbsgeorgia.ui.common.dateOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

internal data class SettingsValidationStrings(
    val registrationIdRequired: String,
    val displayNameRequired: String,
    val taxRateInvalid: String,
    val registrationDateInvalid: String,
    val certificateIssuedDateInvalid: String,
    val reminderTimeInvalid: String,
    val declarationDaysInvalid: String,
    val paymentDaysInvalid: String,
)

internal data class SettingsValidatedInput(
    val registrationId: String,
    val displayName: String,
    val legalForm: String?,
    val registrationDate: LocalDate?,
    val legalAddress: String?,
    val activityType: String?,
    val certificateNumber: String?,
    val certificateIssuedDate: LocalDate?,
    val effectiveDate: LocalDate,
    val taxRatePercent: BigDecimal,
    val defaultReminderTime: LocalTime,
    val declarationReminderDays: List<Int>,
    val paymentReminderDays: List<Int>,
)

internal sealed interface SettingsValidationResult {
    data class Valid(val value: SettingsValidatedInput) : SettingsValidationResult

    data class Invalid(val errorMessage: String) : SettingsValidationResult
}

internal class SettingsValidator(
    private val strings: SettingsValidationStrings,
) {
    fun validate(state: SettingsUiState): SettingsValidationResult {
        val taxRate = runCatching { BigDecimal(state.taxRatePercent) }.getOrNull()
        val defaultReminderTime = runCatching { LocalTime.parse(state.defaultReminderTime) }.getOrNull()
        val registrationDateResult = DateInputParser.parseOptionalIsoDate(state.registrationDate)
        val certificateIssuedDateResult = DateInputParser.parseOptionalIsoDate(state.certificateIssuedDate)
        val declarationReminderDays = parseReminderDays(state.declarationReminderDays)
        val paymentReminderDays = parseReminderDays(state.paymentReminderDays)

        return when {
            state.registrationId.isBlank() -> SettingsValidationResult.Invalid(strings.registrationIdRequired)
            state.displayName.isBlank() -> SettingsValidationResult.Invalid(strings.displayNameRequired)
            taxRate == null || taxRate < BigDecimal.ZERO -> SettingsValidationResult.Invalid(strings.taxRateInvalid)
            registrationDateResult is DateParseResult.Invalid -> SettingsValidationResult.Invalid(strings.registrationDateInvalid)
            certificateIssuedDateResult is DateParseResult.Invalid -> SettingsValidationResult.Invalid(strings.certificateIssuedDateInvalid)
            defaultReminderTime == null -> SettingsValidationResult.Invalid(strings.reminderTimeInvalid)
            declarationReminderDays == null -> SettingsValidationResult.Invalid(strings.declarationDaysInvalid)
            paymentReminderDays == null -> SettingsValidationResult.Invalid(strings.paymentDaysInvalid)
            else -> SettingsValidationResult.Valid(
                SettingsValidatedInput(
                    registrationId = state.registrationId.trim(),
                    displayName = state.displayName.trim(),
                    legalForm = state.legalForm.trim().ifBlank { null },
                    registrationDate = registrationDateResult.dateOrNull(),
                    legalAddress = state.legalAddress.trim().ifBlank { null },
                    activityType = state.activityType.trim().ifBlank { null },
                    certificateNumber = state.certificateNumber.trim().ifBlank { null },
                    certificateIssuedDate = certificateIssuedDateResult.dateOrNull(),
                    effectiveDate = state.effectiveDate,
                    taxRatePercent = taxRate,
                    defaultReminderTime = defaultReminderTime,
                    declarationReminderDays = declarationReminderDays,
                    paymentReminderDays = paymentReminderDays,
                ),
            )
        }
    }
}

internal fun parseReminderDays(value: String): List<Int>? {
    val parts = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    val days = parts.map { it.toIntOrNull() ?: return null }
    if (days.any { it !in 1..15 }) return null
    return days.distinct().sorted()
}
