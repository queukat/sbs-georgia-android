package com.queukat.sbsgeorgia.data.export

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Generated synthetic data only. Never check in a real user's JSON backup. */
class LegacyBackupShapeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val validator = BackupValidator(json)

    @Test
    fun restorePlanAcceptsHistoricLargeBackupShapeAndNormalizesOnlyLegacyZeroTax() {
        val input = syntheticBackup()
        val plan = validator.buildRestorePlan(json.encodeToString(input))

        assertEquals(18, plan.incomeEntries.size)
        assertEquals(13, plan.monthlyDeclarationRecords.size)
        assertEquals(680, plan.fxRates.size)
        assertEquals(12, plan.importedStatements.size)
        assertEquals(2573, plan.importedTransactions.size)

        val zeroMonth = plan.monthlyDeclarationRecords.single { it.periodKey == "2026-02" }
        assertEquals("filed", zeroMonth.workflowStatus)
        assertEquals(true, zeroMonth.zeroDeclarationPrepared)
        assertEquals(LocalDate.of(2026, 4, 15), zeroMonth.declarationFiledDate)
        assertNull(zeroMonth.paymentSentDate)
        assertNull(zeroMonth.paymentCreditedDate)
        assertNull(zeroMonth.paymentAmountGel)

        val paid = plan.monthlyDeclarationRecords.first { it.periodKey == "2025-03" }
        assertEquals("settled", paid.workflowStatus)
        assertEquals("25.00", paid.paymentAmountGel?.toPlainString())
    }

    private fun syntheticBackup(): AppBackupDocument = AppBackupDocument(
        exportedAtEpochMillis = 1,
        taxpayerProfile = TaxpayerProfilePayload(
            registrationId = "synthetic-registration",
            displayName = "Synthetic Taxpayer",
            baseCurrencyView = "gel"
        ),
        statusConfig = SmallBusinessStatusConfigPayload(
            effectiveDate = "2023-12-01",
            defaultTaxRatePercent = "1.0"
        ),
        reminderConfig = ReminderConfigPayload(
            declarationReminderDays = listOf(10, 13, 15),
            paymentReminderDays = listOf(10, 13, 15),
            declarationRemindersEnabled = true,
            paymentRemindersEnabled = true,
            defaultReminderTime = "09:00",
            themeMode = "system"
        ),
        incomeEntries = (1..18).map { index ->
            IncomeEntryPayload(
                id = index.toLong(),
                sourceType = "imported_statement",
                incomeDate = "2025-04-04",
                originalAmount = "100.00",
                originalCurrency = "GEL",
                sourceCategory = "Synthetic income",
                note = "",
                declarationInclusion = "included",
                gelEquivalent = "100.00",
                rateSource = "none",
                manualFxOverride = false,
                sourceStatementId = ((index - 1) % 12 + 1).toLong(),
                sourceTransactionFingerprint = "synthetic-transaction-$index",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1
            )
        },
        monthlyDeclarationRecords = (0..12).map { index ->
            val month = YearMonth.of(2025, 3).plusMonths(index.toLong())
            val legacyZero = month == YearMonth.of(2026, 2)
            val filedZero = month == YearMonth.of(2026, 3)
            MonthlyDeclarationRecordPayload(
                periodKey = month.toString(),
                year = month.year,
                month = month.monthValue,
                workflowStatus = if (filedZero) "filed" else "settled",
                zeroDeclarationPrepared = legacyZero || filedZero,
                declarationFiledDate = if (legacyZero) "2026-04-15" else "2026-04-02",
                paymentSentDate = if (filedZero) null else "2026-04-02",
                paymentCreditedDate = if (filedZero) null else "2026-04-02",
                paymentAmountGel = when {
                    legacyZero -> "0.00"
                    filedZero -> null
                    else -> "25.00"
                },
                notes = ""
            )
        },
        fxRates = (1..680).map { index ->
            FxRatePayload(
                id = index.toLong(),
                rateDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()).toString(),
                currencyCode = "USD",
                units = 1,
                rateToGel = "2.70",
                source = "official_nbg_json",
                manualOverride = false
            )
        },
        importedStatements = (1..12).map { index ->
            ImportedStatementPayload(
                id = index.toLong(),
                sourceFileName = "synthetic-$index.pdf",
                sourceFingerprint = "synthetic-statement-$index",
                importedAtEpochMillis = 1
            )
        },
        importedTransactions = (1..2573).map { index ->
            val included = index <= 18
            ImportedTransactionPayload(
                id = index.toLong(),
                statementId = ((index - 1) % 12 + 1).toLong(),
                transactionFingerprint = "synthetic-transaction-$index",
                incomeDate = "2025-04-04",
                description = "Synthetic transaction",
                paidOut = if (included) null else "1.00",
                paidIn = if (included) "100.00" else null,
                suggestedInclusion = if (included) "included" else "excluded",
                finalInclusion = if (included) "included" else "excluded"
            )
        }
    )
}
