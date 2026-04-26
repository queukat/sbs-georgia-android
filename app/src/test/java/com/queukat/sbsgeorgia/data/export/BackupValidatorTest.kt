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
                content = backupDocument(
                    importedTransactions = """
                        [
                          {
                            "id": 1,
                            "statementId": 42,
                            "transactionFingerprint": "tx-1",
                            "incomeDate": "2026-03-10",
                            "description": "Payment",
                            "paidIn": "125.50",
                            "suggestedInclusion": "included",
                            "finalInclusion": "included"
                          }
                        ]
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("missing statementId 42"))
    }

    @Test
    fun buildRestorePlanRejectsMismatchedMonthlyPeriodKey() {
        val error = runCatching {
            validator.buildRestorePlan(
                content = backupDocument(
                    monthlyDeclarationRecords = """
                        [
                          {
                            "periodKey": "2026-04",
                            "year": 2026,
                            "month": 3,
                            "workflowStatus": "draft",
                            "zeroDeclarationPrepared": false,
                            "notes": ""
                          }
                        ]
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("does not match year=2026, month=3"))
    }

    @Test
    fun buildRestorePlanRejectsNonPositiveIncomeAmount() {
        assertRestorePlanFails(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "manual",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "0.00",
                        "originalCurrency": "USD",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "must have positive originalAmount",
        )
    }

    @Test
    fun buildRestorePlanRejectsBlankIncomeCurrency() {
        assertRestorePlanFails(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "manual",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "125.50",
                        "originalCurrency": "",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "must have valid originalCurrency",
        )
    }

    @Test
    fun buildRestorePlanRejectsInvalidIncomeCurrencyCode() {
        assertRestorePlanFails(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "manual",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "125.50",
                        "originalCurrency": "lari",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "must have valid originalCurrency",
        )
    }

    @Test
    fun buildRestorePlanRejectsNonPositiveFxUnits() {
        assertRestorePlanFails(
            content = backupDocument(
                fxRates = """
                    [
                      {
                        "id": 1,
                        "rateDate": "2026-03-10",
                        "currencyCode": "USD",
                        "units": 0,
                        "rateToGel": "2.70",
                        "source": "official_nbg_json",
                        "manualOverride": false
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "must have positive units",
        )
    }

    @Test
    fun buildRestorePlanRejectsNonPositiveFxRateToGel() {
        assertRestorePlanFails(
            content = backupDocument(
                fxRates = """
                    [
                      {
                        "id": 1,
                        "rateDate": "2026-03-10",
                        "currencyCode": "USD",
                        "units": 1,
                        "rateToGel": "0",
                        "source": "official_nbg_json",
                        "manualOverride": false
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "must have positive rateToGel",
        )
    }

    @Test
    fun buildRestorePlanRejectsIncludedImportedTransactionWithoutIncomeDate() {
        assertRestorePlanFails(
            content = backupDocument(
                importedStatements = """
                    [
                      {
                        "id": 42,
                        "sourceFileName": "statement.pdf",
                        "sourceFingerprint": "statement-1",
                        "importedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
                importedTransactions = """
                    [
                      {
                        "id": 1,
                        "statementId": 42,
                        "transactionFingerprint": "tx-1",
                        "description": "Payment",
                        "paidIn": "125.50",
                        "suggestedInclusion": "included",
                        "finalInclusion": "included"
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "has no incomeDate",
        )
    }

    @Test
    fun buildRestorePlanAcceptsIncludedImportedTransactionWithoutPaidInWhenLinkedIncomeEntryExists() {
        val plan = validator.buildRestorePlan(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "imported_statement",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "125.50",
                        "originalCurrency": "USD",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "sourceStatementId": 42,
                        "sourceTransactionFingerprint": "tx-1",
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
                importedStatements = """
                    [
                      {
                        "id": 42,
                        "sourceFileName": "statement.pdf",
                        "sourceFingerprint": "statement-1",
                        "importedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
                importedTransactions = """
                    [
                      {
                        "id": 1,
                        "statementId": 42,
                        "transactionFingerprint": "tx-1",
                        "incomeDate": "2026-03-10",
                        "description": "Payment",
                        "suggestedInclusion": "included",
                        "finalInclusion": "included"
                      }
                    ]
                """.trimIndent(),
            ),
        )

        assertEquals(1, plan.incomeEntries.size)
        assertEquals(1, plan.importedTransactions.size)
    }

    @Test
    fun buildRestorePlanRejectsIncludedImportedTransactionWithoutLinkedIncomeEntry() {
        assertRestorePlanFails(
            content = backupDocument(
                importedStatements = """
                    [
                      {
                        "id": 42,
                        "sourceFileName": "statement.pdf",
                        "sourceFingerprint": "statement-1",
                        "importedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
                importedTransactions = """
                    [
                      {
                        "id": 1,
                        "statementId": 42,
                        "transactionFingerprint": "tx-1",
                        "incomeDate": "2026-03-10",
                        "description": "Payment",
                        "suggestedInclusion": "included",
                        "finalInclusion": "included"
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "has no linked income entry",
        )
    }

    @Test
    fun buildRestorePlanRejectsDuplicateIncomeSourceFingerprints() {
        assertRestorePlanFails(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "imported_statement",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "125.50",
                        "originalCurrency": "USD",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "sourceStatementId": 42,
                        "sourceTransactionFingerprint": "tx-1",
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      },
                      {
                        "id": 2,
                        "sourceType": "imported_statement",
                        "incomeDate": "2026-03-11",
                        "originalAmount": "225.50",
                        "originalCurrency": "USD",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "sourceStatementId": 42,
                        "sourceTransactionFingerprint": "tx-1",
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "duplicate income entry sourceTransactionFingerprint values",
        )
    }

    @Test
    fun buildRestorePlanRejectsConflictingIncomeSourceLinkage() {
        assertRestorePlanFails(
            content = backupDocument(
                incomeEntries = """
                    [
                      {
                        "id": 1,
                        "sourceType": "imported_statement",
                        "incomeDate": "2026-03-10",
                        "originalAmount": "125.50",
                        "originalCurrency": "USD",
                        "sourceCategory": "Software services",
                        "note": "",
                        "declarationInclusion": "included",
                        "rateSource": "none",
                        "manualFxOverride": false,
                        "sourceStatementId": 42,
                        "sourceTransactionFingerprint": "tx-1",
                        "createdAtEpochMillis": 1,
                        "updatedAtEpochMillis": 1
                      }
                    ]
                """.trimIndent(),
                importedStatements = """
                    [
                      {
                        "id": 42,
                        "sourceFileName": "statement.pdf",
                        "sourceFingerprint": "statement-1",
                        "importedAtEpochMillis": 1
                      },
                      {
                        "id": 43,
                        "sourceFileName": "statement-2.pdf",
                        "sourceFingerprint": "statement-2",
                        "importedAtEpochMillis": 2
                      }
                    ]
                """.trimIndent(),
                importedTransactions = """
                    [
                      {
                        "id": 1,
                        "statementId": 43,
                        "transactionFingerprint": "tx-1",
                        "incomeDate": "2026-03-10",
                        "description": "Payment",
                        "paidIn": "125.50",
                        "suggestedInclusion": "included",
                        "finalInclusion": "included"
                      }
                    ]
                """.trimIndent(),
            ),
            messagePart = "conflicting sourceStatementId/sourceTransactionFingerprint linkage",
        )
    }

    private fun assertRestorePlanFails(
        content: String,
        messagePart: String,
    ) {
        val error = runCatching {
            validator.buildRestorePlan(content)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains(messagePart))
    }

    private fun backupDocument(
        incomeEntries: String = "[]",
        monthlyDeclarationRecords: String = "[]",
        fxRates: String = "[]",
        importedStatements: String = "[]",
        importedTransactions: String = "[]",
    ): String = """
        {
          "formatVersion": 1,
          "exportedAtEpochMillis": 1,
          "incomeEntries": $incomeEntries,
          "monthlyDeclarationRecords": $monthlyDeclarationRecords,
          "fxRates": $fxRates,
          "importedStatements": $importedStatements,
          "importedTransactions": $importedTransactions
        }
    """.trimIndent()
}
