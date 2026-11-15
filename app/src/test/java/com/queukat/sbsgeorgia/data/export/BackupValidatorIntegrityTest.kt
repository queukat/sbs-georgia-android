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
    fun buildRestorePlanMigratesLegacySettledZeroTaxWithoutInventedPayment() {
        val plan = validator.buildRestorePlan(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-02","year":2026,"month":2,
                      "workflowStatus":"settled","zeroDeclarationPrepared":true,
                      "declarationFiledDate":"2026-04-15",
                      "paymentSentDate":"2026-04-15","paymentCreditedDate":"2026-04-15",
                      "paymentAmountGel":"0.00","notes":"Legacy filed month"}]
                """.trimIndent()
            )
        )

        val record = plan.monthlyDeclarationRecords.single()
        assertEquals("2026-02", record.periodKey)
        assertEquals("filed", record.workflowStatus)
        assertEquals(true, record.zeroDeclarationPrepared)
        assertEquals(java.time.LocalDate.of(2026, 4, 15), record.declarationFiledDate)
        assertEquals(null, record.paymentSentDate)
        assertEquals(null, record.paymentCreditedDate)
        assertEquals(null, record.paymentAmountGel)
        assertEquals("Legacy filed month", record.notes)
    }

    @Test
    fun buildRestorePlanKeepsValidPositivePaymentEvenWithZeroDeclarationFlag() {
        val record = validator.buildRestorePlan(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-02","year":2026,"month":2,
                      "workflowStatus":"settled","zeroDeclarationPrepared":true,
                      "declarationFiledDate":"2026-03-15",
                      "paymentSentDate":"2026-03-15","paymentCreditedDate":"2026-03-15",
                      "paymentAmountGel":"25.00","notes":""}]
                """.trimIndent()
            )
        ).monthlyDeclarationRecords.single()

        assertEquals("settled", record.workflowStatus)
        assertEquals("25.00", record.paymentAmountGel?.toPlainString())
        assertEquals(java.time.LocalDate.of(2026, 3, 15), record.paymentCreditedDate)
    }

    @Test
    fun buildRestorePlanRejectsZeroPaymentForUnfiledZeroDeclaration() {
        assertRestorePlanFails(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-02","year":2026,"month":2,
                      "workflowStatus":"settled","zeroDeclarationPrepared":true,
                      "paymentAmountGel":"0.00","notes":""}]
                """.trimIndent()
            ),
            "must have positive paymentAmountGel"
        )
    }

    @Test
    fun buildRestorePlanRejectsNegativePaymentForLegacyZeroDeclaration() {
        assertRestorePlanFails(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-02","year":2026,"month":2,
                      "workflowStatus":"settled","zeroDeclarationPrepared":true,
                      "declarationFiledDate":"2026-03-15",
                      "paymentAmountGel":"-0.01","notes":""}]
                """.trimIndent()
            ),
            "must have positive paymentAmountGel"
        )
    }

    @Test
    fun buildRestorePlanMigratesFiledZeroTaxWithOldPaymentDates() {
        val record = validator.buildRestorePlan(
            backupDocument(
                monthlyDeclarationRecords = """
                    [{"periodKey":"2026-02","year":2026,"month":2,
                      "workflowStatus":"filed","zeroDeclarationPrepared":true,
                      "declarationFiledDate":"2026-03-15",
                      "paymentSentDate":"2026-03-15",
                      "paymentAmountGel":"0.00","notes":""}]
                """.trimIndent()
            )
        ).monthlyDeclarationRecords.single()

        assertEquals("filed", record.workflowStatus)
        assertEquals(null, record.paymentSentDate)
        assertEquals(null, record.paymentCreditedDate)
        assertEquals(null, record.paymentAmountGel)
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
