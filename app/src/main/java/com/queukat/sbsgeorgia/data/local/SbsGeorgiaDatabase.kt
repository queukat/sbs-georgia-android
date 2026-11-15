package com.queukat.sbsgeorgia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaxpayerProfileEntity::class,
        SmallBusinessStatusConfigEntity::class,
        ReminderConfigEntity::class,
        DeclarationFormConfigEntity::class,
        IncomeEntryEntity::class,
        MonthlyDeclarationRecordEntity::class,
        ImportedStatementEntity::class,
        ImportedTransactionEntity::class,
        FxRateEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class SbsGeorgiaDatabase : RoomDatabase() {
    abstract fun taxpayerProfileDao(): TaxpayerProfileDao

    abstract fun smallBusinessStatusConfigDao(): SmallBusinessStatusConfigDao

    abstract fun reminderConfigDao(): ReminderConfigDao

    abstract fun declarationFormConfigDao(): DeclarationFormConfigDao

    abstract fun incomeEntryDao(): IncomeEntryDao

    abstract fun monthlyDeclarationRecordDao(): MonthlyDeclarationRecordDao

    abstract fun fxRateDao(): FxRateDao

    abstract fun importedStatementDao(): ImportedStatementDao

    abstract fun importedTransactionDao(): ImportedTransactionDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE reminder_config
                        ADD COLUMN defaultReminderTime TEXT NOT NULL DEFAULT '09:00'
                        """.trimIndent()
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE taxpayer_profile
                        ADD COLUMN legalForm TEXT
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE taxpayer_profile
                        ADD COLUMN registrationDate TEXT
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE taxpayer_profile
                        ADD COLUMN legalAddress TEXT
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE taxpayer_profile
                        ADD COLUMN activityType TEXT
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE small_business_status_config
                        ADD COLUMN certificateNumber TEXT
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE small_business_status_config
                        ADD COLUMN certificateIssuedDate TEXT
                        """.trimIndent()
                    )
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE monthly_declaration_record
                        ADD COLUMN paymentAmountGel TEXT
                        """.trimIndent()
                    )
                }
            }

        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_income_entry_incomeDate
                        ON income_entry (incomeDate)
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_income_entry_sourceTransactionFingerprint
                        ON income_entry (sourceTransactionFingerprint)
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_imported_transaction_statementId
                        ON imported_transaction (statementId)
                        """.trimIndent()
                    )
                }
            }

        val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS declaration_form_config (
                            singletonId INTEGER NOT NULL,
                            includeCumulativeIncome INTEGER NOT NULL,
                            includeMonthlyIncome INTEGER NOT NULL,
                            monthlyIncomeFieldNumber INTEGER NOT NULL,
                            PRIMARY KEY(singletonId)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO declaration_form_config(
                            singletonId,
                            includeCumulativeIncome,
                            includeMonthlyIncome,
                            monthlyIncomeFieldNumber
                        ) VALUES(1, 1, 1, 20)
                        """.trimIndent()
                    )
                }
            }

        /**
         * Data-only migration: older builds completed zero-tax months as SETTLED
         * with a fictitious zero payment and payment dates. Keep the real filing
         * date and notes, but convert those rows to FILED without payment data.
         *
         * Check the exact decimal value rather than using SQLite CAST AS REAL:
         * it would treat malformed strings and tiny nonzero amounts as zero.
         */
        val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val zeroTaxPeriods = mutableListOf<String>()
                    db.query(
                        """
                        SELECT periodKey, paymentAmountGel
                        FROM monthly_declaration_record
                        WHERE zeroDeclarationPrepared = 1
                          AND declarationFiledDate IS NOT NULL
                          AND LOWER(workflowStatus) IN ('settled', 'filed')
                        """.trimIndent()
                    ).use { cursor ->
                        val periodIndex = cursor.getColumnIndexOrThrow("periodKey")
                        val amountIndex = cursor.getColumnIndexOrThrow("paymentAmountGel")
                        while (cursor.moveToNext()) {
                            val zeroPayment =
                                cursor.isNull(amountIndex) ||
                                    cursor.getString(amountIndex).toBigDecimalOrNull()?.signum() == 0
                            if (zeroPayment) {
                                zeroTaxPeriods += cursor.getString(periodIndex)
                            }
                        }
                    }
                    zeroTaxPeriods.forEach { period ->
                        db.execSQL(
                            """
                            UPDATE monthly_declaration_record
                            SET workflowStatus = 'filed',
                                paymentSentDate = NULL,
                                paymentCreditedDate = NULL,
                                paymentAmountGel = NULL
                            WHERE periodKey = ?
                            """.trimIndent(),
                            arrayOf(period)
                        )
                    }
                }
            }
    }
}
