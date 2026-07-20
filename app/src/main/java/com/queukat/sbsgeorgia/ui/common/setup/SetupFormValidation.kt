package com.queukat.sbsgeorgia.ui.common.setup

import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.ui.common.DateInputParser
import com.queukat.sbsgeorgia.ui.common.DateParseResult
import com.queukat.sbsgeorgia.ui.common.dateOrNull
import java.math.BigDecimal
import java.time.LocalDate

internal data class SetupFormState(
    val registrationId: String,
    val displayName: String,
    val legalForm: String,
    val registrationDate: String,
    val legalAddress: String,
    val activityType: String,
    val certificateNumber: String,
    val certificateIssuedDate: String,
    val effectiveDate: LocalDate,
    val taxRatePercent: String
)

internal data class SetupValidationStrings(
    val registrationIdRequired: String,
    val displayNameRequired: String,
    val taxRateInvalid: String,
    val registrationDateInvalid: String,
    val certificateIssuedDateInvalid: String
)

internal enum class SetupValidationField {
    REGISTRATION_ID,
    DISPLAY_NAME,
    TAX_RATE_PERCENT,
    REGISTRATION_DATE,
    CERTIFICATE_ISSUED_DATE
}

internal data class SetupValidatedInput(
    val registrationId: String,
    val displayName: String,
    val legalForm: String?,
    val registrationDate: LocalDate?,
    val legalAddress: String?,
    val activityType: String?,
    val certificateNumber: String?,
    val certificateIssuedDate: LocalDate?,
    val effectiveDate: LocalDate,
    val taxRatePercent: BigDecimal
) {
    fun toTaxpayerProfile(existing: TaxpayerProfile? = null): TaxpayerProfile {
        val profile =
            existing ?: TaxpayerProfile(
                registrationId = registrationId,
                displayName = displayName
            )
        return profile.copy(
            registrationId = registrationId,
            displayName = displayName,
            legalForm = legalForm,
            registrationDate = registrationDate,
            legalAddress = legalAddress,
            activityType = activityType
        )
    }

    fun toStatusConfig(existing: SmallBusinessStatusConfig? = null): SmallBusinessStatusConfig {
        val config =
            existing ?: SmallBusinessStatusConfig(
                effectiveDate = effectiveDate,
                defaultTaxRatePercent = taxRatePercent
            )
        return config.copy(
            effectiveDate = effectiveDate,
            defaultTaxRatePercent = taxRatePercent,
            certificateNumber = certificateNumber,
            certificateIssuedDate = certificateIssuedDate
        )
    }
}

internal sealed interface SetupValidationResult {
    data class Valid(val value: SetupValidatedInput) : SetupValidationResult

    data class Invalid(val field: SetupValidationField, val errorMessage: String) : SetupValidationResult
}

private val defaultRequiredFieldOrder =
    listOf(SetupValidationField.REGISTRATION_ID, SetupValidationField.DISPLAY_NAME)

internal class SetupFormValidator(
    private val strings: SetupValidationStrings,
    private val requiredFieldOrder: List<SetupValidationField> = defaultRequiredFieldOrder
) {
    fun validate(state: SetupFormState): SetupValidationResult {
        val requiredFieldError = validateRequiredFields(state)
        if (requiredFieldError != null) return requiredFieldError

        val taxRate = runCatching { BigDecimal(state.taxRatePercent) }.getOrNull()
        if (taxRate == null || taxRate < BigDecimal.ZERO) {
            return SetupValidationResult.Invalid(
                field = SetupValidationField.TAX_RATE_PERCENT,
                errorMessage = strings.taxRateInvalid
            )
        }

        val registrationDateResult = DateInputParser.parseOptionalIsoDate(state.registrationDate)
        if (registrationDateResult is DateParseResult.Invalid) {
            return SetupValidationResult.Invalid(
                field = SetupValidationField.REGISTRATION_DATE,
                errorMessage = strings.registrationDateInvalid
            )
        }

        val certificateIssuedDateResult =
            DateInputParser.parseOptionalIsoDate(state.certificateIssuedDate)
        if (certificateIssuedDateResult is DateParseResult.Invalid) {
            return SetupValidationResult.Invalid(
                field = SetupValidationField.CERTIFICATE_ISSUED_DATE,
                errorMessage = strings.certificateIssuedDateInvalid
            )
        }

        return SetupValidationResult.Valid(
            SetupValidatedInput(
                registrationId = state.registrationId.trim(),
                displayName = state.displayName.trim(),
                legalForm = state.legalForm.trim().ifBlank { null },
                registrationDate = registrationDateResult.dateOrNull(),
                legalAddress = state.legalAddress.trim().ifBlank { null },
                activityType = state.activityType.trim().ifBlank { null },
                certificateNumber = state.certificateNumber.trim().ifBlank { null },
                certificateIssuedDate = certificateIssuedDateResult.dateOrNull(),
                effectiveDate = state.effectiveDate,
                taxRatePercent = taxRate
            )
        )
    }

    private fun validateRequiredFields(state: SetupFormState): SetupValidationResult.Invalid? =
        requiredFieldOrder.firstNotNullOfOrNull { field ->
            when (field) {
                SetupValidationField.REGISTRATION_ID ->
                    if (state.registrationId.isBlank()) {
                        SetupValidationResult.Invalid(
                            field = field,
                            errorMessage = strings.registrationIdRequired
                        )
                    } else {
                        null
                    }
                SetupValidationField.DISPLAY_NAME ->
                    if (state.displayName.isBlank()) {
                        SetupValidationResult.Invalid(
                            field = field,
                            errorMessage = strings.displayNameRequired
                        )
                    } else {
                        null
                    }
                SetupValidationField.TAX_RATE_PERCENT,
                SetupValidationField.REGISTRATION_DATE,
                SetupValidationField.CERTIFICATE_ISSUED_DATE -> null
            }
        }
}
