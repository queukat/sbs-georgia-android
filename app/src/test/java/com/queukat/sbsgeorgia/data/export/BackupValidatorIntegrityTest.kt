package com.queukat.sbsgeorgia.data.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorIntegrityTest {
    private val validator = BackupValidator(
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    )

    @Test
    fun buildRestorePlanRejectsDerivedOverdueStatus() {
        assertRestorePlanFails(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-03","year":2026,"month":3,
                      "workflowStatus":"overdue","zeroDeclarationPrepared":false,"notes":""}]
                """.trimIndent()
            ),
            "cannot persist an overdue status"
        )
    }

    @Test
    fun buildRestorePlanRejectsNonPositivePaymentAmount() {
        assertRestorePlanFails(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-03","year":2026,"month":3,
                      "workflowStatus":"payment_sent","zeroDeclarationPrepared":false,
                      "declarationFiledDate":"2026-04-01",
                      "paymentSentDate":"2026-04-02","paymentAmountGel":"0","notes":""}]
                """.trimIndent()
            ),
            "must have positive paymentAmountGel"
        )
    }

    @Test
    fun buildRestorePlanRejectsReminderDaysOutsideFilingWindow() {
        assertRestorePlanFails(
            """
                {"formatVersion":1,"exportedAtEpochMillis":1,"reminderConfig":{
                  "declarationReminderDays":[0,10,16],"paymentReminderDays":[10],
                  "declarationRemindersEnabled":true,"paymentRemindersEnabled":true,
                  "defaultReminderTime":"09:00","themeMode":"system"
                }}
            """.trimIndent(),
            "invalid declaration reminder days"
        )
    }

    @Test
    fun buildRestorePlanStillAcceptsIncompleteProfileForOnboarding() {
        val plan = validator.buildRestorePlan(
            """
                {"formatVersion":1,"exportedAtEpochMillis":1,"taxpayerProfile":{
                  "registrationId":" ","displayName":"","baseCurrencyView":"gel"
                }}
            """.trimIndent()
        )
        assertEquals("", plan.taxpayerProfile?.displayName)
    }

    private fun backupDocument(monthlyDeclarationRecords: String): String =
        """
        {
          "formatVersion": 1,
          "exportedAtEpochMillis": 1,
          "monthlyDeclarationRecords": $monthlyDeclarationRecords
        }
        """.trimIndent()

    private fun assertRestorePlanFails(content: String, messagePart: String) {
        val error = runCatching { validator.buildRestorePlan(content) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains(messagePart))
    }
}
