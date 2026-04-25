package com.queukat.sbsgeorgia.ui.settings

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {
    private val validator = SettingsValidator(
        strings = SettingsValidationStrings(
            registrationIdRequired = "registrationIdRequired",
            displayNameRequired = "displayNameRequired",
            taxRateInvalid = "taxRateInvalid",
            registrationDateInvalid = "registrationDateInvalid",
            certificateIssuedDateInvalid = "certificateIssuedDateInvalid",
            reminderTimeInvalid = "reminderTimeInvalid",
            declarationDaysInvalid = "declarationDaysInvalid",
            paymentDaysInvalid = "paymentDaysInvalid",
        ),
    )

    @Test
    fun validateReturnsParsedPayloadForValidState() {
        val result = validator.validate(
            SettingsUiState(
                registrationId = " 306449082 ",
                displayName = " Test Entrepreneur ",
                legalForm = " IE ",
                registrationDate = "2026-03-07",
                legalAddress = " Tbilisi ",
                activityType = " Software services ",
                certificateNumber = " CERT-1 ",
                certificateIssuedDate = "2026-03-08",
                effectiveDate = LocalDate.of(2026, 3, 7),
                taxRatePercent = "1.0",
                defaultReminderTime = "09:30",
                declarationReminderDays = "10, 13, 13",
                paymentReminderDays = "15, 10",
            ),
        )

        assertTrue(result is SettingsValidationResult.Valid)
        val value = (result as SettingsValidationResult.Valid).value
        assertEquals("306449082", value.registrationId)
        assertEquals("Test Entrepreneur", value.displayName)
        assertEquals("IE", value.legalForm)
        assertEquals(LocalDate.of(2026, 3, 7), value.registrationDate)
        assertEquals("Tbilisi", value.legalAddress)
        assertEquals("Software services", value.activityType)
        assertEquals("CERT-1", value.certificateNumber)
        assertEquals(LocalDate.of(2026, 3, 8), value.certificateIssuedDate)
        assertEquals(LocalTime.of(9, 30), value.defaultReminderTime)
        assertEquals(listOf(10, 13), value.declarationReminderDays)
        assertEquals(listOf(10, 15), value.paymentReminderDays)
    }

    @Test
    fun validateRejectsInvalidReminderDays() {
        val result = validator.validate(
            SettingsUiState(
                registrationId = "306449082",
                displayName = "Test Entrepreneur",
                declarationReminderDays = "0,16",
            ),
        )

        assertEquals(
            SettingsValidationResult.Invalid("declarationDaysInvalid"),
            result,
        )
    }
}
