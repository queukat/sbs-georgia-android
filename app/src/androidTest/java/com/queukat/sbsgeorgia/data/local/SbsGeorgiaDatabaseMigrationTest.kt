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
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SbsGeorgiaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
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
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, SbsGeorgiaDatabase.MIGRATION_1_2)
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
                ) VALUES(1, '306449082', 'Jane Doe', 'GEL')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO small_business_status_config(
                    singletonId,
                    effectiveDate,
                    defaultTaxRatePercent
                ) VALUES(1, '2026-03-07', '1.0')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            SbsGeorgiaDatabase.MIGRATION_2_3,
        ).query(
            """
            SELECT legalForm, registrationDate, legalAddress, activityType
            FROM taxpayer_profile WHERE singletonId = 1
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(null, cursor.getString(0))
            assertEquals(null, cursor.getString(1))
            assertEquals(null, cursor.getString(2))
            assertEquals(null, cursor.getString(3))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
