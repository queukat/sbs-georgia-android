package com.queukat.sbsgeorgia.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SbsGeorgiaDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SbsGeorgiaDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    @Throws(IOException::class)
    fun migrate1To2AddsDefaultReminderTime() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO reminder_config(
                    singletonId,
                    declarationReminderDays,
                    paymentReminderDays,
                    declarationRemindersEnabled,
                    paymentRemindersEnabled,
                    themeMode
                ) VALUES(1, '[10,13,15]', '[10,13,15]', 1, 1, 'SYSTEM')
                """.trimIndent()
            )
            close()
        }

        helper
            .runMigrationsAndValidate(TEST_DB, 2, true, SbsGeorgiaDatabase.MIGRATION_1_2)
            .query("SELECT defaultReminderTime FROM reminder_config WHERE singletonId = 1")
            .use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("09:00", cursor.getString(0))
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3AddsOnboardingMetadataColumns() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO taxpayer_profile(
                    singletonId,
                    registrationId,
                    displayName,
                    baseCurrencyView
                ) VALUES(1, '123456789', 'Jane Doe', 'GEL')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO small_business_status_config(
                    singletonId,
                    effectiveDate,
                    defaultTaxRatePercent
                ) VALUES(1, '2026-03-07', '1.0')
                """.trimIndent()
            )
            close()
        }

        helper
            .runMigrationsAndValidate(
                TEST_DB,
                3,
                true,
                SbsGeorgiaDatabase.MIGRATION_2_3
            ).query(
                """
                SELECT legalForm, registrationDate, legalAddress, activityType
                FROM taxpayer_profile WHERE singletonId = 1
                """.trimIndent()
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(null, cursor.getString(0))
                assertEquals(null, cursor.getString(1))
                assertEquals(null, cursor.getString(2))
                assertEquals(null, cursor.getString(3))
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6AddsDefaultDeclarationFormConfig() {
        helper.createDatabase(TEST_DB, 5).close()

        helper
            .runMigrationsAndValidate(TEST_DB, 6, true, SbsGeorgiaDatabase.MIGRATION_5_6)
            .query(
                """
                SELECT includeCumulativeIncome, includeMonthlyIncome, monthlyIncomeFieldNumber
                FROM declaration_form_config WHERE singletonId = 1
                """.trimIndent()
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(20, cursor.getInt(2))
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7NormalizesLegacyZeroDeclarationsWithoutChangingOtherMonths() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate,
                    paymentAmountGel, notes
                ) VALUES(
                    '2026-02', 2026, 2, 'settled', 1,
                    '2026-04-15', '2026-04-15', '2026-04-15', '0.00', 'Original note'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate,
                    paymentAmountGel, notes
                ) VALUES(
                    '2026-03', 2026, 3, 'settled', 0,
                    '2026-04-02', '2026-04-02', '2026-04-02', '81.60', 'Real tax paid'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate,
                    paymentAmountGel, notes
                ) VALUES(
                    '2026-04', 2026, 4, 'settled', 1,
                    '2026-05-15', '2026-05-15', '2026-05-15', '25.00', 'Actual payment'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate,
                    paymentAmountGel, notes
                ) VALUES(
                    '2026-05', 2026, 5, 'settled', 1,
                    NULL, NULL, NULL, '0.00', 'Unfiled'
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            SbsGeorgiaDatabase.MIGRATION_6_7
        )
        migrated.query(
            """
            SELECT periodKey, workflowStatus, zeroDeclarationPrepared,
                   declarationFiledDate, paymentSentDate, paymentCreditedDate,
                   paymentAmountGel, notes
            FROM monthly_declaration_record
            ORDER BY periodKey
            """.trimIndent()
        ).use { cursor ->
            assertEquals(4, cursor.count)
            cursor.moveToFirst()
            assertEquals("2026-02", cursor.getString(0))
            assertEquals("filed", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("2026-04-15", cursor.getString(3))
            assertEquals(null, cursor.getString(4))
            assertEquals(null, cursor.getString(5))
            assertEquals(null, cursor.getString(6))
            assertEquals("Original note", cursor.getString(7))

            cursor.moveToNext()
            assertEquals("2026-03", cursor.getString(0))
            assertEquals("settled", cursor.getString(1))
            assertEquals("81.60", cursor.getString(6))

            cursor.moveToNext()
            assertEquals("2026-04", cursor.getString(0))
            assertEquals("settled", cursor.getString(1))
            assertEquals("25.00", cursor.getString(6))

            cursor.moveToNext()
            assertEquals("2026-05", cursor.getString(0))
            assertEquals("settled", cursor.getString(1))
            assertEquals("0.00", cursor.getString(6))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To7AppliesBothSchemaAndDataMigrations() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate,
                    paymentAmountGel, notes
                ) VALUES(
                    '2026-02', 2026, 2, 'settled', 1,
                    '2026-04-15', '2026-04-15', '2026-04-15', '0', 'Old build'
                )
                """.trimIndent()
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            SbsGeorgiaDatabase.MIGRATION_5_6,
            SbsGeorgiaDatabase.MIGRATION_6_7
        )
        migrated.query(
            "SELECT workflowStatus, paymentAmountGel, paymentSentDate FROM monthly_declaration_record"
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("filed", cursor.getString(0))
            assertEquals(null, cursor.getString(1))
            assertEquals(null, cursor.getString(2))
        }
        migrated.query(
            "SELECT monthlyIncomeFieldNumber FROM declaration_form_config WHERE singletonId = 1"
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(20, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To7PreservesFirstReleaseDataAndCanonicalizesFiledZeroTax() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO monthly_declaration_record(
                    periodKey, year, month, workflowStatus, zeroDeclarationPrepared,
                    declarationFiledDate, paymentSentDate, paymentCreditedDate, notes
                ) VALUES(
                    '2026-02', 2026, 2, 'settled', 1,
                    '2026-04-15', '2026-04-15', '2026-04-15', 'First release'
                )
                """.trimIndent()
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            SbsGeorgiaDatabase.MIGRATION_1_2,
            SbsGeorgiaDatabase.MIGRATION_2_3,
            SbsGeorgiaDatabase.MIGRATION_3_4,
            SbsGeorgiaDatabase.MIGRATION_4_5,
            SbsGeorgiaDatabase.MIGRATION_5_6,
            SbsGeorgiaDatabase.MIGRATION_6_7
        )
        migrated.query(
            """
            SELECT workflowStatus, declarationFiledDate,
                   paymentSentDate, paymentCreditedDate, paymentAmountGel, notes
            FROM monthly_declaration_record WHERE periodKey = '2026-02'
            """.trimIndent()
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("filed", cursor.getString(0))
            assertEquals("2026-04-15", cursor.getString(1))
            assertEquals(null, cursor.getString(2))
            assertEquals(null, cursor.getString(3))
            assertEquals(null, cursor.getString(4))
            assertEquals("First release", cursor.getString(5))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
