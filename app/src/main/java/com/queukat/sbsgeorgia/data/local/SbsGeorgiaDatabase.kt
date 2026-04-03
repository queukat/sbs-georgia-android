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
        IncomeEntryEntity::class,
        MonthlyDeclarationRecordEntity::class,
        ImportedStatementEntity::class,
        ImportedTransactionEntity::class,
        FxRateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class SbsGeorgiaDatabase : RoomDatabase() {
    abstract fun taxpayerProfileDao(): TaxpayerProfileDao
    abstract fun smallBusinessStatusConfigDao(): SmallBusinessStatusConfigDao
    abstract fun reminderConfigDao(): ReminderConfigDao
    abstract fun incomeEntryDao(): IncomeEntryDao
    abstract fun monthlyDeclarationRecordDao(): MonthlyDeclarationRecordDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun importedStatementDao(): ImportedStatementDao
    abstract fun importedTransactionDao(): ImportedTransactionDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE reminder_config
                    ADD COLUMN defaultReminderTime TEXT NOT NULL DEFAULT '09:00'
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE taxpayer_profile
                    ADD COLUMN legalForm TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE taxpayer_profile
                    ADD COLUMN registrationDate TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE taxpayer_profile
                    ADD COLUMN legalAddress TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE taxpayer_profile
                    ADD COLUMN activityType TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE small_business_status_config
                    ADD COLUMN certificateNumber TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE small_business_status_config
                    ADD COLUMN certificateIssuedDate TEXT
                    """.trimIndent(),
                )
            }
        }
    }
}
