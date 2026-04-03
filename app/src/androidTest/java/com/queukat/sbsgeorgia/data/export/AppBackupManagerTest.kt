package com.queukat.sbsgeorgia.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.local.ImportedStatementEntity
import com.queukat.sbsgeorgia.data.local.ImportedTransactionEntity
import com.queukat.sbsgeorgia.data.local.IncomeEntryEntity
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordEntity
import com.queukat.sbsgeorgia.data.local.ReminderConfigEntity
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigEntity
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileEntity
import com.queukat.sbsgeorgia.domain.model.BaseCurrencyView
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBackupManagerTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun exportAndImportRoundTripPreservesRawImportedStatementMetadata() = runTest {
        val sourceDb = buildDatabase("backup-source")
        val restoreDb = buildDatabase("backup-restore")
        try {
            val sourceStatementId = sourceDb.importedStatementDao().insert(
                ImportedStatementEntity(
                    sourceFileName = "march-statement.pdf",
                    sourceFingerprint = "statement-fingerprint",
                    importedAtEpochMillis = 1234L,
                ),
            )
            sourceDb.taxpayerProfileDao().upsert(
                TaxpayerProfileEntity(
                    registrationId = "306449082",
                    displayName = "Jane Doe",
                    baseCurrencyView = BaseCurrencyView.GEL,
                    legalForm = "Individual Entrepreneur",
                    registrationDate = LocalDate.of(2023, 11, 24),
                    legalAddress = "Georgia, Tbilisi",
                    activityType = "Software development services",
                ),
            )
            sourceDb.smallBusinessStatusConfigDao().upsert(
                SmallBusinessStatusConfigEntity(
                    effectiveDate = LocalDate.of(2026, 3, 7),
                    defaultTaxRatePercent = BigDecimal("1.0"),
                    certificateNumber = "SBS-2026-000123",
                    certificateIssuedDate = LocalDate.of(2026, 3, 7),
                ),
            )
            sourceDb.reminderConfigDao().upsert(
                ReminderConfigEntity(
                    declarationReminderDays = listOf(10, 13, 15),
                    paymentReminderDays = listOf(10, 13, 15),
                    declarationRemindersEnabled = true,
                    paymentRemindersEnabled = true,
                    defaultReminderTime = LocalTime.of(9, 0),
                    themeMode = ThemeMode.DARK,
                ),
            )
            sourceDb.incomeEntryDao().upsert(
                IncomeEntryEntity(
                    id = 11L,
                    sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                    incomeDate = LocalDate.of(2026, 3, 15),
                    originalAmount = BigDecimal("125.50"),
                    originalCurrency = "USD",
                    sourceCategory = "Software services",
                    note = "Imported row",
                    declarationInclusion = DeclarationInclusion.INCLUDED,
                    gelEquivalent = BigDecimal("338.85"),
                    rateSource = FxRateSource.OFFICIAL_NBG_JSON,
                    manualFxOverride = false,
                    sourceStatementId = sourceStatementId,
                    sourceTransactionFingerprint = "tx-fingerprint",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 200L,
                ),
            )
            sourceDb.monthlyDeclarationRecordDao().upsert(
                MonthlyDeclarationRecordEntity(
                    periodKey = "2026-03",
                    year = 2026,
                    month = 3,
                    workflowStatus = "FILED",
                    zeroDeclarationPrepared = false,
                    declarationFiledDate = LocalDate.of(2026, 4, 5),
                    paymentSentDate = null,
                    paymentCreditedDate = null,
                    notes = "Filed on time",
                ),
            )
            sourceDb.fxRateDao().upsert(
                FxRateEntity(
                    id = 21L,
                    rateDate = LocalDate.of(2026, 3, 15),
                    currencyCode = "USD",
                    units = 1,
                    rateToGel = BigDecimal("2.70"),
                    source = FxRateSource.OFFICIAL_NBG_JSON,
                    manualOverride = false,
                ),
            )
            sourceDb.importedTransactionDao().replaceAll(
                listOf(
                    ImportedTransactionEntity(
                        id = 31L,
                        statementId = sourceStatementId,
                        transactionFingerprint = "tx-fingerprint",
                        incomeDate = LocalDate.of(2026, 3, 15),
                        description = "FOR SOFTWARE SERVICES",
                        additionalInformation = "Invoice 001",
                        paidOut = null,
                        paidIn = BigDecimal("125.50"),
                        balance = BigDecimal("1000.00"),
                        suggestedInclusion = DeclarationInclusion.INCLUDED,
                        finalInclusion = DeclarationInclusion.INCLUDED,
                    ),
                ),
            )

            val sourceManager = backupManager(sourceDb)
            val restoreManager = backupManager(restoreDb)
            val exportedJson = sourceManager.exportJson()
            val result = restoreManager.importJson(exportedJson)

            assertEquals(1, result.incomeEntryCount)
            assertEquals(1, result.monthlyRecordCount)
            assertEquals(1, result.importedStatementCount)
            assertEquals(1, result.importedTransactionCount)

            val restoredProfile = restoreDb.taxpayerProfileDao().get()
            val restoredReminder = restoreDb.reminderConfigDao().get()
            val restoredStatements = restoreDb.importedStatementDao().getAll()
            val restoredTransactions = restoreDb.importedTransactionDao().getAll()
            val restoredIncomeEntries = restoreDb.incomeEntryDao().getAll()
            val restoredFxRates = restoreDb.fxRateDao().getAll()

            assertEquals("306449082", restoredProfile?.registrationId)
            assertEquals("Individual Entrepreneur", restoredProfile?.legalForm)
            assertEquals(LocalDate.of(2023, 11, 24), restoredProfile?.registrationDate)
            assertEquals(LocalTime.of(9, 0), restoredReminder?.defaultReminderTime)
            assertEquals("march-statement.pdf", restoredStatements.single().sourceFileName)
            assertEquals("statement-fingerprint", restoredStatements.single().sourceFingerprint)
            assertEquals("tx-fingerprint", restoredTransactions.single().transactionFingerprint)
            assertEquals(restoredStatements.single().id, restoredTransactions.single().statementId)
            assertEquals(restoredStatements.single().id, restoredIncomeEntries.single().sourceStatementId)
            assertEquals("2.70", restoredFxRates.single().rateToGel.toPlainString())
            assertTrue(exportedJson.contains("\"certificateNumber\":\"SBS-2026-000123\""))
            assertTrue(exportedJson.contains("\"importedStatements\""))
            assertTrue(exportedJson.contains("\"sourceFingerprint\":\"statement-fingerprint\""))
        } finally {
            sourceDb.close()
            restoreDb.close()
        }
    }

    private fun buildDatabase(name: String): SbsGeorgiaDatabase =
        Room.inMemoryDatabaseBuilder(appContext, SbsGeorgiaDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun backupManager(database: SbsGeorgiaDatabase): AppBackupManager = AppBackupManager(
        database = database,
        taxpayerProfileDao = database.taxpayerProfileDao(),
        statusConfigDao = database.smallBusinessStatusConfigDao(),
        reminderConfigDao = database.reminderConfigDao(),
        incomeEntryDao = database.incomeEntryDao(),
        monthlyDeclarationRecordDao = database.monthlyDeclarationRecordDao(),
        fxRateDao = database.fxRateDao(),
        importedStatementDao = database.importedStatementDao(),
        importedTransactionDao = database.importedTransactionDao(),
        json = json,
        clock = fixedClock,
        ioDispatcher = Dispatchers.IO,
    )
}
