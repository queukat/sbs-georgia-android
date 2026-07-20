package com.queukat.sbsgeorgia.ui.common.setup

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFormValidatorTest {
    private val strings =
        SetupValidationStrings(
            registrationIdRequired = "registrationIdRequired",
            displayNameRequired = "displayNameRequired",
            taxRateInvalid = "taxRateInvalid",
            registrationDateInvalid = "registrationDateInvalid",
            certificateIssuedDateInvalid = "certificateIssuedDateInvalid"
        )

    @Test
    fun validateReturnsTrimmedSetupAndDomainModels() {
        val result =
            SetupFormValidator(strings).validate(
                SetupFormState(
                    registrationId = " 123456789 ",
                    displayName = " Test Entrepreneur ",
                    legalForm = " IE ",
                    registrationDate = "2026-03-07",
                    legalAddress = " Tbilisi ",
                    activityType = " Software services ",
                    certificateNumber = " CERT-1 ",
                    certificateIssuedDate = "2026-03-08",
                    effectiveDate = LocalDate.of(2026, 3, 7),
                    taxRatePercent = "1.0"
                )
            )

        assertTrue(result is SetupValidationResult.Valid)
        val input = (result as SetupValidationResult.Valid).value
        assertEquals("123456789", input.registrationId)
        assertEquals("Test Entrepreneur", input.displayName)
        assertEquals("IE", input.legalForm)
        assertEquals(LocalDate.of(2026, 3, 7), input.registrationDate)
        assertEquals("Tbilisi", input.legalAddress)
        assertEquals("Software services", input.activityType)
        assertEquals("CERT-1", input.certificateNumber)
        assertEquals(LocalDate.of(2026, 3, 8), input.certificateIssuedDate)
        assertEquals(LocalDate.of(2026, 3, 7), input.effectiveDate)
        assertEquals(BigDecimal("1.0"), input.taxRatePercent)

        assertEquals(input.registrationId, input.toTaxpayerProfile().registrationId)
        assertEquals(input.displayName, input.toTaxpayerProfile().displayName)
        assertEquals(input.taxRatePercent, input.toStatusConfig().defaultTaxRatePercent)
        assertEquals(input.certificateNumber, input.toStatusConfig().certificateNumber)
    }

    @Test
    fun validateUsesConfiguredRequiredFieldOrder() {
        val defaultResult = SetupFormValidator(strings).validate(blankRequiredFieldsState())
        val onboardingResult =
            SetupFormValidator(
                strings = strings,
                requiredFieldOrder =
                listOf(SetupValidationField.DISPLAY_NAME, SetupValidationField.REGISTRATION_ID)
            ).validate(blankRequiredFieldsState())

        assertEquals(
            SetupValidationResult.Invalid(
                field = SetupValidationField.REGISTRATION_ID,
                errorMessage = "registrationIdRequired"
            ),
            defaultResult
        )
        assertEquals(
            SetupValidationResult.Invalid(
                field = SetupValidationField.DISPLAY_NAME,
                errorMessage = "displayNameRequired"
            ),
            onboardingResult
        )
    }

    @Test
    fun validateRejectsInvalidTaxRateBeforeOptionalDates() {
        val result =
            SetupFormValidator(strings).validate(
                validState().copy(
                    taxRatePercent = "-1",
                    registrationDate = "not-a-date"
                )
            )

        assertEquals(
            SetupValidationResult.Invalid(
                field = SetupValidationField.TAX_RATE_PERCENT,
                errorMessage = "taxRateInvalid"
            ),
            result
        )
    }

    private fun blankRequiredFieldsState(): SetupFormState = validState().copy(
        registrationId = "",
        displayName = ""
    )

    private fun validState(): SetupFormState = SetupFormState(
        registrationId = "123456789",
        displayName = "Test Entrepreneur",
        legalForm = "",
        registrationDate = "",
        legalAddress = "",
        activityType = "",
        certificateNumber = "",
        certificateIssuedDate = "",
        effectiveDate = LocalDate.of(2026, 3, 7),
        taxRatePercent = "1.0"
    )
}
