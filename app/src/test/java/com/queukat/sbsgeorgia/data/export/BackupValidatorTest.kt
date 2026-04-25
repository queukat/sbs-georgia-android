package com.queukat.sbsgeorgia.data.export

import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    private val validator = BackupValidator(
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    )

    @Test
    fun buildRestorePlanAcceptsLegacyEnumNamesAndNormalizesCodes() {
        val plan = validator.buildRestorePlan(
            content = """
                {
                  "formatVersion": 1,
                  "exportedAtEpochMillis": 1,
                  "taxpayerProfile": {
                    "registrationId": "306449082",
                    "displayName": "Test Entrepreneur",
                    "baseCurrencyView": "GEL"
                  },
                  "reminderConfig": {
                    "declarationReminderDays": [10, 13],
                    "paymentReminderDays": [10, 13],
                    "declarationRemindersEnabled": true,
                    "paymentRemindersEnabled": false,
                    "defaultReminderTime": "09:00",
                    "themeMode": "SYSTEM"
                  },
                  "monthlyDeclarationRecords": [
                    {
                      "periodKey": "2026-03",
                      "year": 2026,
                      "month": 3,
                      "workflowStatus": "READY_TO_FILE",
                      "zeroDeclarationPrepared": false,
                      "notes": ""
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals("gel", plan.taxpayerProfile?.baseCurrencyView?.dbCode)
        assertEquals("system", plan.reminderConfig?.themeMode?.dbCode)
        assertEquals(MonthlyWorkflowStatus.READY_TO_FILE.dbCode, plan.monthlyDeclarationRecords.single().workflowStatus)
    }

    @Test
    fun buildRestorePlanRejectsOrphanImportedTransactionsBeforeRestore() {
        val error = runCatching {
            validator.buildRestorePlan(
                content = """
                    {
                      "formatVersion": 1,
                      "exportedAtEpochMillis": 1,
                      "importedTransactions": [
                        {
                          "id": 1,
                          "statementId": 42,
                          "transactionFingerprint": "tx-1",
                          "description": "Payment",
                          "suggestedInclusion": "included",
                          "finalInclusion": "included"
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("missing statementId 42"))
    }

    @Test
    fun buildRestorePlanRejectsMismatchedMonthlyPeriodKey() {
        val error = runCatching {
            validator.buildRestorePlan(
                content = """
                    {
                      "formatVersion": 1,
                      "exportedAtEpochMillis": 1,
                      "monthlyDeclarationRecords": [
                        {
                          "periodKey": "2026-04",
                          "year": 2026,
                          "month": 3,
                          "workflowStatus": "draft",
                          "zeroDeclarationPrepared": false,
                          "notes": ""
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("does not match year=2026, month=3"))
    }
}
