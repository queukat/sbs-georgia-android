package com.queukat.sbsgeorgia.ui.settings

import com.queukat.sbsgeorgia.ui.common.setup.SetupFormState
import com.queukat.sbsgeorgia.ui.common.setup.SetupFormValidator
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidatedInput
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidationResult
import com.queukat.sbsgeorgia.ui.common.setup.SetupValidationStrings
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
    val paymentDaysInvalid: String
)

internal data class SettingsValidatedInput(
    val setup: SetupValidatedInput,
    val defaultReminderTime: LocalTime,
    val declarationReminderDays: List<Int>,
    val paymentReminderDays: List<Int>
) {
    val registrationId: String get() = setup.registrationId
    val displayName: String get() = setup.displayName
    val legalForm: String? get() = setup.legalForm
    val registrationDate: LocalDate? get() = setup.registrationDate
    val legalAddress: String? get() = setup.legalAddress
    val activityType: String? get() = setup.activityType
    val certificateNumber: String? get() = setup.certificateNumber
    val certificateIssuedDate: LocalDate? get() = setup.certificateIssuedDate
    val effectiveDate: LocalDate get() = setup.effectiveDate
    val taxRatePercent: BigDecimal get() = setup.taxRatePercent
}

internal sealed interface SettingsValidationResult {
    data class Valid(val value: SettingsValidatedInput) : SettingsValidationResult

    data class Invalid(val errorMessage: String) : SettingsValidationResult
}

internal class SettingsValidator(private val strings: SettingsValidationStrings) {
    private val setupValidator = SetupFormValidator(strings.toSetupValidationStrings())

    fun validate(state: SettingsUiState): SettingsValidationResult {
        val setup =
            when (val setupValidation = setupValidator.validate(state.toSetupFormState())) {
                is SetupValidationResult.Invalid -> {
                    return SettingsValidationResult.Invalid(setupValidation.errorMessage)
                }
                is SetupValidationResult.Valid -> setupValidation.value
            }
        val defaultReminderTime = runCatching {
            LocalTime.parse(state.defaultReminderTime)
        }.getOrNull()
        val declarationReminderDays = parseReminderDays(state.declarationReminderDays)
        val paymentReminderDays = parseReminderDays(state.paymentReminderDays)

        return when {
            defaultReminderTime == null -> SettingsValidationResult.Invalid(
                strings.reminderTimeInvalid
            )
            declarationReminderDays == null -> SettingsValidationResult.Invalid(
                strings.declarationDaysInvalid
            )
            paymentReminderDays == null -> SettingsValidationResult.Invalid(
                strings.paymentDaysInvalid
            )
            else ->
                SettingsValidationResult.Valid(
                    setup.toSettingsValidatedInput(
                        defaultReminderTime = defaultReminderTime,
                        declarationReminderDays = declarationReminderDays,
                        paymentReminderDays = paymentReminderDays
                    )
                )
        }
    }
}

private fun SettingsValidationStrings.toSetupValidationStrings(): SetupValidationStrings = SetupValidationStrings(
    registrationIdRequired = registrationIdRequired,
    displayNameRequired = displayNameRequired,
    taxRateInvalid = taxRateInvalid,
    registrationDateInvalid = registrationDateInvalid,
    certificateIssuedDateInvalid = certificateIssuedDateInvalid
)

private fun SettingsUiState.toSetupFormState(): SetupFormState = SetupFormState(
    registrationId = registrationId,
    displayName = displayName,
    legalForm = legalForm,
    registrationDate = registrationDate,
    legalAddress = legalAddress,
    activityType = activityType,
    certificateNumber = certificateNumber,
    certificateIssuedDate = certificateIssuedDate,
    effectiveDate = effectiveDate,
    taxRatePercent = taxRatePercent
)

private fun SetupValidatedInput.toSettingsValidatedInput(
    defaultReminderTime: LocalTime,
    declarationReminderDays: List<Int>,
    paymentReminderDays: List<Int>
): SettingsValidatedInput = SettingsValidatedInput(
    setup = this,
    defaultReminderTime = defaultReminderTime,
    declarationReminderDays = declarationReminderDays,
    paymentReminderDays = paymentReminderDays
)

internal fun parseReminderDays(value: String): List<Int>? {
    val parts = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    val days = parts.map { it.toIntOrNull() ?: return null }
    if (days.any { it !in 1..15 }) return null
    return days.distinct().sorted()
}
